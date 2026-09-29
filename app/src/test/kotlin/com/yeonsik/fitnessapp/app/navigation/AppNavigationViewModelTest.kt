package com.yeonsik.fitnessapp.app.navigation

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun startupCompletionIsRetainedByViewModelButNotSavedForProcessRecreation() {
        val savedState = SavedStateHandle()
        val navigation = AppNavigationViewModel(savedState)

        assertFalse(navigation.startupCompleted.value == true)
        navigation.completeStartup()
        assertTrue(navigation.startupCompleted.value == true)

        val recreated = AppNavigationViewModel(savedState)
        assertFalse(recreated.startupCompleted.value == true)
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
        assertNull(navigation.adjacentTopLevel(forward = false))
        assertEquals(FitnessScreen.WORKOUT, navigation.adjacentTopLevel(forward = true))
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.WORKOUT, navigation.currentScreen())
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.RECORDS, navigation.currentScreen())

        navigation.selectRecordsHubTab(RecordsHubTab.STATISTICS)
        assertTrue(navigation.swipeTopLevel(forward = true))
        assertEquals(FitnessScreen.SETTINGS, navigation.currentScreen())
        assertNull(navigation.adjacentTopLevel(forward = true))
        assertEquals(FitnessScreen.RECORDS, navigation.adjacentTopLevel(forward = false))
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
            assertNull(navigation.adjacentTopLevel(forward = true))
            assertFalse(navigation.swipeTopLevel(forward = true))
            assertEquals(focusedScreen, navigation.currentScreen())
        }
    }

    @Test
    fun swipeVisualSelectionChangesAtCommitWithoutChangingRouteUntilSettle() {
        val navigation = AppNavigationViewModel(SavedStateHandle())
        val source = navigation.currentScreen()
        val destination = topLevelSwipeDestination(
            dragOffset = -80f,
            threshold = 72f,
            forwardPage = navigation.adjacentTopLevel(forward = true),
            backwardPage = navigation.adjacentTopLevel(forward = false)
        )
        val selection = TopLevelSwipeSelection(source, destination!!)

        assertEquals(FitnessScreen.WORKOUT, visualActiveTopLevelTab(source, selection))
        assertEquals(FitnessScreen.HOME, navigation.currentScreen())

        navigation.selectTopLevel(destination)

        assertEquals(FitnessScreen.WORKOUT, navigation.currentScreen())
        assertEquals(FitnessScreen.WORKOUT, visualActiveTopLevelTab(navigation.currentScreen(), selection))
    }

    @Test
    fun swipeBelowThresholdAndOutsideEdgesKeepsTheCurrentVisualTab() {
        val navigation = AppNavigationViewModel(SavedStateHandle())
        val home = navigation.currentScreen()

        assertNull(topLevelSwipeDestination(-71f, 72f, FitnessScreen.WORKOUT, null))
        assertNull(topLevelSwipeDestination(80f, 72f, FitnessScreen.WORKOUT, null))
        assertEquals(home, visualActiveTopLevelTab(home, null))

        navigation.selectTopLevel(FitnessScreen.SETTINGS)
        assertNull(topLevelSwipeDestination(-80f, 72f, null, FitnessScreen.RECORDS))
        assertEquals(FitnessScreen.SETTINGS, visualActiveTopLevelTab(navigation.currentScreen(), null))
        assertEquals(
            FitnessScreen.SETTINGS,
            visualActiveTopLevelTab(
                navigation.currentScreen(),
                TopLevelSwipeSelection(FitnessScreen.HOME, FitnessScreen.WORKOUT)
            )
        )
    }

    @Test
    fun completedWorkoutStateDoesNotChangeTheSessionEntryEffectKey() {
        val loadingKey = RecordEntryEffectKey(
            FitnessScreen.WORKOUT_SESSION, "owner", "historical-record"
        )
        val completedKey = RecordEntryEffectKey(
            FitnessScreen.WORKOUT_SESSION, "owner", "historical-record"
        )

        assertEquals(loadingKey, completedKey)
        assertFalse(loadingKey == RecordEntryEffectKey(
            FitnessScreen.WORKOUT_SESSION, "owner", "another-record"
        ))
        assertFalse(loadingKey == RecordEntryEffectKey(
            FitnessScreen.WORKOUT_SUMMARY, "owner", "historical-record"
        ))
    }
}
