package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeBriefingTest {
    @Test
    fun noCompletedWorkoutOrWeightShowsMissingRecordStates() {
        val snapshot = snapshot()

        assertNull(homeWorkoutSummary(snapshot))
        assertEquals("체중 미기록 · 식사 미기록", homeBodyMealSummary(snapshot, false))
    }

    @Test
    fun completedWorkoutUsesTodaysMetricsAndBodyMealValues() {
        val snapshot = snapshot().copy(
            dayMetrics = mapOf("2026-10-01" to HomeDayWorkoutMetrics(1, 18, 12_400.0, 4_080)),
            mealCounts = mapOf("2026-10-01" to 3),
            todayWeight = HomeBodyMetric("weight", "2026-10-01", 88.4, "")
        )

        assertEquals("1회 · 18세트 · 12.4t · 1시간 8분", homeWorkoutSummary(snapshot))
        assertEquals("체중 88.4kg · 식사 3회", homeBodyMealSummary(snapshot, true))
    }

    @Test
    fun onlyTodaysCompletedMetricsAreShown() {
        val snapshot = snapshot().copy(
            dayMetrics = mapOf("2026-09-30" to HomeDayWorkoutMetrics(1, 10, 850.0, 1_800))
        )

        assertNull(homeWorkoutSummary(snapshot))
    }

    private fun snapshot() = HomeSnapshot(
        ownerId = "owner",
        today = "2026-10-01",
        todaySessions = emptyList(),
        activeRoutineId = null,
        routines = emptyList(),
        routineExercises = emptyMap(),
        latestRoutineDates = emptyMap(),
        inProgressSessionId = null,
        dayMetrics = emptyMap(),
        mealCounts = emptyMap(),
        mealNutritionTotals = emptyMap(),
        nutritionGoal = null,
        todayWeight = null,
        todayBodyMetrics = emptyList(),
        todayMeals = emptyList()
    )
}
