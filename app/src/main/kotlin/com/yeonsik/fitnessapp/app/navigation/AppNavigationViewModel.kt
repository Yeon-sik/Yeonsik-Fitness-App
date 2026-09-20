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
    val selectedRoutineId: String? = null,
    val recordsHubTab: RecordsHubTab = RecordsHubTab.RECORDS
)

/** The three destinations hosted by the top-level Records tab. */
enum class RecordsHubTab(
    val label: String,
    val screen: FitnessScreen
) {
    RECORDS("기록", FitnessScreen.RECORDS),
    DEVELOPMENT("발전", FitnessScreen.DEVELOPMENT),
    STATISTICS("통계", FitnessScreen.STATISTICS);

    companion object {
        fun fromName(value: String?): RecordsHubTab =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(RECORDS)

        fun forScreen(screen: FitnessScreen): RecordsHubTab? = when (screen) {
            FitnessScreen.RECORDS -> RECORDS
            FitnessScreen.DEVELOPMENT -> DEVELOPMENT
            FitnessScreen.STATISTICS -> STATISTICS
            else -> null
        }
    }
}

class AppNavigationViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val initialScreen = readScreen()
    private val history = FitnessNavigationHistory(canonicalScreen(initialScreen))
    private val mutableState = MutableLiveData<AppNavigationState>()
    val uiState: LiveData<AppNavigationState> = mutableState

    init {
        savedStateHandle[KEY_RECORDS_HUB_TAB] = initialHubTab(initialScreen).name
        publish()
    }

    fun restore(
        screenName: String?,
        savedHistory: ArrayList<String>?,
        today: String?,
        selectedMealDate: String?,
        selectedRecordsDate: String?,
        selectedRoutineId: String?,
        recordsHubTabName: String?
    ) {
        val restoredScreen = parseScreen(screenName)
        val resolvedHubTab = restoredHubTab(
            restoredScreen,
            recordsHubTabName ?: savedStateHandle[KEY_RECORDS_HUB_TAB]
        )
        try {
            history.restoreScreenNames(normalizeHistory(savedHistory))
        } catch (_: IllegalArgumentException) {
            history.restoreCurrent(canonicalScreen(restoredScreen))
        } catch (_: NullPointerException) {
            history.restoreCurrent(canonicalScreen(restoredScreen))
        }
        val resolvedToday = today?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
        savedStateHandle[KEY_TODAY] = resolvedToday
        savedStateHandle[KEY_MEAL_DATE] = selectedMealDate?.takeIf { it.isNotBlank() } ?: resolvedToday
        savedStateHandle[KEY_RECORDS_DATE] = selectedRecordsDate?.takeIf { it.isNotBlank() } ?: resolvedToday
        savedStateHandle[KEY_ROUTINE_ID] = selectedRoutineId
        savedStateHandle[KEY_RECORDS_HUB_TAB] = resolvedHubTab.name
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

    /** Deep navigation entry point. Legacy Statistics/Development routes open the Records hub. */
    fun navigate(screen: FitnessScreen) {
        when (val hubTab = RecordsHubTab.forScreen(screen)) {
            null -> history.push(screen)
            else -> openRecordsHub(hubTab, replace = false)
        }
        publish()
    }

    /** Replaces a destination while preserving the existing session/back-stack policy. */
    fun replace(screen: FitnessScreen) {
        when (val hubTab = RecordsHubTab.forScreen(screen)) {
            null -> history.replace(screen)
            else -> openRecordsHub(hubTab, replace = true)
        }
        publish()
    }

    /** Selects one of the four fixed top-level tabs. Records keeps its last inner tab. */
    fun selectTopLevel(screen: FitnessScreen) {
        if (screen !in TOP_LEVEL_SCREENS) return
        history.replace(screen)
        publish()
    }

    /** Top-level swipes are deliberately unavailable while a focused child screen is open. */
    fun canSwipeTopLevel(): Boolean = history.current() in TOP_LEVEL_SCREENS

    /** Moves to an adjacent top-level tab without wrapping at either end. */
    fun swipeTopLevel(forward: Boolean): Boolean {
        val currentIndex = TOP_LEVEL_SCREENS.indexOf(history.current())
        if (currentIndex < 0) return false
        val destination = TOP_LEVEL_SCREENS.getOrNull(
            currentIndex + if (forward) 1 else -1
        ) ?: return false
        selectTopLevel(destination)
        return true
    }

    fun selectRecordsHubTab(tab: RecordsHubTab) {
        savedStateHandle[KEY_RECORDS_HUB_TAB] = tab.name
        history.replace(FitnessScreen.RECORDS)
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

    fun recordsHubTab(): RecordsHubTab =
        RecordsHubTab.fromName(savedStateHandle[KEY_RECORDS_HUB_TAB])

    private fun snapshot(): AppNavigationState = AppNavigationState(
        screen = history.current(),
        today = savedStateHandle[KEY_TODAY] ?: LocalDate.now().toString(),
        selectedMealDate = stateSelectedMealDate(),
        selectedRecordsDate = stateSelectedRecordsDate(),
        selectedRoutineId = savedStateHandle[KEY_ROUTINE_ID],
        recordsHubTab = recordsHubTab()
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

    private fun openRecordsHub(tab: RecordsHubTab, replace: Boolean) {
        savedStateHandle[KEY_RECORDS_HUB_TAB] = tab.name
        if (replace) {
            history.replace(FitnessScreen.RECORDS)
        } else {
            history.push(FitnessScreen.RECORDS)
        }
    }

    private fun normalizeHistory(names: List<String>?): ArrayList<String> {
        if (names == null || names.isEmpty()) {
            throw IllegalArgumentException("navigation history is required")
        }
        val normalized = ArrayList<String>(names.size)
        for (name in names) {
            val canonical = canonicalScreen(FitnessScreen.valueOf(name))
            if (normalized.isEmpty() && canonical != FitnessScreen.HOME) {
                throw IllegalArgumentException("navigation history must start at HOME")
            }
            if (normalized.lastOrNull() != canonical.name) {
                normalized.add(canonical.name)
            }
        }
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("navigation history is empty")
        }
        return normalized
    }

    private fun canonicalScreen(screen: FitnessScreen): FitnessScreen =
        if (RecordsHubTab.forScreen(screen) != null) FitnessScreen.RECORDS else screen

    private fun initialHubTab(screen: FitnessScreen): RecordsHubTab =
        restoredHubTab(screen, savedStateHandle[KEY_RECORDS_HUB_TAB])

    private fun restoredHubTab(screen: FitnessScreen, savedName: String?): RecordsHubTab =
        when (screen) {
            FitnessScreen.DEVELOPMENT -> RecordsHubTab.DEVELOPMENT
            FitnessScreen.STATISTICS -> RecordsHubTab.STATISTICS
            else -> RecordsHubTab.fromName(savedName)
        }

    private fun readScreen(): FitnessScreen =
        parseScreen(savedStateHandle[KEY_SCREEN] as? String)

    private fun parseScreen(value: String?): FitnessScreen =
        runCatching { FitnessScreen.valueOf(value ?: FitnessScreen.HOME.name) }
            .getOrDefault(FitnessScreen.HOME)

    private companion object {
        val TOP_LEVEL_SCREENS = listOf(
            FitnessScreen.HOME,
            FitnessScreen.WORKOUT,
            FitnessScreen.RECORDS,
            FitnessScreen.SETTINGS
        )

        const val KEY_SCREEN = "navigation.screen"
        const val KEY_HISTORY = "navigation.history"
        const val KEY_TODAY = "navigation.today"
        const val KEY_MEAL_DATE = "navigation.meal_date"
        const val KEY_RECORDS_DATE = "navigation.records_date"
        const val KEY_ROUTINE_ID = "navigation.routine_id"
        const val KEY_RECORDS_HUB_TAB = "navigation.records_hub_tab"
    }
}
