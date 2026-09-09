package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.application.CardioSessionApplicationService
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CardioSessionUiState {
    data object Idle : CardioSessionUiState
    data object Loading : CardioSessionUiState
    data class Ready(val ownerId: String, val session: CardioSessionSnapshot) : CardioSessionUiState
    data class Missing(val ownerId: String, val recordId: String) : CardioSessionUiState
    data class Error(val ownerId: String, val message: String) : CardioSessionUiState
}

sealed interface CardioRouteUiState {
    data object Idle : CardioRouteUiState
    data class Loading(val ownerId: String, val recordId: String) : CardioRouteUiState
    data class Ready(
        val ownerId: String,
        val recordId: String,
        val projection: CardioRouteProjection
    ) : CardioRouteUiState
    data class Error(val ownerId: String, val recordId: String, val message: String) : CardioRouteUiState
}

enum class CardioSessionAction {
    OPEN,
    START,
    PREPARE_RESUME,
    RESUME,
    PAUSE,
    PREPARE_FINISH,
    FINISH,
    PREPARE_HEART_RATE_EDIT,
    UPDATE_HEART_RATE,
    PREPARE_CANCEL,
    CANCEL,
    LOAD_ROUTE
}

enum class CardioSessionActionOutcome {
    OPENED,
    STARTED,
    RESUME_READY,
    RESUMED,
    PAUSED,
    FINISH_READY,
    COMPLETED,
    HEART_RATE_READY,
    UPDATED,
    CANCEL_READY,
    CANCELLED,
    ROUTE_LOADED,
    NOT_FOUND,
    FAILURE
}

/** One-shot owner-scoped result for cardio lifecycle work. */
class CardioSessionActionEvent(
    val requestId: Long,
    val ownerId: String,
    val action: CardioSessionAction,
    val outcome: CardioSessionActionOutcome,
    val recordId: String?,
    val session: CardioSessionSnapshot?,
    val route: CardioRouteProjection?,
    val pausedByFinish: Boolean,
    val message: String?
) {
    private val consumed = AtomicBoolean(false)

    fun consume(): Boolean = consumed.compareAndSet(false, true)
}

/** Persists only the active cardio record id; GPS metrics are always re-read. */
class CardioSessionViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CardioRepositoryApi,
    private val sessionApplicationService: CardioSessionApplicationService? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<CardioSessionUiState>(CardioSessionUiState.Idle)
    val uiState: LiveData<CardioSessionUiState> = mutableState
    private val mutableActionState = MutableLiveData<CardioSessionActionEvent>()
    val actionState: LiveData<CardioSessionActionEvent> = mutableActionState
    private val mutableRouteState = MutableLiveData<CardioRouteUiState>(CardioRouteUiState.Idle)
    val routeState: LiveData<CardioRouteUiState> = mutableRouteState
    @Volatile private var refreshPending = false
    private var requestVersion = 0L
    private var actionVersion = 0L
    private var routeRequestVersion = 0L

    fun enter(scope: AccountScope, recordId: String?) {
        savedStateHandle[KEY_RECORD_ID] = recordId
        if (recordId == null) {
            mutableState.value = CardioSessionUiState.Idle
            return
        }
        val request = ++requestVersion
        mutableState.value = CardioSessionUiState.Loading
        load(scope, recordId, request)
    }

    fun refresh(scope: AccountScope, recordId: String) {
        if (refreshPending) return
        refreshPending = true
        load(scope, recordId, requestVersion)
    }

    fun open(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.OPEN) {
            val session = requireService().load(scope, recordId)
            if (session == null) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "GPS 유산소 상태를 찾지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.OPENED, recordId, session, null, false, null)
            }
        }
    }

    fun start(scope: AccountScope, activityType: CardioActivityType, date: String) {
        executeAction(scope, CardioSessionAction.START) {
            val session = requireService().start(scope, activityType, date)
            if (session == null) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, null, null, null, false,
                    "유산소 기록을 시작하지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.STARTED, session.recordId, session, null, false, null)
            }
        }
    }

    fun prepareResume(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.PREPARE_RESUME) {
            val session = requireService().load(scope, recordId)
            if (session == null || session.status != CardioSessionSnapshot.STATUS_PAUSED) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "재개할 유산소 기록을 찾지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.RESUME_READY, recordId, session, null, false, null)
            }
        }
    }

    fun resume(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.RESUME) {
            if (!requireService().resume(scope, recordId)) {
                return@executeAction ActionResult(
                    CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "재개할 유산소 기록을 찾지 못했습니다."
                )
            }
            ActionResult(
                CardioSessionActionOutcome.RESUMED,
                recordId,
                requireService().load(scope, recordId),
                null,
                false,
                null
            )
        }
    }

    fun pause(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.PAUSE) {
            if (!requireService().pause(scope, recordId)) {
                return@executeAction ActionResult(
                    CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "일시정지할 유산소 기록을 찾지 못했습니다."
                )
            }
            ActionResult(
                CardioSessionActionOutcome.PAUSED,
                recordId,
                requireService().load(scope, recordId),
                null,
                false,
                null
            )
        }
    }

    fun prepareFinish(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.PREPARE_FINISH) {
            val service = requireService()
            var session = service.load(scope, recordId)
            if (session == null) {
                return@executeAction ActionResult(
                    CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "완료할 유산소 기록을 찾지 못했습니다."
                )
            }
            if (session.status == CardioSessionSnapshot.STATUS_COMPLETED) {
                return@executeAction ActionResult(
                    CardioSessionActionOutcome.COMPLETED, recordId, session, null, false, null
                )
            }
            val pausedByFinish = session.status == CardioSessionSnapshot.STATUS_TRACKING
            if (pausedByFinish && !service.pause(scope, recordId)) {
                return@executeAction ActionResult(
                    CardioSessionActionOutcome.FAILURE, recordId, null, null, false,
                    "유산소 기록을 완료할 수 없습니다."
                )
            }
            if (pausedByFinish) session = service.load(scope, recordId)
            ActionResult(
                CardioSessionActionOutcome.FINISH_READY,
                recordId,
                session,
                null,
                pausedByFinish,
                null
            )
        }
    }

    fun finish(scope: AccountScope, recordId: String, averageHeartRateBpm: Int?) {
        executeAction(scope, CardioSessionAction.FINISH) {
            val session = requireService().finish(scope, recordId, averageHeartRateBpm)
            if (session == null) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "평균 심박수를 저장하지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.COMPLETED, recordId, session, null, false, null)
            }
        }
    }

    fun prepareAverageHeartRateEdit(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.PREPARE_HEART_RATE_EDIT) {
            val session = requireService().load(scope, recordId)
            if (session == null || session.status != CardioSessionSnapshot.STATUS_COMPLETED) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "수정할 유산소 기록을 찾지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.HEART_RATE_READY, recordId, session, null, false, null)
            }
        }
    }

    fun updateAverageHeartRate(scope: AccountScope, recordId: String, averageHeartRateBpm: Int?) {
        executeAction(scope, CardioSessionAction.UPDATE_HEART_RATE) {
            val session = requireService().updateAverageHeartRate(scope, recordId, averageHeartRateBpm)
            if (session == null) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "평균 심박수를 저장하지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.UPDATED, recordId, session, null, false, null)
            }
        }
    }

    fun cancel(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.CANCEL) {
            requireService().cancel(scope, recordId)
            ActionResult(CardioSessionActionOutcome.CANCELLED, recordId, null, null, false, null)
        }
    }

    fun prepareCancel(scope: AccountScope, recordId: String) {
        executeAction(scope, CardioSessionAction.PREPARE_CANCEL) {
            val session = requireService().load(scope, recordId)
            if (session == null) {
                ActionResult(CardioSessionActionOutcome.NOT_FOUND, recordId, null, null, false,
                    "취소할 유산소 기록을 찾지 못했습니다.")
            } else {
                ActionResult(CardioSessionActionOutcome.CANCEL_READY, recordId, session, null, false, null)
            }
        }
    }

    fun loadRoute(scope: AccountScope, recordId: String) {
        val request = ++routeRequestVersion
        mutableRouteState.value = CardioRouteUiState.Loading(scope.ownerId, recordId)
        executor.execute {
            try {
                val projection = requireService().route(scope, recordId)
                if (request == routeRequestVersion) {
                    mutableRouteState.postValue(
                        CardioRouteUiState.Ready(scope.ownerId, recordId, projection)
                    )
                }
            } catch (error: Exception) {
                if (request == routeRequestVersion) {
                    mutableRouteState.postValue(
                        CardioRouteUiState.Error(
                            scope.ownerId,
                            recordId,
                            error.message ?: "경로를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    private fun executeAction(
        scope: AccountScope,
        action: CardioSessionAction,
        operation: () -> ActionResult
    ) {
        val request = ++actionVersion
        executor.execute {
            try {
                val result = operation()
                if (request != actionVersion) return@execute
                mutableActionState.postValue(
                    CardioSessionActionEvent(
                        request,
                        scope.ownerId,
                        action,
                        result.outcome,
                        result.recordId,
                        result.session,
                        result.route,
                        result.pausedByFinish,
                        result.message
                    )
                )
            } catch (error: Exception) {
                if (request != actionVersion) return@execute
                mutableActionState.postValue(
                    CardioSessionActionEvent(
                        request,
                        scope.ownerId,
                        action,
                        CardioSessionActionOutcome.FAILURE,
                        null,
                        null,
                        null,
                        false,
                        error.message ?: "유산소 작업을 완료하지 못했습니다."
                    )
                )
            }
        }
    }

    private fun requireService(): CardioSessionApplicationService =
        sessionApplicationService ?: error("유산소 세션 작업 Service가 연결되지 않았습니다.")

    private data class ActionResult(
        val outcome: CardioSessionActionOutcome,
        val recordId: String?,
        val session: CardioSessionSnapshot?,
        val route: CardioRouteProjection?,
        val pausedByFinish: Boolean,
        val message: String?
    )

    private fun load(scope: AccountScope, recordId: String, request: Long) {
        executor.execute {
            try {
                val state = repository.loadSession(scope, recordId)?.let {
                    CardioSessionUiState.Ready(scope.ownerId, it)
                } ?: CardioSessionUiState.Missing(scope.ownerId, recordId)
                if (request == requestVersion) mutableState.postValue(state)
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(
                    CardioSessionUiState.Error(
                        scope.ownerId,
                        error.message ?: "유산소 기록을 불러오지 못했습니다."
                    )
                )
            } finally {
                refreshPending = false
            }
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_RECORD_ID = "cardio_session.record_id"
    }
}
