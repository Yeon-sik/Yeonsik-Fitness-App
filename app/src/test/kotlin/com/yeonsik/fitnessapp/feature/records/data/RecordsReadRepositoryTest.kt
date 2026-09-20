package com.yeonsik.fitnessapp.feature.records.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.body.model.BodyWeightWindow
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitness.shared.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitness.shared.feature.meal.model.MealReadSummary
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutBodyPartSets
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadDayMetrics
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadSessionSummary
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutWeekProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsReadRepositoryTest {
    @Test
    fun buildsMonthGridMarkersDetailsAndDailyWeightAverage() {
        val scope = AccountScope("owner-a")
        val workout = FakeWorkoutReadApi(
            dates = listOf("2024-02-05"),
            summaries = listOf(
                WorkoutReadSessionSummary(
                    id = "workout-1",
                    date = "2024-02-05",
                    title = "상체",
                    workoutType = "strength",
                    durationSeconds = 1800,
                    totalVolumeKg = 1200.0,
                    completedSetCount = 12,
                    muscleLabels = listOf("등")
                )
            )
        )
        val body = FakeBodyReadApi(
            entries = listOf(
                BodyReadEntry("weight-1", "2024-02-05", 70.0, ""),
                BodyReadEntry("weight-2", "2024-02-05", 70.4, "")
            )
        )
        val meal = FakeMealReadApi(setOf("2024-02-07"))

        val snapshot = RecordsReadRepository(workout, body, meal).load(
            scope = scope,
            displayedMonth = "2024-02",
            selectedDate = "2024-02-05",
            today = "2024-02-20"
        )

        assertEquals(42, snapshot.calendarDays.size)
        assertEquals("2024-01-29", snapshot.calendarDays.first().date)
        assertEquals("2024-03-10", snapshot.calendarDays.last().date)
        assertEquals(scope.ownerId, snapshot.ownerId)
        assertEquals("2024-02", snapshot.displayedMonth)
        assertEquals("2024-02-05", snapshot.selectedDate)
        assertEquals("2024-02-20", snapshot.today)

        val workoutDay = snapshot.calendarDays.first { it.date == "2024-02-05" }
        assertTrue(workoutDay.hasWorkout)
        assertTrue(workoutDay.hasBodyMetric)
        assertTrue(!workoutDay.hasMeal)
        assertEquals(listOf("등"), workoutDay.muscleLabels)
        assertTrue(snapshot.calendarDays.first { it.date == "2024-02-07" }.hasMeal)

        assertEquals(1, snapshot.selectedDay.workouts.size)
        assertEquals("workout-1", snapshot.selectedDay.workouts.single().id)
        assertEquals(2, snapshot.selectedDay.bodyMetrics.size)
        assertTrue(snapshot.selectedDay.meals.isEmpty())
        assertEquals(1, snapshot.weightTrend.size)
        assertEquals(70.2, snapshot.weightTrend.single().averageKg, 0.001)
        assertTrue(workout.scopes.all { it == scope.ownerId })
        assertTrue(body.scopes.all { it == scope.ownerId })
        assertTrue(meal.scopes.all { it == scope.ownerId })
    }

    @Test
    fun selectedHistoricalDateIsReadIndependentlyOfDisplayedMonth() {
        val scope = AccountScope("owner-a")
        val body = FakeBodyReadApi(emptyList())
        val snapshot = RecordsReadRepository(
            FakeWorkoutReadApi(emptyList(), emptyList()),
            body,
            FakeMealReadApi(emptySet())
        ).load(scope, "2024-02", "2024-01-31", "2024-02-20")

        assertEquals("2024-02", snapshot.displayedMonth)
        assertEquals("2024-01-31", snapshot.selectedDate)
        assertEquals("2024-01-31", snapshot.selectedDay.date)
        assertTrue(snapshot.selectedDay.workouts.isEmpty())
        assertTrue(snapshot.selectedDay.bodyMetrics.isEmpty())
        assertTrue(snapshot.selectedDay.meals.isEmpty())
    }
}

private class FakeWorkoutReadApi(
    private val dates: List<String>,
    private val summaries: List<WorkoutReadSessionSummary>
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
    override fun completedDates(scope: AccountScope, startDate: String, endDate: String): List<String> {
        scopes += scope.ownerId
        return dates
    }
    override fun completedSessionSummaries(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutReadSessionSummary> {
        scopes += scope.ownerId
        return summaries
    }
    override fun completedResistanceSessions(scope: AccountScope, startDate: String, endDate: String): Int = 0
}

private class FakeBodyReadApi(
    private val entries: List<BodyReadEntry>
) : BodyMetricsReadApi {
    val scopes = mutableListOf<String>()

    override fun bodyProfile(scope: AccountScope): BodyProfile = BodyProfile.empty()
    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> {
        scopes += scope.ownerId
        return entries.filter { it.date == date }
    }
    override fun latestBodyMetricOnOrBefore(scope: AccountScope, date: String): BodyReadEntry? = null
    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int = 0
    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> {
        scopes += scope.ownerId
        return entries.map { it.date }.distinct()
    }
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
    private val dateSet: Set<String>
) : MealReadApi {
    val scopes = mutableListOf<String>()

    override fun mealCount(scope: AccountScope, date: String): Int = 0
    override fun mealTotals(scope: AccountScope, date: String) =
        MealReadNutritionTotals(0, emptyMap())
    override fun meals(scope: AccountScope, date: String): List<MealReadSummary> {
        scopes += scope.ownerId
        return emptyList()
    }
    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int = 0
    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> {
        scopes += scope.ownerId
        return dateSet.toList()
    }
    override fun nutritionSummary(scope: AccountScope, startDate: String, endDate: String) =
        MealNutritionReadSummary(0.0, 0, 0, 0)
}
