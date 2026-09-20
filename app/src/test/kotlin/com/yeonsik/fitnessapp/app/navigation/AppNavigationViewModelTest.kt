package com.yeonsik.fitnessapp.app.navigation

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayList

class AppNavigationViewModelTest {
    private val synchronousTaskExecutor = object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread(): Boolean = true
    }

    @Before
    fun setUpLiveDataExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(synchronousTaskExecutor)
    }

    @After
    fun resetLiveDataExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun firstRecordsEntryUsesRecordsInnerTab() {
        val navigation = AppNavigationViewModel(SavedStateHandle())

        navigation.selectTopLevel(FitnessScreen.RECORDS)

        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())
        assertEquals(RecordsHubTab.RECORDS, navigation.recordsHubTab())
    }

    @Test
    fun legacyDeepNavigationOpensTheMatchingRecordsInnerTab() {
        val navigation = AppNavigationViewModel(SavedStateHandle())

        navigation.navigate(FitnessScreen.STATISTICS)

        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())
        assertEquals(RecordsHubTab.STATISTICS, navigation.recordsHubTab())
        assertEquals(
            arrayListOf(FitnessScreen.HOME.name, FitnessScreen.RECORDS.name),
            navigation.savedScreenNames()
        )
    }

    @Test
    fun recordsInnerTabSurvivesLeavingAndReturningToTopLevel() {
        val navigation = AppNavigationViewModel(SavedStateHandle())
        navigation.selectRecordsHubTab(RecordsHubTab.DEVELOPMENT)

        navigation.selectTopLevel(FitnessScreen.WORKOUT)
        navigation.selectTopLevel(FitnessScreen.RECORDS)

        assertEquals(RecordsHubTab.DEVELOPMENT, navigation.recordsHubTab())
        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())
    }

    @Test
    fun legacySavedHistoryIsCanonicalizedToRecordsHub() {
        val navigation = AppNavigationViewModel(SavedStateHandle())

        navigation.restore(
            screenName = FitnessScreen.DEVELOPMENT.name,
            savedHistory = ArrayList(listOf(FitnessScreen.HOME.name, FitnessScreen.DEVELOPMENT.name)),
            today = "2026-09-20",
            selectedMealDate = "2026-09-20",
            selectedRecordsDate = "2026-09-20",
            selectedRoutineId = null,
            recordsHubTabName = RecordsHubTab.RECORDS.name
        )

        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())
        assertEquals(RecordsHubTab.DEVELOPMENT, navigation.recordsHubTab())
        assertEquals(
            arrayListOf(FitnessScreen.HOME.name, FitnessScreen.RECORDS.name),
            navigation.savedScreenNames()
        )
    }

    @Test
    fun swipeMovesOnlyAcrossTheFourTopLevelTabsAndPreservesRecordsSelection() {
        val navigation = AppNavigationViewModel(SavedStateHandle())

        assertTrue(navigation.canSwipeTopLevel())
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.WORKOUT, navigation.currentScreen())
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())

        navigation.selectRecordsHubTab(RecordsHubTab.STATISTICS)
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.SETTINGS, navigation.currentScreen())
        assertFalse(navigation.swipeTopLevel(forward = true))
        assertTrue(navigation.swipeTopLevel(forward = false))

        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())
        assertEquals(RecordsHubTab.STATISTICS, navigation.recordsHubTab())
        assertTrue(navigation.swipeTopLevel(forward = false))
        assertEquals(FitnessScreen.WORKOUT, navigation.currentScreen())
        assertTrue(navigation.swipeTopLevel(forward = false))
        assertEquals(FitnessScreen.HOME, navigation.currentScreen())
        assertFalse(navigation.swipeTopLevel(forward = false))
    }

    @Test
    fun swipeIsDisabledForFocusedChildDestinations() {
        val focusedScreens = listOf(
            FitnessScreen.STRENGTH,
            FitnessScreen.ROUTINE_DETAIL,
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_SESSION,
            FitnessScreen.WORKOUT_EXERCISE_DETAIL,
            FitnessScreen.WORKOUT_EXERCISE_ADD,
            FitnessScreen.MEALS
        )

        focusedScreens.forEach { focusedScreen ->
            val navigation = AppNavigationViewModel(SavedStateHandle())
            navigation.navigate(focusedScreen)

            assertFalse(navigation.canSwipeTopLevel())
            assertFalse(navigation.swipeTopLevel(forward = true))
            assertEquals(focusedScreen, navigation.currentScreen())
        }
    }
}