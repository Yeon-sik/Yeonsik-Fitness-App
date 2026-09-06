package com.yeonsik.fitnessapp.feature.workout.ui

import android.os.Handler
import android.os.Looper
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
import java.util.function.Consumer
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.exercise.RoutineExercise

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
    private val mainHandler = Handler(Looper.getMainLooper())
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

    fun updateTypedSet(scope: AccountScope, recordId: String, setId: String, input: FitnessRepository.SetInput,
                       callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.updateTypedSet(scope, recordId, setId, input)
    }

    fun addTypedSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int,
                    input: FitnessRepository.SetInput, callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.addTypedSet(scope, recordId, exerciseId, setIndex, input)
    }

    fun deleteSet(scope: AccountScope, recordId: String, setId: String,
                  callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.deleteSet(scope, recordId, setId)
    }

    fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String,
                       callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.deleteExercise(scope, recordId, exerciseId)
    }

    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String,
                        replacement: RoutineExercise, callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.replaceExercise(scope, recordId, exerciseId, replacement)
    }

    private fun executeWrite(callback: Consumer<Boolean>, work: () -> Boolean) {
        executor.execute {
            try {
                val result = work()
                mainHandler.post { callback.accept(result) }
            } catch (_: Exception) {
                mainHandler.post { callback.accept(false) }
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
