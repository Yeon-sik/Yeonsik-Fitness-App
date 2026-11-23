package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface DailyWorkoutStorage {
    fun read(name: String): String?
    fun write(name: String, text: String)
}

data class LocalDailyWorkouts(
    val workouts: List<DailyWorkoutSource>,
    val samplesByWorkout: Map<String, List<DailyHeartRateSample>> = emptyMap(),
    val linkedWorkoutKeys: Map<String, String> = emptyMap()
)

fun interface DailyWorkoutLocalReader {
    fun read(day: WorkoutDay): LocalDailyWorkouts
}

class DailyWorkoutRepository(
    private val storage: DailyWorkoutStorage,
    private val localRecords: DailyWorkoutLocalReader
) {
    constructor(storage: DailyWorkoutStorage, localWorkouts: (WorkoutDay) -> List<DailyWorkoutSource>) :
        this(storage, DailyWorkoutLocalReader { day -> LocalDailyWorkouts(localWorkouts(day)) })

    suspend fun load(day: WorkoutDay): DailyWorkoutReport = access { report(day, readDay(day)) }

    suspend fun importFleek(day: WorkoutDay, csv: String): DailyWorkoutReport = access {
        val incoming = parseFleekDailyWorkouts(csv)
        val old = readFleek()
        val next = (old + incoming).associateBy { it.workout.key }.values.toList()
        val result = report(day, readDay(day), next)
        storage.write(FLEEK_FILE, json.encodeToString(next))
        result
    }

    suspend fun saveWearables(day: WorkoutDay, records: DailyWearableRecords): DailyWorkoutReport = access {
        val current = readDay(day)
        val start = day.start.toEpochMilliseconds()
        val end = day.end.toEpochMilliseconds()
        val workouts = records.workouts.filter { it.startMillis?.let { time -> time in start until end } == true }
        val sampleEnd = maxOf(end, workouts.mapNotNull { it.endMillis }.maxOrNull() ?: end)
        val filtered = DailyWearableRecords(workouts, records.heartRateSamples.filter { it.timeMillis in start until sampleEnd }.distinct())
        saveDay(day, current.copy(wearables = filtered, zone = day.zone.id))
    }

    suspend fun decide(day: WorkoutDay, candidate: DailyWorkoutCandidate, same: Boolean): DailyWorkoutReport = access {
        val current = readDay(day)
        val first = candidate.first.key
        val second = candidate.second.key
        val pending = report(day, current).candidates
        require(pending.any { it.first.key == first && it.second.key == second }) {
            "운동 기록이 변경되었습니다. 다시 불러온 뒤 확인해 주세요."
        }
        saveDay(day, current.copy(decisions = current.decisions + DailyWorkoutDecision(first, second, same)))
    }

    suspend fun undo(day: WorkoutDay, decision: DailyWorkoutDecision): DailyWorkoutReport = access {
        val current = readDay(day)
        saveDay(day, current.copy(decisions = current.decisions - decision))
    }

    private fun saveDay(day: WorkoutDay, saved: SavedDay): DailyWorkoutReport {
        val result = report(day, saved)
        storage.write(day.date.toString(), json.encodeToString(saved))
        return result
    }

    private fun report(day: WorkoutDay, saved: SavedDay, fleek: List<FleekDailyWorkout> = readFleek()): DailyWorkoutReport {
        val local = localRecords.read(day)
        val inputs = local.workouts.associateBy { it.key }.toMutableMap()
        for (fileWorkout in fleek.asSequence().filter { it.date == day.date.toString() }.map { it.workout }) {
            val storedWorkout = inputs[fileWorkout.key]
            inputs[fileWorkout.key] = storedWorkout?.copy(
                kind = fileWorkout.kind,
                startMillis = fileWorkout.startMillis ?: storedWorkout.startMillis,
                durationSeconds = storedWorkout.durationSeconds ?: fileWorkout.durationSeconds,
                distanceMeters = storedWorkout.distanceMeters ?: fileWorkout.distanceMeters
            ) ?: fileWorkout
        }
        val wearable = saved.wearables.takeIf { saved.zone == day.zone.id } ?: DailyWearableRecords()
        return projectDailyWorkouts(
            day, inputs.values + wearable.workouts, wearable.heartRateSamples, saved.decisions,
            local.linkedWorkoutKeys, local.samplesByWorkout
        )
    }

    private fun readDay(day: WorkoutDay): SavedDay = storage.read(day.date.toString())?.let {
        json.decodeFromString<SavedDay>(it).also { saved -> check(saved.version == 1) { "지원하지 않는 운동 기록 버전입니다." } }
    } ?: SavedDay()

    private fun readFleek(): List<FleekDailyWorkout> = storage.read(FLEEK_FILE)
        ?.let { json.decodeFromString<List<FleekDailyWorkout>>(it) }.orEmpty()

    private suspend fun <T> access(operation: () -> T): T = withContext(Dispatchers.Default) { lock.withLock { operation() } }

    private companion object {
        const val FLEEK_FILE = "fleek-v1"
        val json = Json { encodeDefaults = true }
        val lock = Mutex()
    }
}

@Serializable
private data class SavedDay(
    val version: Int = 1,
    val zone: String? = null,
    val wearables: DailyWearableRecords = DailyWearableRecords(),
    val decisions: List<DailyWorkoutDecision> = emptyList()
)
