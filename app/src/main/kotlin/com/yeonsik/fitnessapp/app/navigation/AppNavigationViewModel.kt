package com.yeonsik.fitnessapp.app.navigation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.state.FitnessNavigationHistory
import com.yeonsik.fitnessapp.state.FitnessScreen
import java.time.LocalDate

/**
 * App-level navigation state that survives Activity recreation.
 *
 * Feature ViewModels still own feature state. This ViewModel only owns the
 * route, the small set of cross-route selection values, and the existing
 * push/back/replace policy.
 */
data class AppNavigationState(
    val screen: FitnessScreen = FitnessScreen.HOME,
    val today: String = LocalDate.now().toString(),
    val selectedMealDate: String = today,
    val selectedRecordsDate: String = today,
    val selectedRoutineId: String? = null
)

class AppNavigationViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val history = FitnessNavigationHistory(readScreen())
    private val mutableState = MutableLiveData(snapshot())
    val uiState: LiveData<AppNavigationState> = mutableState

    fun restore(
        screenName: String?,
        savedHistory: ArrayList<String>?,
        today: String?,
        selectedMealDate: String?,
        selectedRecordsDate: String?,
        selectedRoutineId: String?
    ) {
        val restoredScreen = parseScreen(screenName)
        try {
            history.restoreScreenNames(savedHistory)
        } catch (_: IllegalArgumentException) {
            history.restoreCurrent(restoredScreen)
        } catch (_: NullPointerException) {
            history.restoreCurrent(restoredScreen)
        }
        val resolvedToday = today?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
        savedStateHandle[KEY_TODAY] = resolvedToday
        savedStateHandle[KEY_MEAL_DATE] = selectedMealDate?.takeIf { it.isNotBlank() } ?: resolvedToday
        savedStateHandle[KEY_RECORDS_DATE] = selectedRecordsDate?.takeIf { it.isNotBlank() } ?: resolvedToday
        savedStateHandle[KEY_ROUTINE_ID] = selectedRoutineId
        publish()
    }

    fun updateToday(today: String) {
        if (today.isBlank()) return
        savedStateHandle[KEY_TODAY] = today
        if (history.current() != FitnessScreen.MEALS) {
            savedStateHandle[KEY_MEAL_DATE] = today
        }
        if (history.current() != FitnessScreen.RECORDS) {
            savedStateHandle[KEY_RECORDS_DATE] = today
        }
        publish()
    }

    fun selectMealDate(date: String) {
        if (date.isBlank()) return
        savedStateHandle[KEY_MEAL_DATE] = date
        publish()
    }

    fun selectRecordsDate(date: String) {
        if (date.isBlank()) return
        savedStateHandle[KEY_RECORDS_DATE] = date
        publish()
    }

    fun selectRoutine(routineId: String?) {
        savedStateHandle[KEY_ROUTINE_ID] = routineId
        publish()
    }

    fun navigate(screen: FitnessScreen) {
        history.push(screen)
        publish()
    }

    fun replace(screen: FitnessScreen) {
        history.replace(screen)
        publish()
    }

    fun back(): Boolean {
        history.back() ?: return false
        publish()
        return true
    }

    fun currentScreen(): FitnessScreen = history.current()

    fun canBack(): Boolean = history.canBack()

    fun savedScreenNames(): ArrayList<String> = history.savedScreenNames()

    fun selectedMealDate(): String = stateSelectedMealDate()

    fun selectedRecordsDate(): String = stateSelectedRecordsDate()

    fun selectedRoutineId(): String? = savedStateHandle[KEY_ROUTINE_ID]

    private fun snapshot(): AppNavigationState = AppNavigationState(
        screen = history.current(),
        today = savedStateHandle[KEY_TODAY] ?: LocalDate.now().toString(),
        selectedMealDate = stateSelectedMealDate(),
        selectedRecordsDate = stateSelectedRecordsDate(),
        selectedRoutineId = savedStateHandle[KEY_ROUTINE_ID]
    )

    private fun stateSelectedMealDate(): String =
        savedStateHandle[KEY_MEAL_DATE]
            ?: savedStateHandle[KEY_TODAY]
            ?: LocalDate.now().toString()

    private fun stateSelectedRecordsDate(): String =
        savedStateHandle[KEY_RECORDS_DATE]
            ?: savedStateHandle[KEY_TODAY]
            ?: LocalDate.now().toString()

    private fun publish() {
        savedStateHandle[KEY_SCREEN] = history.current().name
        savedStateHandle[KEY_HISTORY] = history.savedScreenNames()
        mutableState.value = snapshot()
    }

    private fun readScreen(): FitnessScreen =
        parseScreen(savedStateHandle[KEY_SCREEN] as? String)

    private fun parseScreen(value: String?): FitnessScreen =
        runCatching { FitnessScreen.valueOf(value ?: FitnessScreen.HOME.name) }
            .getOrDefault(FitnessScreen.HOME)

    private companion object {
        const val KEY_SCREEN = "navigation.screen"
        const val KEY_HISTORY = "navigation.history"
        const val KEY_TODAY = "navigation.today"
        const val KEY_MEAL_DATE = "navigation.meal_date"
        const val KEY_RECORDS_DATE = "navigation.records_date"
        const val KEY_ROUTINE_ID = "navigation.routine_id"
    }
}
