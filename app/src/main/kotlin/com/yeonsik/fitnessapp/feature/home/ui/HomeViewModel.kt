package com.yeonsik.fitnessapp.feature.home.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Ready(val snapshot: HomeSnapshot) : HomeUiState
    data class Error(val ownerId: String, val message: String) : HomeUiState
}

class HomeViewModel @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") private val savedStateHandle: SavedStateHandle,
    private val repository: HomeRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val uiState: LiveData<HomeUiState> = mutableState
    @Volatile private var requestVersion = 0L

    fun enter(scope: AccountScope, today: String) {
        val request = ++requestVersion
        mutableState.value = HomeUiState.Loading
        executor.execute {
            try {
                val snapshot = repository.load(scope, today)
                if (request == requestVersion) mutableState.postValue(HomeUiState.Ready(snapshot))
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(
                    HomeUiState.Error(scope.ownerId, error.message ?: "홈을 불러오지 못했습니다.")
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
