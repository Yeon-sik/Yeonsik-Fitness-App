package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadSessionSummary
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class FeatureHomeReadSourcesTest {
    @Test fun readsOnlyRequestedCompletedWindowAndPreservesAccountScope() {
        val calls = mutableListOf<List<Any?>>()
        val sources = sources(listOf(summary("today"), summary("old", date = "2026-09-30")), calls)
        val status = sources.todayWorkoutStatus(AccountScope("owner-b"), TODAY)
        assertEquals(listOf(listOf(AccountScope("owner-b"), TODAY, TODAY)), calls)
        assertTrue(status.hasCompletedWorkout)
        assertEquals(listOf("등", "이두"), status.muscleLabels)
    }

    @Test fun multipleStrengthSessionsUnionOnlyCompletedSetProjectionLabels() {
        val status = sources(listOf(
            summary("first", projection = listOf("등", "이두")),
            summary("second", projection = listOf("이두", "가슴", "")),
            summary("cardio", type = "cardio", projection = listOf("종아리"))
        )).todayWorkoutStatus(AccountScope("owner"), TODAY)
        assertEquals(listOf("등", "이두", "가슴"), status.muscleLabels)
        assertTrue(status.hasCompletedStrength)
        assertTrue(status.hasCompletedCardio)
        assertFalse(status.muscleLabels.contains("raw-not-completed"))
    }

    @Test fun missingProjectionDoesNotFallBackToAllExerciseLabels() {
        val status = sources(listOf(summary("strength", projection = emptyList())))
            .todayWorkoutStatus(AccountScope("owner"), TODAY)
        assertTrue(status.hasCompletedWorkout)
        assertTrue(status.hasCompletedStrength)
        assertTrue(status.muscleLabels.isEmpty())
    }

    @Test fun multipleCardioDurationsSumSecondsBeforeMinuteFormatting() {
        val status = sources(listOf(
            summary("first", type = "cardio", seconds = 59),
            summary("second", type = "cardio", seconds = 61),
            summary("strength", seconds = 3_600),
            summary("old", type = "cardio", seconds = 900, date = "2026-09-30")
        )).todayWorkoutStatus(AccountScope("owner"), TODAY)
        assertEquals(120L, status.cardioDurationSeconds)
        assertTrue(status.hasCompletedCardio)
    }

    @Test fun noCompletedSessionsHasNoWorkoutFacts() {
        val status = sources(emptyList()).todayWorkoutStatus(AccountScope("owner"), TODAY)
        assertFalse(status.hasCompletedWorkout)
        assertFalse(status.hasCompletedStrength)
        assertFalse(status.hasCompletedCardio)
        assertTrue(status.muscleLabels.isEmpty())
        assertEquals(0L, status.cardioDurationSeconds)
        assertEquals(0.0, status.completedStrengthVolumeKg, 0.0)
    }

    @Test fun volumeSumsOnlyTodaysCompletedStrengthSessions() {
        val status = sources(listOf(
            summary("first", volume = 1_200.0),
            summary("second", volume = 650.5),
            summary("cardio", type = "cardio", volume = 9_999.0),
            summary("yesterday", date = "2026-09-30", volume = 8_888.0)
        )).todayWorkoutStatus(AccountScope("owner"), TODAY)
        assertEquals(1_850.5, status.completedStrengthVolumeKg, 0.0)
    }

    private fun sources(
        summaries: List<WorkoutReadSessionSummary>,
        calls: MutableList<List<Any?>> = mutableListOf()
    ): FeatureHomeReadSources {
        val workout = Proxy.newProxyInstance(WorkoutReadApi::class.java.classLoader,
            arrayOf(WorkoutReadApi::class.java)) { _, method, args ->
            check(method.name == "completedSessionSummaries") { "Unexpected read: ${method.name}" }
            calls += args.toList()
            summaries
        } as WorkoutReadApi
        return FeatureHomeReadSources(workout, unusedApi(MealReadApi::class.java),
            unusedApi(BodyMetricsReadApi::class.java), unusedApi(DevelopmentReadApi::class.java))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unusedApi(type: Class<T>): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("Unexpected read: ${method.name}")
        } as T

    private fun summary(id: String, type: String = "strength", seconds: Int = 0,
        date: String = TODAY, projection: List<String> = listOf("등", "이두"), volume: Double = 0.0) =
        WorkoutReadSessionSummary(id, date, id, type, seconds, volume, 1,
            muscleLabels = listOf("raw-not-completed"), projectionMuscleLabels = projection)

    private companion object { const val TODAY = "2026-10-01" }
}
