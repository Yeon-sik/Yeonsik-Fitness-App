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
 * Feature ViewModels still own feature state. This ViewModel owns the route,
 * cross-route selection values, the push/back/replace policy, and an unsaved
 * Activity-lifetime startup flag.
 */
data class AppNavigationState(
    val screen: FitnessScreen = FitnessScreen.HOME,
    val today: String = LocalDate.now().toString(),
    val selectedMealDate: String = today,
    val selectedRecordsDate: String = today,
    val selectedRoutineId: String? = null,
    val recordsHubTab: RecordsHubTab = RecordsHubTab.RECORDS,
    val topLevelEntrance: TopLevelEntranceEvent? = null
)

/** An arrival, not a composition or data-loading event. Pending arrivals are runtime only. */
data class TopLevelEntranceEvent(val destination: FitnessScreen, val generation: Long)

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
    private val freshNavigation = !savedStateHandle.contains(KEY_SCREEN)
    private val initialScreen = readScreen()
    private val history = FitnessNavigationHistory(canonicalScreen(initialScreen))
    private val mutableState = MutableLiveData<AppNavigationState>()
    val uiState: LiveData<AppNavigationState> = mutableState
    // Runtime only: a restored process must preload Home and Records again.
    private val mutableStartupCompleted = MutableLiveData(false)
    val startupCompleted: LiveData<Boolean> = mutableStartupCompleted
    private var entranceGeneration = savedStateHandle.get<Long>(KEY_ENTRANCE_GENERATION) ?: 0L
    private var topLevelEntrance: TopLevelEntranceEvent? =
        if (freshNavigation) newEntrance(FitnessScreen.HOME) else null

    fun completeStartup() {
        if (mutableStartupCompleted.value != true) mutableStartupCompleted.value = true
    }

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
        topLevelEntrance = null
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
        topLevelEntrance = null
        when (val hubTab = RecordsHubTab.forScreen(screen)) {
            null -> history.push(screen)
            else -> openRecordsHub(hubTab, replace = false)
        }
        publish()
    }

    /** Replaces a destination while preserving the existing session/back-stack policy. */
    fun replace(screen: FitnessScreen) {
        topLevelEntrance = null
        when (val hubTab = RecordsHubTab.forScreen(screen)) {
            null -> history.replace(screen)
            else -> openRecordsHub(hubTab, replace = true)
        }
        publish()
    }

    /** Selects one of the four fixed top-level tabs. Records keeps its last inner tab. */
    fun selectTopLevel(screen: FitnessScreen) {
        if (screen !in TOP_LEVEL_SCREENS) return
        topLevelEntrance = null
        history.replace(screen)
        publish()
    }

    /** Only an actual bottom-tab click from another top-level page creates an entrance. */
    fun selectTopLevelFromTab(screen: FitnessScreen) {
        if (screen !in TOP_LEVEL_SCREENS || screen == history.current()) return
        topLevelEntrance = if (history.current() in TOP_LEVEL_SCREENS &&
            screen in ANIMATED_TOP_LEVEL_SCREENS
        ) newEntrance(screen) else null
        history.replace(screen)
        publish()
    }

    /** A drag owns the arrival motion, including a drag that eventually springs back. */
    fun beginTopLevelSwipe() {
        if (!canSwipeTopLevel() || topLevelEntrance == null) return
        topLevelEntrance = null
        publish()
    }

    /** Top-level swipes are deliberately unavailable while a focused child screen is open. */
    fun canSwipeTopLevel(): Boolean = history.current() in TOP_LEVEL_SCREENS

    /** Moves to an adjacent top-level tab without wrapping at either end. */
    fun swipeTopLevel(forward: Boolean): Boolean {
        val destination = adjacentTopLevel(forward) ?: return false
        selectTopLevel(destination)
        return true
    }

    /** Returns the page that can be revealed during a top-level drag. */
    fun adjacentTopLevel(forward: Boolean): FitnessScreen? {
        val currentIndex = TOP_LEVEL_SCREENS.indexOf(history.current())
        if (currentIndex < 0) return null
        return TOP_LEVEL_SCREENS.getOrNull(currentIndex + if (forward) 1 else -1)
    }

    fun selectRecordsHubTab(tab: RecordsHubTab) {
        topLevelEntrance = null
        savedStateHandle[KEY_RECORDS_HUB_TAB] = tab.name
        history.replace(FitnessScreen.RECORDS)
        publish()
    }

    fun back(): Boolean {
        history.back() ?: return false
        topLevelEntrance = null
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
        recordsHubTab = recordsHubTab(),
        topLevelEntrance = topLevelEntrance
    )

    private fun newEntrance(destination: FitnessScreen): TopLevelEntranceEvent {
        entranceGeneration += 1L
        savedStateHandle[KEY_ENTRANCE_GENERATION] = entranceGeneration
        return TopLevelEntranceEvent(destination, entranceGeneration)
    }

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
        val ANIMATED_TOP_LEVEL_SCREENS = setOf(
            FitnessScreen.HOME, FitnessScreen.WORKOUT, FitnessScreen.RECORDS
        )
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
        const val KEY_ENTRANCE_GENERATION = "navigation.entrance_generation"
    }
}
