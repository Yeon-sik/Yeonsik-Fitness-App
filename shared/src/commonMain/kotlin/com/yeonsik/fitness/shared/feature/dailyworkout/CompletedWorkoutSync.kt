package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

private val uuidText = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

data class WorkoutCloudAccount(val host: String, val owner: String, val label: String, val localProfile: String = "이 기기") {
    init {
        require(host.matches(Regex("[a-z0-9][a-z0-9.-]{0,98}[a-z0-9]")) && owner.matches(uuidText))
    }
}

interface WorkoutCloudAccess {
    fun account(): WorkoutCloudAccount?
    suspend fun rpc(account: WorkoutCloudAccount, function: String, body: String): String
}

/** Only user-entered completed strength facts cross this boundary. No provider, GPS or pulse fields. */
@Serializable
internal data class CompletedWorkoutDocument(
    val title: String, val startMillis: Long?, val endMillis: Long?, val durationSeconds: Int?,
    val totalVolumeKg: Double, val exercises: List<DailyWorkoutExercise>
) {
    fun asSource(id: String) = DailyWorkoutSource(WorkoutOrigin.FITNESS, WorkoutOrigin.FITNESS.label,
        id, title, DailyWorkoutKind.STRENGTH, startMillis, endMillis, durationSeconds,
        exercises = exercises, totalVolumeKg = totalVolumeKg)
}

@Serializable
internal data class CloudWorkoutRow(
    val id: String, val day: String, val revision: Long, val workout: CompletedWorkoutDocument?, val memo: String
)

@Serializable
private data class WorkoutCloudSummary(val version: Int, val from: String, val to: String, val sessions: Long,
    val completed_sets: Long, val volume_kg: Double, val sessions_with_duration: Long, val known_duration_seconds: Long)

@Serializable
internal data class SyncedWorkout(
    val server: CloudWorkoutRow? = null,
    val lastNative: CompletedWorkoutDocument? = null,
    val memoDraft: String? = null,
    val remove: Boolean = false,
    val conflict: Boolean = false
)

internal class CompletedWorkoutSync(
    private val storage: DailyWorkoutStorage,
    private val local: DailyWorkoutLocalReader,
    private val access: WorkoutCloudAccess,
    val account: WorkoutCloudAccount
) {
    private fun key(day: WorkoutDay) = "strength-sync-v1-${account.host}-${account.owner}-${day.date}"

    private fun checkAccount() {
        val current = access.account()
        check(current?.host == account.host && current.owner == account.owner) { "동기화 계정이 바뀌었습니다." }
    }

    fun cached(day: WorkoutDay): Map<String, SyncedWorkout> {
        checkAccount()
        return storage.read(key(day))?.let { json.decodeFromString(it) } ?: emptyMap()
    }

    private fun save(day: WorkoutDay, rows: Map<String, SyncedWorkout>) {
        checkAccount()
        storage.write(key(day), json.encodeToString(rows))
    }

    fun supplement(day: WorkoutDay, native: List<DailyWorkoutSource>): List<DailyWorkoutSource> {
        val localIds = native.mapNotNullTo(mutableSetOf()) { it.savedSessionId() }
        return cached(day).filterKeys { it !in localIds }.mapNotNull { (id, entry) -> entry.server?.workout?.asSource(id) }
    }

    private fun native(day: WorkoutDay): Map<String, CompletedWorkoutDocument> =
        local.completedStrength(day).filter { it.kind == DailyWorkoutKind.STRENGTH && it.savedSessionId() != null }
            .associate { source ->
                val sessionId = checkNotNull(source.savedSessionId())
                require(uuidText.matches(sessionId) && source.totalVolumeKg != null &&
                    source.exercises.all { it.id != null && it.sets.all { set -> set.id != null } }) {
                    "이 운동의 식별자 또는 저장된 볼륨을 확인할 수 없습니다."
                }
                sessionId to CompletedWorkoutDocument(source.title, source.startMillis, source.endMillis,
                    source.durationSeconds, source.totalVolumeKg, source.exercises)
            }

    private fun DailyWorkoutSource.savedSessionId(): String? = when (origin) {
        WorkoutOrigin.FITNESS -> (nativeSessionId ?: id).lowercase()
        WorkoutOrigin.FLEEK -> nativeSessionId?.lowercase()
        else -> null
    }

    suspend fun edit(day: WorkoutDay, id: String, memo: String?, remove: Boolean = false) = locked {
        require(memo == null || memo.length <= 1000)
        val entries = cached(day).toMutableMap()
        val entry = entries.getValue(id)
        check(entry.server?.workout != null && !entry.conflict)
        entries[id] = entry.copy(memoDraft = memo, remove = remove)
        save(day, entries)
    }

    suspend fun acceptServer(day: WorkoutDay, id: String) = locked {
        val entries = cached(day).toMutableMap()
        val entry = entries.getValue(id)
        check(entry.conflict)
        entries[id] = SyncedWorkout(server = entry.server, lastNative = native(day)[id])
        save(day, entries)
    }

    suspend fun weeklySummary(day: WorkoutDay): String = locked {
        val from = day.date.minus(6, DateTimeUnit.DAY).toString()
        val body = buildJsonObject {
            put("p_expected_owner", account.owner); put("p_from", from); put("p_to", day.date.toString())
        }
        val summary = json.decodeFromString<WorkoutCloudSummary>(
            access.rpc(account, "fitness_completed_workout_summary_v1", body.toString()))
        checkAccount()
        check(summary.version == 1 && summary.from == from && summary.to == day.date.toString() &&
            summary.sessions >= 0 && summary.completed_sets >= 0 && summary.volume_kg.isFinite() && summary.volume_kg >= 0 &&
            summary.sessions_with_duration in 0..summary.sessions && summary.known_duration_seconds >= 0)
        json.encodeToString(summary)
    }

    suspend fun synchronize(day: WorkoutDay) = locked {
        val entries = cached(day).toMutableMap()
        val remote = fetchDay(day)
        val owners = storage.read(NATIVE_OWNERS)?.let { json.decodeFromString<Map<String, String>>(it) }.orEmpty().toMutableMap()
        val identity = "${account.host}/${account.owner}"
        val native = native(day).filterKeys { owners[it] == null || owners[it] == identity }
        if (native.keys.any { it !in owners }) {
            native.keys.forEach { owners[it] = identity }
            checkAccount()
            storage.write(NATIVE_OWNERS, json.encodeToString(owners))
        }
        val deleted = local.deletedCompletedWorkouts(entries.keys)
        for (id in (entries.keys + native.keys + remote.keys).sorted()) {
            val entry = entries[id] ?: SyncedWorkout()
            val server = remote[id]
            val localWorkout = native[id]
            check(entry.server == null || server != null) { "서버 기록을 대조할 수 없습니다. 다시 시도해 주세요." }
            if (entry.conflict) {
                entries[id] = entry.copy(server = server)
                continue
            }
            val removed = entry.remove || (entry.lastNative != null && id in deleted)
            val nativeChanged = localWorkout != null && localWorkout != entry.lastNative
            val writing = removed || nativeChanged || entry.memoDraft != null
            val document = if (removed) null else if (nativeChanged) localWorkout else entry.server?.workout
            val memo = if (removed) "" else entry.memoDraft ?: entry.server?.memo.orEmpty()
            val same = server != null && server.workout == document && server.memo == memo
            val serverChanged = server?.revision != entry.server?.revision
            val next = when {
                !writing || same -> SyncedWorkout(server, localWorkout ?: entry.lastNative)
                serverChanged || (server != null && server.workout == null) ->
                    entry.copy(server = server, conflict = true)
                else -> SyncedWorkout(write(day, id, entry.server?.revision ?: 0, document, memo), localWorkout ?: entry.lastNative)
            }
            entries[id] = next
            // Acknowledged rows survive an interrupted batch. A lost response can be replayed.
            save(day, entries)
        }
        save(day, entries)
    }

    private suspend fun fetchDay(day: WorkoutDay): Map<String, CloudWorkoutRow> {
        val result = mutableMapOf<String, CloudWorkoutRow>()
        var after: String? = null
        do {
            val body = buildJsonObject {
                put("p_expected_owner", account.owner); put("p_day", day.date.toString())
                after?.let { put("p_after", it) }
            }
            checkAccount()
            val rows = json.decodeFromString<List<CloudWorkoutRow>>(access.rpc(account, "fitness_completed_workouts_v1", body.toString()))
            for (row in rows) {
                require(row.day == day.date.toString() && uuidText.matches(row.id) && row.revision > 0 &&
                    (after == null || row.id > checkNotNull(after))) { "동기화 응답의 운동 순서가 올바르지 않습니다." }
                after = row.id
                result[row.id] = row
            }
        } while (rows.size == PAGE_SIZE)
        return result
    }

    private suspend fun write(day: WorkoutDay, id: String, revision: Long, workout: CompletedWorkoutDocument?, memo: String): CloudWorkoutRow {
        val body = buildJsonObject {
            put("p_expected_owner", account.owner); put("p_day", day.date.toString()); put("p_id", id)
            put("p_revision", revision); put("p_workout", workout?.let { json.encodeToJsonElement(it) } ?: JsonNull)
            put("p_memo", memo)
        }
        checkAccount()
        val row = json.decodeFromString<CloudWorkoutRow>(access.rpc(account, "fitness_save_completed_workout_v1", body.toString()))
        check(row.id == id && row.day == day.date.toString() && row.revision > revision && row.workout == workout && row.memo == memo) {
            "운동 저장 응답을 확인할 수 없습니다. 다시 시도해 주세요."
        }
        return row
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.Default) { lock.withLock { checkAccount(); block() } }

    private companion object {
        const val PAGE_SIZE = 32
        const val NATIVE_OWNERS = "strength-sync-native-owners-v1"
        val json = Json { encodeDefaults = true }
        val lock = Mutex()
    }
}
