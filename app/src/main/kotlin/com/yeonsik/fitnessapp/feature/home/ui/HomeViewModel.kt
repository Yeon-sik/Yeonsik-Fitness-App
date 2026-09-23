package com.yeonsik.fitnessapp.feature.home.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.routine.model.RoutineSummary
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
}

class HomeViewModel @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") private val savedStateHandle: SavedStateHandle,
    private val repository: HomeRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val uiState: LiveData<HomeUiState> = mutableState
    private val requestGate = HomeRequestGate()
    private var loadingIdentity: HomeRequestIdentity? = null
    private var stale = false

    fun enterIfNeeded(scope: AccountScope, today: String) {
        val identity = HomeRequestIdentity(scope.ownerId, today.trim())
        val current = mutableState.value
        if (!stale && (current is HomeUiState.Ready && current.requestIdentity == identity ||
                    current is HomeUiState.Loading && loadingIdentity == identity)) return
        enter(scope, today)
    }

    fun markStale() { stale = true }

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
                if (requestGate.accepts(request, identity) &&
                    snapshot.ownerId == identity.ownerId && snapshot.today == identity.date
                ) {
                    mutableState.postValue(HomeUiState.Ready(snapshot, identity))
                }
            } catch (error: Exception) {
                if (requestGate.accepts(request, identity)) mutableState.postValue(
                    HomeUiState.Error(scope.ownerId, error.message ?: "홈을 불러오지 못했습니다.", identity.date)
                )
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
        executor.shutdownNow()
    }
}
