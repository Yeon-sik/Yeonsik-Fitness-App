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
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

sealed interface WorkoutExerciseDetailUiState {
    data object Idle : WorkoutExerciseDetailUiState
    data object Loading : WorkoutExerciseDetailUiState
    data class Ready(
        val ownerId: String,
        val detail: WorkoutExerciseDetail,
        val initializedInitialSet: Boolean,
        val readOnly: Boolean = false
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val shutdownExecutorOnCleared: Boolean = true
) : ViewModel() {
    private val mutableState = MutableLiveData<WorkoutExerciseDetailUiState>(WorkoutExerciseDetailUiState.Idle)
    val uiState: LiveData<WorkoutExerciseDetailUiState> = mutableState
    private val mainHandler = Handler(Looper.getMainLooper())
    private var requestVersion = 0L

    fun enter(scope: AccountScope, recordId: String, activeExerciseId: String?, readOnly: Boolean = false) {
        val restoredRecordId: String? = savedStateHandle[KEY_RECORD_ID]
        val restoredExerciseId: String? = savedStateHandle[KEY_EXERCISE_ID]
        val restoredReadOnly: Boolean = savedStateHandle[KEY_READ_ONLY] ?: false
        if (restoredRecordId == recordId && restoredExerciseId == activeExerciseId
            && restoredReadOnly == readOnly
            && mutableState.value is WorkoutExerciseDetailUiState.Loading) return

        savedStateHandle[KEY_RECORD_ID] = recordId
        savedStateHandle[KEY_EXERCISE_ID] = activeExerciseId
        savedStateHandle[KEY_READ_ONLY] = readOnly
        val request = ++requestVersion
        mutableState.value = WorkoutExerciseDetailUiState.Loading
        executor.execute {
            try {
                var detail = repository.loadExerciseDetail(scope, recordId, activeExerciseId)
                if (detail == null) {
                    publishIfCurrent(request, WorkoutExerciseDetailUiState.Missing(scope.ownerId, recordId))
                    return@execute
                }
                val initialized = if (!readOnly && detail.sets.isEmpty()) {
                    initializeWorkoutExercise.execute(scope, recordId, detail.activeExercise.id)
                } else false
                if (initialized) detail = repository.loadExerciseDetail(scope, recordId, detail.activeExercise.id)
                if (detail == null) {
                    publishIfCurrent(request, WorkoutExerciseDetailUiState.Missing(scope.ownerId, recordId))
                } else {
                    savedStateHandle[KEY_EXERCISE_ID] = detail.activeExercise.id
                    publishIfCurrent(
                        request,
                        WorkoutExerciseDetailUiState.Ready(scope.ownerId, detail, initialized, readOnly)
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

    /** Current detail target used by the app navigation root after picker writes. */
    fun activeExerciseId(): String? = savedStateHandle[KEY_EXERCISE_ID]

    fun rememberActiveExercise(exerciseId: String?) {
        savedStateHandle[KEY_EXERCISE_ID] = exerciseId
    }

    fun clearActiveExercise() {
        savedStateHandle.remove<String>(KEY_EXERCISE_ID)
    }

    fun updateTypedSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput,
                       callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.updateTypedSet(scope, recordId, setId, input)
    }

    fun addTypedSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int,
                       input: WorkoutSetInput, callback: Consumer<Boolean>) = executeWrite(callback) {
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

    fun addExercise(scope: AccountScope, recordId: String,
                    exercise: WorkoutExerciseReplacement,
                    callback: Consumer<Boolean>) = executeWrite(callback) {
        repository.addExercise(scope, recordId, exercise)
    }

    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String,
                        replacement: WorkoutExerciseReplacement, callback: Consumer<Boolean>) = executeWrite(callback) {
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
        if (shutdownExecutorOnCleared) executor.shutdownNow()
    }

    private companion object {
        const val KEY_RECORD_ID = "workout_detail.record_id"
        const val KEY_EXERCISE_ID = "workout_detail.exercise_id"
        const val KEY_READ_ONLY = "workout_detail.read_only"
    }
}
