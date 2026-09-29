package com.yeonsik.fitnessapp.app.navigation

import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeRequestIdentity
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.feature.records.ui.RecordsUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLoadingGateTest {
    private val owner = "owner-a"
    private val today = "2026-09-29"

    @Test
    fun waitsForBothMatchingSnapshots() {
        val home = readyHome()
        val records = readyRecords()

        assertFalse(startupDataSettled(HomeUiState.Loading, records, owner, today))
        assertFalse(startupDataSettled(home, RecordsUiState.Idle, owner, today))
        assertFalse(startupDataSettled(readyHome("owner-b"), records, owner, today))
        assertFalse(startupDataSettled(home, readyRecords(month = "2026-08"), owner, today))
        assertFalse(startupDataSettled(home, readyRecords(date = "2026-09-28"), owner, today))
        assertTrue(startupDataSettled(home, records, owner, today))
    }

    @Test
    fun keepsFastLoadsVisibleForAtLeast550Milliseconds() {
        val home = readyHome()
        val records = readyRecords()

        assertFalse(startupCanComplete(home, records, owner, today, 100))
        assertFalse(startupCanComplete(home, records, owner, today, 549))
        assertTrue(startupCanComplete(home, records, owner, today, 550))
        assertTrue(startupCanComplete(home, records, owner, today, 1_800))
    }

    @Test
    fun loadingFailsOpenAtTenSeconds() {
        val loadingRecords = RecordsUiState.Loading(owner, "2026-09", today)

        assertFalse(startupCanComplete(
            HomeUiState.Loading, loadingRecords, owner, today, 9_999
        ))
        assertTrue(startupCanComplete(
            HomeUiState.Loading, loadingRecords, owner, today, 10_000
        ))
        assertTrue(startupCanComplete(
            readyHome(), loadingRecords, owner, today, 10_000
        ))
    }

    @Test
    fun matchingErrorsReleaseGateAndForeignErrorsDoNot() {
        assertTrue(startupDataSettled(
            HomeUiState.Error(owner, "unavailable", today), RecordsUiState.Loading(
                owner, "2026-09", today
            ), owner, today
        ))
        assertTrue(startupDataSettled(
            HomeUiState.Loading,
            RecordsUiState.Error(owner, "2026-09", today, "unavailable"),
            owner, today
        ))
        assertFalse(startupDataSettled(
            HomeUiState.Error("owner-b", "unavailable", today), readyRecords(), owner, today
        ))
    }

    private fun readyHome(snapshotOwner: String = owner): HomeUiState.Ready {
        val snapshot = HomeSnapshot(
            ownerId = snapshotOwner,
            today = today,
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
        return HomeUiState.Ready(snapshot, HomeRequestIdentity(snapshotOwner, today))
    }

    private fun readyRecords(
        month: String = "2026-09",
        date: String = today
    ) = RecordsUiState.Ready(
        RecordsSnapshot(owner, month, date, emptyList(), emptyMap(), emptyList())
    )
}
