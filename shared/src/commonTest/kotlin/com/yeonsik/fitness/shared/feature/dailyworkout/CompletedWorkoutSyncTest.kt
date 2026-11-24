package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletedWorkoutSyncTest {
    private val day = WorkoutDay(LocalDate.parse("2025-01-01"), TimeZone.UTC)
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"
    private val first = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"
    private val second = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"

    private fun source(id: String) = DailyWorkoutSource(WorkoutOrigin.FITNESS, WorkoutOrigin.FITNESS.label, id,
        "Fixture", DailyWorkoutKind.STRENGTH, manualHeartRateBpm = 123.0,
        exercises = listOf(DailyWorkoutExercise("Fixture exercise",
            listOf(DailyWorkoutSet(weightKg = 45.359237, reps = 10, rir = 2, id = "$id-set", inputLoad = 100.0, inputUnit = "lb")), "$id-ex")),
        totalVolumeKg = 453.59237)

    private class Files : DailyWorkoutStorage {
        val files = mutableMapOf<String, String>()
        override fun read(name: String) = files[name]
        override fun write(name: String, text: String) { files[name] = text }
    }

    private class Local(var workouts: List<DailyWorkoutSource>) : DailyWorkoutLocalReader {
        val deleted = mutableSetOf<String>()
        override fun read(day: WorkoutDay) = LocalDailyWorkouts(workouts)
        override fun deletedCompletedWorkouts(ids: Set<String>) = ids.intersect(deleted)
    }

    private class Remote(user: String) : WorkoutCloudAccess {
        var user = user
        val records = mutableMapOf<Pair<String, String>, CloudWorkoutRow>()
        var loseReply = false
        var offline = false
        val writes = mutableListOf<String>()
        override fun account() = WorkoutCloudAccount("fixture.supabase.co", user, "fixture")
        override suspend fun rpc(account: WorkoutCloudAccount, function: String, body: String): String {
            check(!offline)
            val request = Json.parseToJsonElement(body).jsonObject
            check(request.getValue("p_expected_owner").jsonPrimitive.content == user)
            if (function == "fitness_completed_workout_summary_v1") {
                val from = request.getValue("p_from").jsonPrimitive.content
                val to = request.getValue("p_to").jsonPrimitive.content
                val docs = records.filterKeys { it.first == user }.values.filter { it.day in from..to }.mapNotNull { it.workout }
                return buildJsonObject {
                    put("version", 1); put("from", from); put("to", to); put("sessions", docs.size)
                    put("completed_sets", docs.sumOf { it.exercises.sumOf { e -> e.sets.size } })
                    put("volume_kg", docs.sumOf { it.totalVolumeKg }); put("sessions_with_duration", docs.count { it.durationSeconds != null })
                    put("known_duration_seconds", docs.sumOf { it.durationSeconds ?: 0 })
                }.toString()
            }
            val day = request.getValue("p_day").jsonPrimitive.content
            if (function == "fitness_completed_workouts_v1") {
                val after = request["p_after"]?.jsonPrimitive?.content.orEmpty()
                return Json.encodeToString(records.filterKeys { it.first == user }.values
                    .filter { it.day == day && it.id > after }.sortedBy { it.id }.take(32))
            }
            writes += body
            val id = request.getValue("p_id").jsonPrimitive.content
            val revision = request.getValue("p_revision").jsonPrimitive.content.toLong()
            val stored = records[user to id]
            check(revision == (stored?.revision ?: 0L))
            val doc = request.getValue("p_workout").takeUnless { it == JsonNull }?.let {
                Json.decodeFromString<CompletedWorkoutDocument>(it.toString())
            }
            val row = CloudWorkoutRow(id, day, revision + 1, doc, request.getValue("p_memo").jsonPrimitive.content)
            records[user to id] = row
            if (loseReply) { loseReply = false; error("response lost") }
            return Json.encodeToString(row)
        }
    }

    @Test fun twoDevicesKeepRepeatedRoutinesAndOfflineNotesAcrossRestart() = runTest {
        val remote = Remote(owner)
        val galaxyFiles = Files()
        val iphoneFiles = Files()
        val watch = source(first).copy(id = "private-provider-id", origin = WorkoutOrigin.HEALTH_CONNECT)
        val importedFleek = source(second).copy(origin = WorkoutOrigin.FLEEK, id = "private-fleek-source", nativeSessionId = second)
        val galaxyNative = Local(listOf(source(first), importedFleek, watch))
        val iphoneNative = Local(emptyList())
        val galaxy = CompletedWorkoutSync(galaxyFiles, galaxyNative, remote, remote.account())
        fun iphone() = CompletedWorkoutSync(iphoneFiles, iphoneNative, remote, remote.account())
        remote.loseReply = true
        assertFailsWith<IllegalStateException> { galaxy.synchronize(day) }
        galaxy.synchronize(day)
        iphone().synchronize(day)
        assertEquals(2, remote.writes.size)
        val report = DailyWorkoutRepository(iphoneFiles, iphoneNative).withSyncedWorkouts(iphone()).load(day)
        assertEquals(2, report.sessions.size)
        val imported = report.sources.values.single { it.id == first }
        assertEquals(source(first).exercises, imported.exercises)
        assertEquals(source(first).totalVolumeKg, imported.totalVolumeKg)
        assertNull(imported.manualHeartRateBpm)
        assertFalse(remote.writes.any { "HeartRate" in it || "provider-id" in it || "private-fleek-source" in it })
        remote.offline = true
        iphone().edit(day, first, "iPhone memo")
        assertFailsWith<IllegalStateException> { iphone().synchronize(day) }
        assertEquals("iPhone memo", iphone().cached(day).getValue(first).memoDraft)
        remote.offline = false
        iphone().synchronize(day)
        galaxy.synchronize(day)
        assertEquals("iPhone memo", galaxy.cached(day).getValue(first).server?.memo)
        assertEquals(3, remote.writes.size)
        assertEquals(2, remote.records.size)
        assertTrue(galaxy.supplement(day, galaxyNative.workouts).isEmpty())
        // The other direction creates its own session under the same account.
        val third = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3"
        iphoneNative.workouts = listOf(source(third))
        iphone().synchronize(day)
        galaxy.synchronize(day)
        assertEquals(third, galaxy.supplement(day, galaxyNative.workouts).single().id)
        val weekly = Json.parseToJsonElement(iphone().weeklySummary(day)).jsonObject
        assertEquals("3", weekly.getValue("sessions").jsonPrimitive.content)
        assertEquals("2024-12-26", weekly.getValue("from").jsonPrimitive.content)
        assertFalse(weekly.keys.any { it in setOf("memo", "exercises", "id", "owner") })
    }

    @Test fun preservesDraftOnConcurrentEditPropagatesVerifiedDeletionAndIsolatesAccounts() = runTest {
        val remote = Remote(owner)
        val files = Files()
        val local = Local(listOf(source(first)))
        val a = CompletedWorkoutSync(files, local, remote, remote.account())
        val b = CompletedWorkoutSync(Files(), Local(emptyList()), remote, remote.account())
        a.synchronize(day); b.synchronize(day)
        a.edit(day, first, "A"); b.edit(day, first, "B")
        a.synchronize(day); b.synchronize(day)
        val conflict = b.cached(day).getValue(first)
        assertTrue(conflict.conflict)
        assertEquals("B", conflict.memoDraft)
        assertEquals("A", conflict.server?.memo)
        b.acceptServer(day, first)
        local.workouts = emptyList()
        a.synchronize(day)
        assertTrue(remote.records.getValue(owner to first).workout != null)
        local.deleted += first
        a.synchronize(day); b.synchronize(day)
        assertNull(b.cached(day).getValue(first).server?.workout)
        local.workouts = listOf(source(first))
        remote.user = other
        assertFailsWith<IllegalStateException> { a.synchronize(day) }
        val separate = CompletedWorkoutSync(files, local, remote, remote.account())
        separate.synchronize(day)
        assertTrue(separate.cached(day).isEmpty())
        assertFalse(remote.records.containsKey(other to first))
    }
}
