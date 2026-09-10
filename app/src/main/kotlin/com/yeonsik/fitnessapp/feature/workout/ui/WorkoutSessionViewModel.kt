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

sealed interface ManualPastWorkoutUiState {
    data object Idle : ManualPastWorkoutUiState
    data class Ready(val ownerId: String) : ManualPastWorkoutUiState
    data class Error(val ownerId: String, val message: String) : ManualPastWorkoutUiState
}

sealed interface WorkoutRestTimerState {
    data object Inactive : WorkoutRestTimerState
    data class Active(
        val ownerId: String,
        val endsAtMillis: Long,
        val totalSeconds: Int
    ) : WorkoutRestTimerState
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

enum class WorkoutSessionTerminalOutcome {
    MISSING,
    COMPLETED,
    DISCARDED_EMPTY,
    FAILURE
}

class WorkoutSessionTerminalEvent(
    val requestId: Long,
    val ownerId: String,
    val outcome: WorkoutSessionTerminalOutcome,
    val recordId: String?,
    val message: String?
) {
    private val consumed = AtomicBoolean(false)

    fun consume(): Boolean = consumed.compareAndSet(false, true)
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
    private val mutableTerminalEvents = MutableLiveData<WorkoutSessionTerminalEvent>()
    val terminalEvents: LiveData<WorkoutSessionTerminalEvent> = mutableTerminalEvents
    private val mutableManualPastState =
        MutableLiveData<ManualPastWorkoutUiState>(restoreManualPastState())
    val manualPastState: LiveData<ManualPastWorkoutUiState> = mutableManualPastState
    private val mutableRestTimerState = MutableLiveData<WorkoutRestTimerState>(restoreRestTimerState())
    val restTimerState: LiveData<WorkoutRestTimerState> = mutableRestTimerState
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
                if (session == null) {
                    publishIfCurrent(
                        request,
                        WorkoutSessionUiState.Missing(scope.ownerId, recordId)
                    )
                    publishTerminalIfCurrent(
                        request,
                        WorkoutSessionTerminalEvent(
                            request,
                            scope.ownerId,
                            WorkoutSessionTerminalOutcome.MISSING,
                            recordId,
                            "운동 기록을 찾지 못했습니다."
                        )
                    )
                } else {
                    publishIfCurrent(request, WorkoutSessionUiState.Ready(scope.ownerId, session))
                }
            } catch (error: Exception) {
                publishIfCurrent(
                    request,
                    WorkoutSessionUiState.Error(
                        scope.ownerId,
                        error.message ?: "운동 기록을 불러오지 못했습니다."
                    )
                )
                publishTerminalIfCurrent(
                    request,
                    WorkoutSessionTerminalEvent(
                        request,
                        scope.ownerId,
                        WorkoutSessionTerminalOutcome.FAILURE,
                        recordId,
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
                    publishTerminalIfCurrent(
                        request,
                        WorkoutSessionTerminalEvent(
                            request,
                            scope.ownerId,
                            WorkoutSessionTerminalOutcome.COMPLETED,
                            recordId,
                            null
                        )
                    )
                } else {
                    completeWorkout.discardEmptySession(scope, recordId)
                    publishIfCurrent(
                        request,
                        WorkoutSessionUiState.DiscardedEmptySession(scope.ownerId, recordId)
                    )
                    publishTerminalIfCurrent(
                        request,
                        WorkoutSessionTerminalEvent(
                            request,
                            scope.ownerId,
                            WorkoutSessionTerminalOutcome.DISCARDED_EMPTY,
                            recordId,
                            "수행한 세트가 없어 운동을 저장하지 않았습니다."
                        )
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
                publishTerminalIfCurrent(
                    request,
                    WorkoutSessionTerminalEvent(
                        request,
                        scope.ownerId,
                        WorkoutSessionTerminalOutcome.FAILURE,
                        recordId,
                        error.message ?: "운동을 완료하지 못했습니다."
                    )
                )
            }
        }
    }

    /** Keeps the record selected by a one-shot action available after recreation. */
    fun rememberActiveRecord(recordId: String) {
        savedStateHandle[KEY_RECORD_ID] = recordId
    }

    fun activeRecordId(): String? = savedStateHandle[KEY_RECORD_ID]

    fun clearActiveRecordIfMatches(recordId: String) {
        if (activeRecordId() == recordId) {
            savedStateHandle.remove<String>(KEY_RECORD_ID)
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

    fun openManualPastEditor(scope: AccountScope) {
        val request = ++actionVersion
        executor.execute {
            try {
                val service = requireSessionApplicationService()
                val activeRecordId = service.latestInProgress(scope)
                if (activeRecordId != null) {
                    val cardio = service.isCardioSession(scope, activeRecordId)
                    if (request == actionVersion) {
                        mutableActionState.postValue(
                            WorkoutSessionActionEvent(
                                request,
                                scope.ownerId,
                                WorkoutSessionAction.CONTINUE,
                                WorkoutSessionActionOutcome.OPEN_EXISTING,
                                activeRecordId,
                                cardio,
                                "진행 중인 운동을 먼저 이어갑니다."
                            )
                        )
                    }
                } else if (request == actionVersion) {
                    savedStateHandle[KEY_MANUAL_PAST_OWNER_ID] = scope.ownerId
                    mutableManualPastState.postValue(ManualPastWorkoutUiState.Ready(scope.ownerId))
                }
            } catch (error: Exception) {
                if (request == actionVersion) {
                    mutableManualPastState.postValue(
                        ManualPastWorkoutUiState.Error(
                            scope.ownerId,
                            error.message ?: "진행 중인 운동을 확인하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun dismissManualPastEditor() {
        savedStateHandle.remove<String>(KEY_MANUAL_PAST_OWNER_ID)
        mutableManualPastState.value = ManualPastWorkoutUiState.Idle
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

    fun startRestTimer(ownerId: String, restSeconds: Int?) {
        val seconds = restSeconds?.takeIf { it > 0 } ?: DEFAULT_REST_SECONDS
        val endsAtMillis = System.currentTimeMillis() + seconds * 1000L
        savedStateHandle[KEY_REST_OWNER_ID] = ownerId
        savedStateHandle[KEY_REST_ENDS_AT] = endsAtMillis
        savedStateHandle[KEY_REST_TOTAL_SECONDS] = seconds
        mutableRestTimerState.value = WorkoutRestTimerState.Active(
            ownerId,
            endsAtMillis,
            seconds
        )
    }

    fun stopRestTimer() {
        savedStateHandle.remove<String>(KEY_REST_OWNER_ID)
        savedStateHandle.remove<Long>(KEY_REST_ENDS_AT)
        savedStateHandle.remove<Int>(KEY_REST_TOTAL_SECONDS)
        mutableRestTimerState.value = WorkoutRestTimerState.Inactive
    }

    private fun restoreRestTimerState(): WorkoutRestTimerState {
        val ownerId: String? = savedStateHandle[KEY_REST_OWNER_ID]
        val endsAtMillis: Long? = savedStateHandle[KEY_REST_ENDS_AT]
        val totalSeconds: Int? = savedStateHandle[KEY_REST_TOTAL_SECONDS]
        return if (!ownerId.isNullOrBlank()
            && endsAtMillis != null
            && totalSeconds != null
            && endsAtMillis > System.currentTimeMillis()
            && totalSeconds > 0
        ) {
            WorkoutRestTimerState.Active(ownerId, endsAtMillis, totalSeconds)
        } else {
            WorkoutRestTimerState.Inactive
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

    private fun publishTerminalIfCurrent(request: Long, event: WorkoutSessionTerminalEvent) {
        if (request == requestVersion) mutableTerminalEvents.postValue(event)
    }

    private fun restoreManualPastState(): ManualPastWorkoutUiState {
        val ownerId: String? = savedStateHandle[KEY_MANUAL_PAST_OWNER_ID]
        return if (ownerId.isNullOrBlank()) {
            ManualPastWorkoutUiState.Idle
        } else {
            ManualPastWorkoutUiState.Ready(ownerId)
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_RECORD_ID = "workout_session.record_id"
        const val KEY_REST_OWNER_ID = "workout_rest.owner_id"
        const val KEY_REST_ENDS_AT = "workout_rest.ends_at"
        const val KEY_REST_TOTAL_SECONDS = "workout_rest.total_seconds"
        const val KEY_MANUAL_PAST_OWNER_ID = "workout_manual_past.owner_id"
        const val DEFAULT_REST_SECONDS = 90
    }
}
