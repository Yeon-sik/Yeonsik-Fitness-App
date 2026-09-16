package com.yeonsik.fitnessapp.feature.statistics.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.BodyProfile
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry
import com.yeonsik.fitnessapp.feature.body.model.BodyWeightWindow
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.model.MealReadSummary
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPerformanceMetric
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSufficiencyLevel
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutBodyPartSets
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExercisePerformancePoint
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutReadDayMetrics
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutReadSessionSummary
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutWeekProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsReadRepositoryTest {
    @Test
    fun computesCurrentWindowComparisonTrendsAndOwnerScopedFacts() {
        val scope = AccountScope("owner-a")
        val workout = FakeWorkoutReadApi(
            sessions = listOf(
                summary("2024-02-02", 30.0),
                summary("2024-02-05", 100.0),
                summary("2024-02-09", 50.0)
            ),
            performance = listOf(
                WorkoutExercisePerformancePoint("bench|external", "벤치프레스", "2024-02-05", 80.0, 100.0),
                WorkoutExercisePerformancePoint("bench|external", "벤치프레스", "2024-02-09", 85.0, 110.0)
            )
        )
        val body = FakeBodyReadApi(
            entries = listOf(
                BodyReadEntry("old-weight", "2024-02-02", 69.0, ""),
                BodyReadEntry("weight-a", "2024-02-05", 70.0, ""),
                BodyReadEntry("weight-b", "2024-02-05", 70.4, ""),
                BodyReadEntry("weight-c", "2024-02-09", 71.0, "")
            )
        )
        val meal = FakeMealReadApi(setOf("2024-02-02", "2024-02-06"))

        val snapshot = StatisticsReadRepository(workout, body, meal).load(
            scope,
            "2024-02-10",
            StatisticsPeriod.SEVEN_DAYS
        )

        assertEquals("2024-02-04", snapshot.window.startDate)
        assertEquals("2024-02-10", snapshot.window.endDate)
        assertEquals("2024-01-28", snapshot.window.previousStartDate)
        assertEquals("2024-02-03", snapshot.window.previousEndDate)
        assertEquals(scope.ownerId, snapshot.ownerId)
        assertEquals(2, snapshot.sufficiency.workoutSessions)
        assertEquals(2, snapshot.sufficiency.workoutDays)
        assertEquals(2, snapshot.sufficiency.weightDays)
        assertEquals(1, snapshot.sufficiency.mealDays)
        assertEquals(StatisticsSufficiencyLevel.READY, snapshot.sufficiency.level)

        val volumeMetric = snapshot.comparison.first { it.label == "운동 볼륨" }
        assertEquals(150.0, volumeMetric.current!!, 0.001)
        assertEquals(30.0, volumeMetric.previous!!, 0.001)
        assertEquals(120.0, volumeMetric.difference!!, 0.001)
        val weightMetric = snapshot.comparison.first { it.label == "평균 체중" }
        assertEquals(70.6, weightMetric.current!!, 0.001)
        assertEquals(69.0, weightMetric.previous!!, 0.001)
        assertEquals(2, snapshot.workoutVolumeTrend.size)
        assertEquals(150.0, snapshot.workoutVolumeTrend.last().value!!, 0.001)
        assertEquals(2.0, snapshot.workoutFrequencyTrend.last().value!!, 0.001)

        val performance = snapshot.exercisePerformanceTrends.single()
        assertEquals("벤치프레스", performance.exerciseName)
        assertEquals(StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX, performance.metric)
        assertEquals(2, performance.points.size)
        assertTrue(workout.scopes.all { it == scope.ownerId })
        assertTrue(body.scopes.all { it == scope.ownerId })
        assertTrue(meal.scopes.all { it == scope.ownerId })
    }

    @Test
    fun keepsMissingPeriodValuesUnknownAndShowsNoDataState() {
        val scope = AccountScope("owner-a")
        val snapshot = StatisticsReadRepository(
            FakeWorkoutReadApi(emptyList(), emptyList()),
            FakeBodyReadApi(emptyList()),
            FakeMealReadApi(emptySet())
        ).load(scope, "2024-02-10", StatisticsPeriod.THIRTY_DAYS)

        assertEquals(StatisticsSufficiencyLevel.NONE, snapshot.sufficiency.level)
        assertTrue(snapshot.workoutVolumeTrend.isEmpty())
        assertTrue(snapshot.workoutFrequencyTrend.isEmpty())
        assertTrue(snapshot.bodyWeightTrend.isEmpty())
        assertTrue(snapshot.exercisePerformanceTrends.isEmpty())
        assertTrue(snapshot.observedPatterns.isEmpty())
        assertNull(snapshot.comparison.first { it.label == "운동 볼륨" }.current)
        assertNull(snapshot.comparison.first { it.label == "평균 체중" }.current)
    }

    private fun summary(date: String, volume: Double) = WorkoutReadSessionSummary(
        id = "session-$date",
        date = date,
        title = "운동",
        workoutType = "strength",
        durationSeconds = 1800,
        totalVolumeKg = volume,
        completedSetCount = 10,
        muscleLabels = emptyList()
    )
}

private class FakeWorkoutReadApi(
    private val sessions: List<WorkoutReadSessionSummary>,
    private val performance: List<WorkoutExercisePerformancePoint>
) : WorkoutReadApi {
    val scopes = mutableListOf<String>()

    override fun sessionsForDate(scope: AccountScope, date: String): List<String> = emptyList()
    override fun dayMetrics(scope: AccountScope, date: String) =
        WorkoutReadDayMetrics(0, 0, 0.0, 0)
    override fun latestInProgress(scope: AccountScope): String? = null
    override fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String? = null
    override fun weekProgress(scope: AccountScope, startDate: String, endDate: String) =
        WorkoutWeekProgress(0, 0)
    override fun strengthSetsByBodyPart(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutBodyPartSets> = emptyList()
    override fun latestDetailedTrainingDate(
        scope: AccountScope,
        referenceDate: String,
        bodyPartAliases: List<String>
    ): String? = null
    override fun completedRecordedDays(scope: AccountScope, startDate: String, endDate: String): Int = 0
    override fun completedDates(scope: AccountScope, startDate: String, endDate: String): List<String> = emptyList()
    override fun completedSessionSummaries(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutReadSessionSummary> {
        scopes += scope.ownerId
        return sessions
    }
    override fun exercisePerformance(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutExercisePerformancePoint> {
        scopes += scope.ownerId
        return performance
    }
    override fun completedResistanceSessions(scope: AccountScope, startDate: String, endDate: String): Int = 0
}

private class FakeBodyReadApi(
    private val entries: List<BodyReadEntry>
) : BodyMetricsReadApi {
    val scopes = mutableListOf<String>()

    override fun bodyProfile(scope: AccountScope): BodyProfile = BodyProfile.empty()
    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> = emptyList()
    override fun latestBodyMetricOnOrBefore(scope: AccountScope, date: String): BodyReadEntry? = null
    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int = 0
    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> = emptyList()
    override fun weightWindow(scope: AccountScope, startDate: String, endDate: String) =
        BodyWeightWindow(null, 0)
    override fun weightEntries(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<BodyReadEntry> {
        scopes += scope.ownerId
        return entries
    }
}

private class FakeMealReadApi(
    private val dates: Set<String>
) : MealReadApi {
    val scopes = mutableListOf<String>()

    override fun mealCount(scope: AccountScope, date: String): Int = 0
    override fun mealTotals(scope: AccountScope, date: String) =
        MealReadNutritionTotals(0, emptyMap())
    override fun meals(scope: AccountScope, date: String): List<MealReadSummary> = emptyList()
    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int = 0
    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> {
        scopes += scope.ownerId
        return dates.toList()
    }
    override fun nutritionSummary(scope: AccountScope, startDate: String, endDate: String) =
        MealNutritionReadSummary(0.0, 0, 0, 0)
}
