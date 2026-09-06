package com.yeonsik.fitnessapp.feature.home.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.data.NutritionTotals
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance
import com.yeonsik.fitnessapp.routine.RoutineRepository
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
    fun routines(): List<RoutineRepository.RoutineSummary> = snapshot()?.routines ?: emptyList()
    fun routineExercises(id: String): List<RoutineExerciseInstance> =
        snapshot()?.routineExercises?.get(id) ?: emptyList()
    fun latestRoutineDate(id: String): String? = snapshot()?.latestRoutineDates?.get(id)
    fun latestInProgressSessionId(): String? = snapshot()?.inProgressSessionId
    fun dayWorkoutMetrics(date: String): FitnessRepository.DayWorkoutMetrics =
        snapshot()?.dayMetrics?.get(date) ?: FitnessRepository.DayWorkoutMetrics()
    fun mealCountForDate(date: String): Int = snapshot()?.mealCounts?.get(date) ?: 0
    fun mealNutritionTotalsForDate(date: String): NutritionTotals? = snapshot()?.mealNutritionTotals?.get(date)
    fun nutritionGoal(): AthleteNutritionGoal? = snapshot()?.nutritionGoal
    fun todayWeight(): FitnessRepository.BodyMetricEntry? = snapshot()?.todayWeight
    fun todayBodyMetrics(): List<FitnessRepository.BodyMetricEntry> = snapshot()?.todayBodyMetrics ?: emptyList()
    fun todayMeals(): List<FitnessRepository.MealEntry> = snapshot()?.todayMeals ?: emptyList()

    override fun onCleared() {
        executor.shutdownNow()
    }
}
