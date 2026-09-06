package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface WorkoutSessionUiState {
    data object Idle : WorkoutSessionUiState
    data object Loading : WorkoutSessionUiState
    data class Ready(val ownerId: String, val session: WorkoutSessionSnapshot) : WorkoutSessionUiState
    data class Missing(val ownerId: String, val recordId: String) : WorkoutSessionUiState
    data class Completing(val ownerId: String, val recordId: String) : WorkoutSessionUiState
    data class Completed(val ownerId: String, val recordId: String) : WorkoutSessionUiState
    data class DiscardedEmptySession(val ownerId: String, val recordId: String) : WorkoutSessionUiState
    data class Error(val ownerId: String, val message: String) : WorkoutSessionUiState
}

/** Stores only the recoverable session id; completion state remains transient. */
class WorkoutSessionViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepositoryApi,
    private val completeWorkout: CompleteWorkout,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<WorkoutSessionUiState>(WorkoutSessionUiState.Idle)
    val uiState: LiveData<WorkoutSessionUiState> = mutableState
    private var requestVersion = 0L

    fun enter(scope: AccountScope, recordId: String?) {
        savedStateHandle[KEY_RECORD_ID] = recordId
        if (recordId == null) {
            mutableState.value = WorkoutSessionUiState.Idle
            return
        }
        val request = ++requestVersion
        mutableState.value = WorkoutSessionUiState.Loading
        executor.execute {
            try {
                val session = repository.loadSession(scope, recordId)
                publishIfCurrent(
                    request,
                    session?.let { WorkoutSessionUiState.Ready(scope.ownerId, it) }
                        ?: WorkoutSessionUiState.Missing(scope.ownerId, recordId)
                )
            } catch (error: Exception) {
                publishIfCurrent(
                    request,
                    WorkoutSessionUiState.Error(
                        scope.ownerId,
                        error.message ?: "운동 기록을 불러오지 못했습니다."
                    )
                )
            }
        }
    }

    fun finish(scope: AccountScope, recordId: String) {
        if (mutableState.value is WorkoutSessionUiState.Completing) return
        val request = ++requestVersion
        mutableState.value = WorkoutSessionUiState.Completing(scope.ownerId, recordId)
        executor.execute {
            try {
                val result = completeWorkout.execute(scope, recordId)
                if (result == WorkoutCompletion.COMPLETED) {
                    publishIfCurrent(request, WorkoutSessionUiState.Completed(scope.ownerId, recordId))
                } else {
                    completeWorkout.discardEmptySession(scope, recordId)
                    publishIfCurrent(
                        request,
                        WorkoutSessionUiState.DiscardedEmptySession(scope.ownerId, recordId)
                    )
                }
            } catch (error: Exception) {
                publishIfCurrent(
                    request,
                    WorkoutSessionUiState.Error(
                        scope.ownerId,
                        error.message ?: "운동을 완료하지 못했습니다."
                    )
                )
            }
        }
    }

    private fun publishIfCurrent(request: Long, state: WorkoutSessionUiState) {
        if (request == requestVersion) mutableState.postValue(state)
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_RECORD_ID = "workout_session.record_id"
    }
}
