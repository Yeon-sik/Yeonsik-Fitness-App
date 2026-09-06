package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface WorkoutExerciseDetailUiState {
    data object Idle : WorkoutExerciseDetailUiState
    data object Loading : WorkoutExerciseDetailUiState
    data class Ready(
        val ownerId: String,
        val detail: WorkoutExerciseDetail,
        val initializedInitialSet: Boolean
    ) : WorkoutExerciseDetailUiState
    data class Missing(val ownerId: String, val recordId: String) : WorkoutExerciseDetailUiState
    data class Error(val ownerId: String, val message: String) : WorkoutExerciseDetailUiState
}

/**
 * Owns only restoration identifiers. Persisted workout rows are always re-read
 * from SQLite through the feature API, never copied into SavedStateHandle.
 */
class WorkoutExerciseDetailViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepositoryApi,
    private val initializeWorkoutExercise: InitializeWorkoutExercise,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<WorkoutExerciseDetailUiState>(WorkoutExerciseDetailUiState.Idle)
    val uiState: LiveData<WorkoutExerciseDetailUiState> = mutableState
    private var requestVersion = 0L

    fun enter(scope: AccountScope, recordId: String, activeExerciseId: String?) {
        val restoredRecordId: String? = savedStateHandle[KEY_RECORD_ID]
        val restoredExerciseId: String? = savedStateHandle[KEY_EXERCISE_ID]
        if (restoredRecordId == recordId && restoredExerciseId == activeExerciseId
            && mutableState.value is WorkoutExerciseDetailUiState.Loading) return

        savedStateHandle[KEY_RECORD_ID] = recordId
        savedStateHandle[KEY_EXERCISE_ID] = activeExerciseId
        val request = ++requestVersion
        mutableState.value = WorkoutExerciseDetailUiState.Loading
        executor.execute {
            try {
                var detail = repository.loadExerciseDetail(scope, recordId, activeExerciseId)
                if (detail == null) {
                    publishIfCurrent(request, WorkoutExerciseDetailUiState.Missing(scope.ownerId, recordId))
                    return@execute
                }
                val initialized = if (detail.sets.isEmpty()) {
                    initializeWorkoutExercise.execute(scope, recordId, detail.activeExercise.id)
                } else false
                if (initialized) detail = repository.loadExerciseDetail(scope, recordId, detail.activeExercise.id)
                if (detail == null) {
                    publishIfCurrent(request, WorkoutExerciseDetailUiState.Missing(scope.ownerId, recordId))
                } else {
                    publishIfCurrent(
                        request,
                        WorkoutExerciseDetailUiState.Ready(scope.ownerId, detail, initialized)
                    )
                }
            } catch (error: Exception) {
                publishIfCurrent(
                    request,
                    WorkoutExerciseDetailUiState.Error(
                        scope.ownerId,
                        error.message ?: "운동 세트를 준비하지 못했습니다."
                    )
                )
            }
        }
    }

    private fun publishIfCurrent(request: Long, state: WorkoutExerciseDetailUiState) {
        if (request == requestVersion) mutableState.postValue(state)
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_RECORD_ID = "workout_detail.record_id"
        const val KEY_EXERCISE_ID = "workout_detail.exercise_id"
    }
}
