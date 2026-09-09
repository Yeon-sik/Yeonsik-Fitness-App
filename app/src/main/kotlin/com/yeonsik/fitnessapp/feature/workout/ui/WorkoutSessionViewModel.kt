package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.application.WorkoutSessionApplicationService
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

enum class WorkoutSessionAction {
    OPEN_RECORD,
    CONTINUE,
    START_EMPTY,
    START_ROUTINE,
    START_MANUAL_PAST,
    DELETE
}

enum class WorkoutSessionActionOutcome {
    OPEN_EXISTING,
    CREATED,
    DELETED,
    NONE,
    FAILURE
}

/**
 * One-shot result for session actions initiated by the route or Activity chrome.
 * The owner and request id are retained so a late result cannot affect another account
 * or a newer action. Consumption is intentionally attached to the event instance because
 * LiveData replays its latest value after recreation.
 */
class WorkoutSessionActionEvent(
    val requestId: Long,
    val ownerId: String,
    val action: WorkoutSessionAction,
    val outcome: WorkoutSessionActionOutcome,
    val recordId: String?,
    val cardioSession: Boolean,
    val message: String?
) {
    private val consumed = AtomicBoolean(false)

    fun consume(): Boolean = consumed.compareAndSet(false, true)
}

/** Stores only the recoverable session id; completion state remains transient. */
class WorkoutSessionViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepositoryApi,
    private val completeWorkout: CompleteWorkout,
    private val sessionApplicationService: WorkoutSessionApplicationService? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<WorkoutSessionUiState>(WorkoutSessionUiState.Idle)
    val uiState: LiveData<WorkoutSessionUiState> = mutableState
    private val mutableActionState = MutableLiveData<WorkoutSessionActionEvent>()
    val actionState: LiveData<WorkoutSessionActionEvent> = mutableActionState
    private var requestVersion = 0L
    private var actionVersion = 0L

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

    fun openRecord(scope: AccountScope, recordId: String) {
        executeAction(scope, WorkoutSessionAction.OPEN_RECORD) {
            val cardio = requireSessionApplicationService().isCardioSession(scope, recordId)
            ActionResult(WorkoutSessionActionOutcome.OPEN_EXISTING, recordId, cardio, null)
        }
    }

    fun continueIfAvailable(scope: AccountScope) {
        executeAction(scope, WorkoutSessionAction.CONTINUE) {
            val service = requireSessionApplicationService()
            val recordId = service.latestInProgress(scope)
            if (recordId == null) {
                return@executeAction ActionResult(
                    WorkoutSessionActionOutcome.NONE,
                    null,
                    false,
                    "진행 중인 운동이 없습니다."
                )
            }
            val cardio = service.isCardioSession(scope, recordId)
            ActionResult(WorkoutSessionActionOutcome.OPEN_EXISTING, recordId, cardio, null)
        }
    }

    fun startEmpty(scope: AccountScope, date: String) {
        executeAction(scope, WorkoutSessionAction.START_EMPTY) {
            val service = requireSessionApplicationService()
            val activeRecordId = service.latestInProgress(scope)
            if (activeRecordId != null) {
                val cardio = service.isCardioSession(scope, activeRecordId)
                return@executeAction ActionResult(
                    WorkoutSessionActionOutcome.OPEN_EXISTING,
                    activeRecordId,
                    cardio,
                    "진행 중인 운동을 먼저 이어갑니다."
                )
            }
            val recordId = service.createEmptySession(scope, date)
            ActionResult(WorkoutSessionActionOutcome.CREATED, recordId, false, null)
        }
    }

    fun startRoutine(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        exercises: List<RoutineExerciseInstance>
    ) {
        executeAction(scope, WorkoutSessionAction.START_ROUTINE) {
            val service = requireSessionApplicationService()
            val activeRecordId = service.latestInProgress(scope)
            if (activeRecordId != null) {
                val cardio = service.isCardioSession(scope, activeRecordId)
                return@executeAction ActionResult(
                    WorkoutSessionActionOutcome.OPEN_EXISTING,
                    activeRecordId,
                    cardio,
                    "진행 중인 운동을 먼저 이어갑니다."
                )
            }
            val recordId = service.createSessionFromRoutine(
                scope, date, title, routineId, exercises
            )
            ActionResult(WorkoutSessionActionOutcome.CREATED, recordId, false, null)
        }
    }

    fun startManualPast(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        exercises: List<RoutineExerciseInstance>,
        startedAt: String,
        endedAt: String
    ) {
        executeAction(scope, WorkoutSessionAction.START_MANUAL_PAST) {
            val recordId = requireSessionApplicationService().createManualPastSessionFromRoutine(
                scope, date, title, routineId, exercises, startedAt, endedAt
            )
            ActionResult(WorkoutSessionActionOutcome.CREATED, recordId, false, null)
        }
    }

    fun delete(scope: AccountScope, recordId: String) {
        executeAction(scope, WorkoutSessionAction.DELETE) {
            val result = requireSessionApplicationService().delete(scope, recordId)
            ActionResult(
                WorkoutSessionActionOutcome.DELETED,
                recordId,
                result.cardioSession,
                null
            )
        }
    }

    private fun executeAction(
        scope: AccountScope,
        action: WorkoutSessionAction,
        operation: () -> ActionResult
    ) {
        val request = ++actionVersion
        executor.execute {
            try {
                val result = operation()
                if (request != actionVersion) return@execute
                mutableActionState.postValue(
                    WorkoutSessionActionEvent(
                        request,
                        scope.ownerId,
                        action,
                        result.outcome,
                        result.recordId,
                        result.cardioSession,
                        result.message
                    )
                )
            } catch (error: Exception) {
                if (request != actionVersion) return@execute
                mutableActionState.postValue(
                    WorkoutSessionActionEvent(
                        request,
                        scope.ownerId,
                        action,
                        WorkoutSessionActionOutcome.FAILURE,
                        null,
                        false,
                        error.message ?: "운동 작업을 완료하지 못했습니다."
                    )
                )
            }
        }
    }

    private fun requireSessionApplicationService(): WorkoutSessionApplicationService =
        sessionApplicationService
            ?: error("운동 세션 작업 Service가 연결되지 않았습니다.")

    private data class ActionResult(
        val outcome: WorkoutSessionActionOutcome,
        val recordId: String?,
        val cardioSession: Boolean,
        val message: String?
    )

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
