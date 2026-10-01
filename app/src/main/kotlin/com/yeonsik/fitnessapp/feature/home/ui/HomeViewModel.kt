package com.yeonsik.fitnessapp.feature.home.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityHistoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindowPolicy
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.routine.model.RoutineSummary
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.time.LocalDate

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Ready(
        val snapshot: HomeSnapshot,
        val requestIdentity: HomeRequestIdentity = HomeRequestIdentity(snapshot.ownerId, snapshot.today)
    ) : HomeUiState
    data class Error(val ownerId: String, val message: String, val date: String? = null) : HomeUiState
}

data class HomeRequestIdentity(val ownerId: String, val date: String)

/** Monotonic token plus owner/date identity prevents an old read from being published. */
internal class HomeRequestGate {
    private var nextToken = 0L
    @Volatile private var active: Pair<Long, HomeRequestIdentity>? = null

    @Synchronized
    fun begin(identity: HomeRequestIdentity): Long {
        val token = ++nextToken
        active = token to identity
        return token
    }

    fun accepts(token: Long, identity: HomeRequestIdentity): Boolean =
        active == (token to identity)

    @Synchronized fun invalidate() { active = null }
}

class HomeViewModel @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") private val savedStateHandle: SavedStateHandle,
    private val repository: HomeRepositoryApi,
    private val activityRepository: HomeActivityHistoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val mainExecutor: Executor = Executor { Handler(Looper.getMainLooper()).post(it) }
) : ViewModel() {
    private val mutableState = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val uiState: LiveData<HomeUiState> = mutableState
    private val requestGate = HomeRequestGate()
    private var loadingIdentity: HomeRequestIdentity? = null
    private var stale = false
    private val mutableActivityState = MutableLiveData<HomeActivityUiState>(HomeActivityUiState.Idle)
    val activityState: LiveData<HomeActivityUiState> = mutableActivityState
    private val mutableActivityDayDetails =
        MutableLiveData<HomeActivityDayDetailsUiState>(HomeActivityDayDetailsUiState.Idle)
    val activityDayDetails: LiveData<HomeActivityDayDetailsUiState> = mutableActivityDayDetails
    private val activityRequestGate = HomeActivityRequestGate()
    private var activityDetailsGeneration = 0L
    private var activityScope: AccountScope? = null
    private var activityToday: String? = null
    private var activityPageOffset = 0
    private var earliestLoaded = false
    private var firstRecordedDate: String? = null
    private val activityCache = mutableMapOf<HomeActivityRequestIdentity, HomeActivityUiState.Ready>()

    fun enterIfNeeded(scope: AccountScope, today: String) {
        val identity = HomeRequestIdentity(scope.ownerId, today.trim())
        val current = mutableState.value
        if (!stale && (current is HomeUiState.Ready && current.requestIdentity == identity ||
                    current is HomeUiState.Loading && loadingIdentity == identity)) {
            enterActivityIfNeeded(scope, identity.date)
            return
        }
        enter(scope, today)
    }

    fun markStale() {
        stale = true
        requestGate.invalidate()
        invalidateActivity(clearEarliest = true)
    }

    fun enter(scope: AccountScope, today: String) {
        val requestedDate = today.trim()
        val identity = HomeRequestIdentity(scope.ownerId, requestedDate)
        val request = requestGate.begin(identity)
        loadingIdentity = identity
        stale = false
        mutableState.value = HomeUiState.Loading
        executor.execute {
            try {
                val snapshot = repository.load(scope, requestedDate)
                mainExecutor.execute {
                    if (requestGate.accepts(request, identity) &&
                        snapshot.ownerId == identity.ownerId && snapshot.today == identity.date
                    ) mutableState.value = HomeUiState.Ready(snapshot, identity)
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (requestGate.accepts(request, identity)) mutableState.value =
                        HomeUiState.Error(scope.ownerId, error.message ?: "홈을 불러오지 못했습니다.", identity.date)
                }
            }
        }
        enterActivityIfNeeded(scope, requestedDate)
    }

    private fun invalidateActivity(clearEarliest: Boolean) {
        activityRequestGate.invalidate()
        activityDetailsGeneration += 1L
        mutableActivityDayDetails.value = HomeActivityDayDetailsUiState.Idle
        activityCache.clear()
        activityPageOffset = 0
        if (clearEarliest) {
            earliestLoaded = false
            firstRecordedDate = null
        }
        mutableActivityState.value = HomeActivityUiState.Idle
    }

    private fun enterActivityIfNeeded(scope: AccountScope, today: String) {
        val ownerChanged = activityScope != scope
        if (ownerChanged || activityToday != today) {
            invalidateActivity(clearEarliest = ownerChanged)
            activityScope = scope
            activityToday = today
        }
        if (mutableActivityState.value == HomeActivityUiState.Idle) loadActivityPage(0)
    }

    fun previousActivityPage() = selectActivityPage(activityPageOffset + 1)
    fun nextActivityPage() = selectActivityPage(activityPageOffset - 1)

    fun selectActivityPage(pageOffset: Int) {
        val today = activityToday ?: return
        val first = firstRecordedDate ?: return
        val lastPage = HomeActivityWindowPolicy.lastPageOffset(LocalDate.parse(today), LocalDate.parse(first))
        if (pageOffset !in 0..lastPage) return
        if (pageOffset == activityPageOffset && mutableActivityState.value !is HomeActivityUiState.Error) return
        loadActivityPage(pageOffset)
    }

    fun retryActivityHistory() { loadActivityPage(activityPageOffset) }

    fun selectActivityDay(date: String) {
        val scope = activityScope ?: return
        val page = mutableActivityState.value as? HomeActivityUiState.Ready ?: return
        if (page.identity.ownerId != scope.ownerId || page.cells.none {
                it.state == com.yeonsik.fitnessapp.feature.home.model.HomeActivityCellState.TRACKED &&
                    it.date.toString() == date
            }
        ) return
        when (val current = mutableActivityDayDetails.value) {
            is HomeActivityDayDetailsUiState.Loading ->
                if (current.ownerId == scope.ownerId && current.date == date) return
            is HomeActivityDayDetailsUiState.Ready ->
                if (current.ownerId == scope.ownerId && current.details.date == date) return
            else -> Unit
        }

        val generation = ++activityDetailsGeneration
        mutableActivityDayDetails.value = HomeActivityDayDetailsUiState.Loading(scope.ownerId, date)
        executor.execute {
            val result = runCatching {
                activityRepository.detailsForDate(scope, date).also {
                    require(it.date == date) { "Activity details returned a different date." }
                }
            }
            mainExecutor.execute {
                if (generation != activityDetailsGeneration || activityScope != scope) return@execute
                mutableActivityDayDetails.value = result.fold(
                    onSuccess = { HomeActivityDayDetailsUiState.Ready(scope.ownerId, it) },
                    onFailure = { HomeActivityDayDetailsUiState.Error(scope.ownerId, date) }
                )
            }
        }
    }

    private fun loadActivityPage(pageOffset: Int) {
        val scope = activityScope ?: return
        val today = activityToday ?: return
        val identity = HomeActivityRequestIdentity(scope.ownerId, today, pageOffset)
        val request = activityRequestGate.begin(identity)
        activityPageOffset = pageOffset
        activityCache[identity]?.let {
            mutableActivityState.value = it
            return
        }
        val knownFirst = firstRecordedDate
        val wasEarliestLoaded = earliestLoaded
        val knownWindow = knownFirst?.let {
            HomeActivityWindowPolicy.window(LocalDate.parse(today), LocalDate.parse(it), pageOffset)
        }
        val previous = when (val state = mutableActivityState.value) {
            is HomeActivityUiState.Ready -> state
            is HomeActivityUiState.Loading -> state.previous
            is HomeActivityUiState.Error -> state.previous
            else -> null
        }?.takeIf { it.identity.ownerId == scope.ownerId && it.identity.today == today }
        mutableActivityState.value = HomeActivityUiState.Loading(identity, knownWindow, previous)
        executor.execute {
            var readFirst = knownFirst
            var readEarliest = wasEarliestLoaded
            var window = knownWindow
            val result = try {
                if (!readEarliest) {
                    readFirst = activityRepository.firstRecordedDate(scope)
                    readEarliest = true
                }
                val first = readFirst
                if (first == null) HomeActivityUiState.Empty(identity)
                else {
                    val loadedWindow = HomeActivityWindowPolicy.window(
                        LocalDate.parse(today), LocalDate.parse(first), pageOffset
                    )
                    window = loadedWindow
                    val kinds = activityRepository.recordedKindsByDate(
                        scope, loadedWindow.start.toString(), loadedWindow.queryEnd.toString()
                    )
                    HomeActivityUiState.Ready(identity, loadedWindow, loadedWindow.cells(kinds))
                }
            } catch (error: Exception) {
                HomeActivityUiState.Error(
                    identity, error.message ?: "활동 내역을 불러오지 못했습니다.", window, previous
                )
            }
            mainExecutor.execute {
                if (activityRequestGate.accepts(request, identity)) {
                    earliestLoaded = readEarliest
                    firstRecordedDate = readFirst
                    if (result is HomeActivityUiState.Ready) activityCache[identity] = result
                    mutableActivityState.value = result
                }
            }
        }
    }

    fun snapshot(): HomeSnapshot? = (mutableState.value as? HomeUiState.Ready)?.snapshot
    fun sessionsForDate(date: String): List<String> = snapshot()?.let {
        if (it.today == date) it.todaySessions else emptyList()
    } ?: emptyList()
    fun activeRoutineId(): String? = snapshot()?.activeRoutineId
    fun routines(): List<RoutineSummary> = snapshot()?.routines ?: emptyList()
    fun routineExercises(id: String): List<RoutineExerciseInstance> =
        snapshot()?.routineExercises?.get(id) ?: emptyList()
    fun latestRoutineDate(id: String): String? = snapshot()?.latestRoutineDates?.get(id)
    fun latestInProgressSessionId(): String? = snapshot()?.inProgressSessionId
    fun dayWorkoutMetrics(date: String): HomeDayWorkoutMetrics =
        snapshot()?.dayMetrics?.get(date) ?: HomeDayWorkoutMetrics(0, 0, 0.0, 0)
    fun mealCountForDate(date: String): Int = snapshot()?.mealCounts?.get(date) ?: 0
    fun mealNutritionTotalsForDate(date: String): HomeNutritionTotals? = snapshot()?.mealNutritionTotals?.get(date)
    fun nutritionGoal(): HomeNutritionGoal? = snapshot()?.nutritionGoal
    fun todayWeight(): HomeBodyMetric? = snapshot()?.todayWeight
    fun todayBodyMetrics(): List<HomeBodyMetric> = snapshot()?.todayBodyMetrics ?: emptyList()
    fun todayMeals(): List<HomeMealSummary> = snapshot()?.todayMeals ?: emptyList()

    override fun onCleared() {
        requestGate.invalidate()
        activityRequestGate.invalidate()
        executor.shutdownNow()
    }
}
