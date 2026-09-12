package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitnessapp.app.navigation.homeEntryEffectKey
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun inProgressSnapshotDoesNotCauseAnotherHomeLoad() {
        val snapshot = HomeSnapshot(
            ownerId = "owner",
            today = "2026-09-12",
            todaySessions = emptyList(),
            activeRoutineId = null,
            routines = emptyList(),
            routineExercises = emptyMap(),
            latestRoutineDates = emptyMap(),
            inProgressSessionId = "session-in-progress",
            dayMetrics = emptyMap(),
            mealCounts = emptyMap(),
            mealNutritionTotals = emptyMap(),
            nutritionGoal = null,
            todayWeight = null,
            todayBodyMetrics = emptyList(),
            todayMeals = emptyList()
        )
        val states = listOf<HomeUiState>(
            HomeUiState.Idle,
            HomeUiState.Loading,
            HomeUiState.Ready(snapshot)
        )

        val keysByScreen = listOf(FitnessScreen.HOME, FitnessScreen.STRENGTH).map { screen ->
            states.map { _ ->
                homeEntryEffectKey(screen, "owner", "2026-09-12")
            }
        }

        keysByScreen.forEach { keys -> assertEquals(1, keys.distinct().size) }
        assertEquals("session-in-progress", snapshot.inProgressSessionId)
    }
}
