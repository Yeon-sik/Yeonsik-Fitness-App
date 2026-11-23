package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DailyWorkoutTest {
    private val day = WorkoutDay(LocalDate.parse("2026-11-11"), TimeZone.of("Asia/Seoul"))
    private val appleWatch = "com.apple.workout"
    private val samsung = "com.sec.android.app.shealth"
    private val fleekCsv = "date,exercise,reps,weight(kg)\n2026-11-11,벤치프레스,8,60\n"

    @Test
    fun fleekAndTwoAppleHealthCopiesBecomeOneSessionInEitherImportOrder() = runTest {
        val minutes = 35 * 60
        val wearable = source(WorkoutOrigin.HEALTH_KIT, appleWatch, "watch", DailyWorkoutKind.STRENGTH, 10, minutes)
        val mirror = source(WorkoutOrigin.HEALTH_KIT, "com.primero.fleek", "mirror", DailyWorkoutKind.STRENGTH, 10, minutes)
        val sample = DailyHeartRateSample(appleWatch, wearable.startMillis!! + 60_000, 148.0)
        val input = DailyWearableRecords(listOf(wearable, mirror, wearable), listOf(sample, sample))
        val results = listOf(true, false).map { watchFirst ->
            val storage = MemoryStorage()
            val repo = DailyWorkoutRepository(storage) { emptyList() }
            var report = if (watchFirst) {
                repo.saveWearables(day, input)
                repo.importFleek(day, fleekCsv)
            } else {
                repo.importFleek(day, fleekCsv)
                repo.saveWearables(day, input)
            }
            assertEquals(1, report.sessions.size)
            assertEquals(2, report.pendingCount)
            assertNull(report.sessions.single().timing.startMillis)
            repeat(2) {
                report = repo.decide(day, report.candidates.first(), true)
            }
            assertEquals(3, report.sessions.single().sources.size)
            assertEquals(60.0, report.sessions.single().details!!.exercises.single().sets.single().weightKg)
            assertEquals(1, report.sessions.single().heartRate?.sampleCount)
            val fresh = DailyWorkoutRepository(storage) { emptyList() }
            fresh.importFleek(day, fleekCsv)
            val again = fresh.saveWearables(day, input)
            assertEquals(report.toJson(), again.toJson())
            Json.parseToJsonElement(again.toJson()).jsonObject.also { json ->
                assertEquals(1, json.getValue("session_count").jsonPrimitive.int)
                assertEquals(0, json.getValue("pending_review_count").jsonPrimitive.int)
                assertEquals(minutes, json.getValue("sessions").jsonArray.single().jsonObject.getValue("duration_seconds").jsonPrimitive.int)
                assertFalse(again.toJson().contains("\"watch\""))
            }
            again.toJson()
        }
        assertEquals(results.first(), results.last())
    }

    @Test
    fun watchOnlyRunPersistsAndOnlyTheSameLaterFitnessRunCollapses() = runTest {
        var local = emptyList<DailyWorkoutSource>()
        val storage = MemoryStorage()
        val repo = DailyWorkoutRepository(storage) { local }
        val first = source(WorkoutOrigin.HEALTH_CONNECT, samsung, "run-1", DailyWorkoutKind.RUNNING, 8, 1800)
        val later = source(WorkoutOrigin.HEALTH_CONNECT, samsung, "run-2", DailyWorkoutKind.RUNNING, 19, 2000)
        val records = DailyWearableRecords(listOf(later, first, later))
        assertEquals(2, repo.saveWearables(day, records).sessions.size)
        local = listOf(source(WorkoutOrigin.FITNESS, "Fitness App", "same-run", DailyWorkoutKind.RUNNING, 19, 2005))
        val before = repo.load(day)
        assertEquals(2, before.sessions.size)
        assertEquals(1, before.pendingCount)
        val merged = repo.decide(day, before.candidates.single(), true)
        assertEquals(listOf(1, 2), merged.sessions.map { it.sources.size })
        assertEquals(0, merged.pendingCount)
        assertEquals(2, DailyWorkoutRepository(storage) { local }.load(day).sessions.size)
        val unlinked = repo.undo(day, merged.decisions.single())
        val separate = repo.decide(day, unlinked.candidates.single(), false)
        assertEquals(3, separate.sessions.size)
        assertTrue(repo.saveWearables(day, records).candidates.isEmpty())
    }

    @Test
    fun heartRateWithoutWorkoutIsNotCountedAsExercise() = runTest {
        val repo = DailyWorkoutRepository(MemoryStorage()) { emptyList() }
        val sample = DailyHeartRateSample(samsung, day.start.toEpochMilliseconds() + 120_000, 100.0)
        val report = repo.saveWearables(day, DailyWearableRecords(heartRateSamples = listOf(sample)))
        assertTrue(report.sessions.isEmpty())
        assertEquals(1, report.unlinkedHeartRate.single().sampleCount)
    }

    @Test
    fun workoutCrossingMidnightKeepsItsSamplesOnItsStartDay() = runTest {
        val repo = DailyWorkoutRepository(MemoryStorage()) { emptyList() }
        val run = source(WorkoutOrigin.HEALTH_CONNECT, samsung, "overnight", DailyWorkoutKind.RUNNING, 23, 4000)
        val afterMidnight = DailyHeartRateSample(samsung, day.end.toEpochMilliseconds() + 60_000, 133.0)
        val outside = DailyHeartRateSample(samsung, run.endMillis!! + 60_000, 135.0)
        val report = repo.saveWearables(day, DailyWearableRecords(listOf(run), listOf(afterMidnight, outside)))
        assertEquals(1, assertNotNull(report.sessions.single().heartRate).sampleCount)
        assertTrue(report.unlinkedHeartRate.isEmpty())
    }

    @Test
    fun fleekDateOnlyAndMeasuredMidnightRemainDistinguishable() {
        val unknown = parseFleekDailyWorkouts(fleekCsv).single().workout
        val midnight = parseFleekDailyWorkouts(fleekCsv.replace("2026-11-11", "2026-11-11T00:00:00+09:00")).single().workout
        assertNull(unknown.startMillis)
        assertEquals(Instant.parse("2026-11-10T15:00:00Z").toEpochMilliseconds(), midnight.startMillis)
        assertNull(midnight.endMillis)
        val external = parseFleekDailyWorkouts("date,exercise,reps,duration(s),distance(m)\n2026-11-11T18:00:00,러닝,,1800,5000\n").single().workout
        assertEquals(DailyWorkoutKind.RUNNING, external.kind)
        assertEquals(5000.0, external.distanceMeters)
    }

    private fun source(origin: WorkoutOrigin, writer: String, id: String, kind: DailyWorkoutKind, hour: Int, duration: Int): DailyWorkoutSource {
        val start = day.start.toEpochMilliseconds() + hour * 3_600_000L
        return DailyWorkoutSource(origin, writer, id, kind.label, kind, start, start + duration * 1000L, duration)
    }

    private class MemoryStorage : DailyWorkoutStorage {
        private val files = mutableMapOf<String, String>()
        override fun read(name: String) = files[name]
        override fun write(name: String, text: String) { files[name] = text }
    }
}
