package com.yeonsik.fitnessapp.feature.development.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import com.yeonsik.fitnessapp.feature.development.application.DevelopmentApplicationService
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReportApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentTrackingRepositoryApi
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentTrainingOverview
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentGuidance
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface DevelopmentUiState {
    data object Idle : DevelopmentUiState
    data object Loading : DevelopmentUiState
    data class Ready(
        val ownerId: String,
        val report: DevelopmentReport,
        val training: DevelopmentTrainingOverview? = null,
        val followedKeys: List<String> = emptyList(),
        val trainingError: String? = null,
        val trackingMessage: String? = null,
        val guidance: DevelopmentGuidance? = null,
        val guidanceError: String? = null
    ) : DevelopmentUiState
    data class Error(val ownerId: String, val message: String) : DevelopmentUiState
}

sealed interface PaperAdviceUiState {
    data object Idle : PaperAdviceUiState
    data class Loading(val ownerId: String) : PaperAdviceUiState
    data class Ready(val ownerId: String, val assessment: PaperAdviceAssessment) : PaperAdviceUiState
    data class Error(val ownerId: String, val message: String) : PaperAdviceUiState
}

sealed interface DevelopmentProfileEditorUiState {
    data object Idle : DevelopmentProfileEditorUiState
    data class Loading(val requestId: Long, val ownerId: String) : DevelopmentProfileEditorUiState
    data class Ready(
        val requestId: Long,
        val ownerId: String,
        val date: String,
        val editor: DevelopmentApplicationService.ProfileEditor
    ) : DevelopmentProfileEditorUiState

    data class Saving(val requestId: Long, val ownerId: String) : DevelopmentProfileEditorUiState
    data class Saved(val requestId: Long, val ownerId: String) : DevelopmentProfileEditorUiState
    data class Error(val requestId: Long, val ownerId: String, val message: String) : DevelopmentProfileEditorUiState
}

sealed interface DevelopmentGoalEditorUiState {
    data object Idle : DevelopmentGoalEditorUiState
    data class Loading(val requestId: Long, val ownerId: String) : DevelopmentGoalEditorUiState
    data class Ready(
        val requestId: Long,
        val ownerId: String,
        val goal: DevelopmentGoal
    ) : DevelopmentGoalEditorUiState

    data class Saving(val requestId: Long, val ownerId: String) : DevelopmentGoalEditorUiState
    data class Saved(val requestId: Long, val ownerId: String) : DevelopmentGoalEditorUiState
    data class Error(val requestId: Long, val ownerId: String, val message: String) : DevelopmentGoalEditorUiState
}

enum class RecoveryEditorKind {
    NUTRITION_GOAL,
    CHECK_IN
}

sealed interface RecoveryEditorUiState {
    data object Idle : RecoveryEditorUiState
    data class Loading(val requestId: Long, val ownerId: String, val date: String) : RecoveryEditorUiState
    data class Ready(
        val requestId: Long,
        val ownerId: String,
        val date: String,
        val nutritionGoal: AthleteNutritionGoal?,
        val checkIn: AthleteDailyCheckIn,
        val editor: RecoveryEditorKind? = null
    ) : RecoveryEditorUiState

    data class Saving(
        val requestId: Long,
        val ownerId: String,
        val date: String,
        val editor: RecoveryEditorKind
    ) : RecoveryEditorUiState

    data class Saved(
        val requestId: Long,
        val ownerId: String,
        val date: String,
        val editor: RecoveryEditorKind
    ) : RecoveryEditorUiState

    data class Error(
        val requestId: Long,
        val ownerId: String,
        val date: String,
        val message: String
    ) : RecoveryEditorUiState
}

class DevelopmentViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DevelopmentReportApi,
    private val applicationService: DevelopmentApplicationService,
    private val recoveryRepository: RecoveryRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val trackingRepository: DevelopmentTrackingRepositoryApi? = null
) : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var trackingRequestVersion = 0L
    private val mutableState = MutableLiveData<DevelopmentUiState>(DevelopmentUiState.Idle)
    val uiState: LiveData<DevelopmentUiState> = mutableState
    private val mutablePaperAdviceState = MutableLiveData<PaperAdviceUiState>(PaperAdviceUiState.Idle)
    val paperAdviceState: LiveData<PaperAdviceUiState> = mutablePaperAdviceState
    private val mutableProfileEditorState =
        MutableLiveData<DevelopmentProfileEditorUiState>(DevelopmentProfileEditorUiState.Idle)
    val profileEditorState: LiveData<DevelopmentProfileEditorUiState> = mutableProfileEditorState
    private val mutableGoalEditorState =
        MutableLiveData<DevelopmentGoalEditorUiState>(DevelopmentGoalEditorUiState.Idle)
    val goalEditorState: LiveData<DevelopmentGoalEditorUiState> = mutableGoalEditorState
    private val mutableRecoveryEditorState =
        MutableLiveData<RecoveryEditorUiState>(RecoveryEditorUiState.Idle)
    val recoveryEditorState: LiveData<RecoveryEditorUiState> = mutableRecoveryEditorState
    private var reportRequestVersion = 0L
    private var reportIdentity: Pair<String, String>? = null
    private var stale = false

    fun enterIfNeeded(scope: AccountScope, date: String) {
        val identity = scope.ownerId to date
        val report = mutableState.value
        val reportAvailable = report is DevelopmentUiState.Ready &&
            report.ownerId == scope.ownerId || report is DevelopmentUiState.Loading
        if (!stale && reportIdentity == identity && reportAvailable) return
        enter(scope, date)
    }

    fun enterRecoveryIfNeeded(scope: AccountScope, date: String) {
        val current = mutableRecoveryEditorState.value
        if (current is RecoveryEditorUiState.Ready &&
            current.ownerId == scope.ownerId && current.date == date ||
            current is RecoveryEditorUiState.Loading &&
            current.ownerId == scope.ownerId && current.date == date) return
        enterRecovery(scope, date)
    }

    fun markStale() { stale = true }
    private var editorRequestVersion = 0L
    private var recoveryRequestVersion = 0L
    private var pendingRecoveryEditor: Pair<String, RecoveryEditorKind>? = null

    fun enter(scope: AccountScope, date: String) {
        reportIdentity = scope.ownerId to date
        stale = false
        savedStateHandle[KEY_DATE] = date
        val request = ++reportRequestVersion
        mutableState.value = DevelopmentUiState.Loading
        mutablePaperAdviceState.value = PaperAdviceUiState.Idle
        executor.execute {
            try {
                val referenceDate = LocalDate.parse(date)
                val report = repository.buildReport(scope, referenceDate)
                val training = runCatching { repository.buildTrainingOverview(scope, referenceDate) }
                val guidance = runCatching {
                    repository.buildGuidance(scope, referenceDate,
                        training.getOrNull() ?: DevelopmentTrainingOverview.empty(referenceDate))
                }
                val preferences = runCatching { trackingRepository?.followedExerciseKeys(scope) }
                val exercises = training.getOrNull()?.exercises.orEmpty()
                val available = exercises.map { it.key }.toSet()
                val stored = preferences.getOrNull()
                val selected = stored?.filter { it in available }
                val followed = if (stored == null || stored.isNotEmpty() && selected.isNullOrEmpty())
                    exercises.take(3).map { it.key } else selected.orEmpty()
                mainHandler.post {
                    if (request == reportRequestVersion && reportIdentity == (scope.ownerId to date)) {
                        mutableState.value = DevelopmentUiState.Ready(scope.ownerId, report,
                            training.getOrNull(), followed,
                            training.exceptionOrNull()?.message ?: if (training.isFailure)
                                "운동 변화를 불러오지 못했습니다." else null,
                            if (preferences.isFailure) "관심 운동 설정을 불러오지 못해 최근 운동을 표시합니다." else null,
                            guidance.getOrNull(), if (guidance.isFailure) "조정 제안을 불러오지 못했어요." else null)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (request == reportRequestVersion && reportIdentity == (scope.ownerId to date))
                        mutableState.value = DevelopmentUiState.Error(scope.ownerId,
                            error.message ?: "발전 리포트를 불러오지 못했습니다.")
                }
            }
        }
    }

    fun requestPaperAdvice(scope: AccountScope, date: String) {
        if (reportIdentity != (scope.ownerId to date)) return
        val current = mutablePaperAdviceState.value
        if (current is PaperAdviceUiState.Ready && current.ownerId == scope.ownerId ||
            current is PaperAdviceUiState.Loading && current.ownerId == scope.ownerId) return
        mutablePaperAdviceState.value = PaperAdviceUiState.Loading(scope.ownerId)
        loadPaperAdvice(scope, date, reportRequestVersion)
    }

    fun followExercises(scope: AccountScope, keys: List<String>) {
        val state = mutableState.value as? DevelopmentUiState.Ready ?: return
        if (state.ownerId != scope.ownerId || keys.size > 5 || keys.distinct().size != keys.size) return
        val available = state.training?.exercises.orEmpty().map { it.key }.toSet()
        if (keys.any { it !in available }) return
        val request = ++trackingRequestVersion
        val reportRequest = reportRequestVersion
        mutableState.value = state.copy(followedKeys = keys.toList(), trackingMessage = null)
        executor.execute {
            val result = runCatching { trackingRepository?.saveFollowedExerciseKeys(scope, keys) }
            mainHandler.post {
                val current = mutableState.value as? DevelopmentUiState.Ready
                if (request == trackingRequestVersion && reportRequest == reportRequestVersion &&
                    current != null && current.ownerId == scope.ownerId && result.isFailure) {
                    mutableState.value = current.copy(followedKeys = state.followedKeys,
                        trackingMessage = result.exceptionOrNull()?.message ?: "관심 운동을 저장하지 못했습니다.")
                }
            }
        }
    }

    fun openProfileEditor(scope: AccountScope, date: String) {
        val requestId = nextEditorRequest()
        savedStateHandle[KEY_PROFILE_DATE] = date
        mutableProfileEditorState.value =
            DevelopmentProfileEditorUiState.Loading(requestId, scope.ownerId)
        executor.execute {
            try {
                val editor = applicationService.loadProfileEditor(scope, date)
                if (requestId == editorRequestVersion) {
                    mutableProfileEditorState.postValue(
                        DevelopmentProfileEditorUiState.Ready(
                            requestId,
                            scope.ownerId,
                            date,
                            editor
                        )
                    )
                }
            } catch (error: Exception) {
                if (requestId == editorRequestVersion) {
                    mutableProfileEditorState.postValue(
                        DevelopmentProfileEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "바디 정보를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun saveProfileAndWeight(
        scope: AccountScope,
        profile: BodyProfile?,
        weightRecordId: String?,
        weightDate: String,
        weightKg: Double?,
        weightMemo: String
    ) {
        val requestId = nextEditorRequest()
        savedStateHandle[KEY_PROFILE_DATE] = weightDate
        mutableProfileEditorState.value =
            DevelopmentProfileEditorUiState.Saving(requestId, scope.ownerId)
        executor.execute {
            try {
                applicationService.saveProfileAndWeight(
                    scope,
                    profile,
                    weightRecordId,
                    weightDate,
                    weightKg,
                    weightMemo
                )
                if (requestId == editorRequestVersion) {
                    mutableProfileEditorState.postValue(
                        DevelopmentProfileEditorUiState.Saved(requestId, scope.ownerId)
                    )
                }
            } catch (error: Exception) {
                if (requestId == editorRequestVersion) {
                    mutableProfileEditorState.postValue(
                        DevelopmentProfileEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "바디 정보를 저장하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun openGoalEditor(scope: AccountScope) {
        val requestId = nextEditorRequest()
        savedStateHandle[KEY_GOAL_OWNER_ID] = scope.ownerId
        mutableGoalEditorState.value =
            DevelopmentGoalEditorUiState.Loading(requestId, scope.ownerId)
        executor.execute {
            try {
                val goal = applicationService.loadGoal(scope)
                if (requestId == editorRequestVersion) {
                    mutableGoalEditorState.postValue(
                        DevelopmentGoalEditorUiState.Ready(requestId, scope.ownerId, goal)
                    )
                }
            } catch (error: Exception) {
                if (requestId == editorRequestVersion) {
                    mutableGoalEditorState.postValue(
                        DevelopmentGoalEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "발전 목표를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun saveGoal(scope: AccountScope, goal: DevelopmentGoal) {
        val requestId = nextEditorRequest()
        savedStateHandle[KEY_GOAL_OWNER_ID] = scope.ownerId
        mutableGoalEditorState.value =
            DevelopmentGoalEditorUiState.Saving(requestId, scope.ownerId)
        executor.execute {
            try {
                applicationService.saveGoal(scope, goal)
                if (requestId == editorRequestVersion) {
                    mutableGoalEditorState.postValue(
                        DevelopmentGoalEditorUiState.Saved(requestId, scope.ownerId)
                    )
                }
            } catch (error: Exception) {
                if (requestId == editorRequestVersion) {
                    mutableGoalEditorState.postValue(
                        DevelopmentGoalEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "발전 목표를 저장하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun enterRecovery(scope: AccountScope, date: String) {
        val requestId = ++recoveryRequestVersion
        if (pendingRecoveryEditor?.first != scope.ownerId) pendingRecoveryEditor = null
        mutableRecoveryEditorState.value =
            RecoveryEditorUiState.Loading(requestId, scope.ownerId, date)
        executor.execute {
            try {
                val goal = recoveryRepository.nutritionGoal(scope)
                val checkIn = recoveryRepository.checkIn(scope, date)
                mainHandler.post {
                    if (requestId != recoveryRequestVersion) return@post
                    val editor = pendingRecoveryEditor?.takeIf { it.first == scope.ownerId }?.second
                    pendingRecoveryEditor = null
                    mutableRecoveryEditorState.value =
                        RecoveryEditorUiState.Ready(
                            requestId,
                            scope.ownerId,
                            checkIn.date,
                            goal,
                            checkIn,
                            editor
                        )
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (requestId != recoveryRequestVersion) return@post
                    pendingRecoveryEditor = null
                    mutableRecoveryEditorState.value =
                        RecoveryEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            date,
                            error.message ?: "영양·회복 정보를 불러오지 못했습니다."
                        )
                }
            }
        }
    }

    fun openNutritionGoalEditor(scope: AccountScope) {
        openRecoveryEditor(scope, RecoveryEditorKind.NUTRITION_GOAL)
    }

    fun openCheckInEditor(scope: AccountScope) {
        openRecoveryEditor(scope, RecoveryEditorKind.CHECK_IN)
    }

    private fun openRecoveryEditor(scope: AccountScope, kind: RecoveryEditorKind) {
        val state = mutableRecoveryEditorState.value
        if (state is RecoveryEditorUiState.Ready && state.ownerId == scope.ownerId) {
            mutableRecoveryEditorState.value = state.copy(editor = kind)
        } else {
            pendingRecoveryEditor = scope.ownerId to kind
            if (state !is RecoveryEditorUiState.Loading || state.ownerId != scope.ownerId) {
                enterRecovery(scope, reportIdentity?.takeIf { it.first == scope.ownerId }?.second
                    ?: LocalDate.now().toString())
            }
        }
    }

    fun saveNutritionGoal(scope: AccountScope, goal: AthleteNutritionGoal) {
        val state = mutableRecoveryEditorState.value as? RecoveryEditorUiState.Ready ?: return
        if (state.ownerId != scope.ownerId || state.editor != RecoveryEditorKind.NUTRITION_GOAL) return
        val requestId = ++recoveryRequestVersion
        mutableRecoveryEditorState.value = RecoveryEditorUiState.Saving(
            requestId, scope.ownerId, state.date, RecoveryEditorKind.NUTRITION_GOAL
        )
        executor.execute {
            try {
                recoveryRepository.saveNutritionGoal(scope, goal)
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Saved(
                            requestId, scope.ownerId, state.date, RecoveryEditorKind.NUTRITION_GOAL
                        )
                    )
                }
            } catch (error: Exception) {
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            state.date,
                            error.message ?: "영양 목표를 저장하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun saveCheckIn(scope: AccountScope, checkIn: AthleteDailyCheckIn) {
        val state = mutableRecoveryEditorState.value as? RecoveryEditorUiState.Ready ?: return
        if (state.ownerId != scope.ownerId || state.editor != RecoveryEditorKind.CHECK_IN) return
        val requestId = ++recoveryRequestVersion
        mutableRecoveryEditorState.value = RecoveryEditorUiState.Saving(
            requestId, scope.ownerId, state.date, RecoveryEditorKind.CHECK_IN
        )
        executor.execute {
            try {
                recoveryRepository.saveCheckIn(scope, checkIn)
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Saved(
                            requestId, scope.ownerId, state.date, RecoveryEditorKind.CHECK_IN
                        )
                    )
                }
            } catch (error: Exception) {
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            state.date,
                            error.message ?: "회복 체크인을 저장하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun dismissProfileEditor() {
        mutableProfileEditorState.value = DevelopmentProfileEditorUiState.Idle
    }

    fun dismissGoalEditor() {
        mutableGoalEditorState.value = DevelopmentGoalEditorUiState.Idle
    }

    fun dismissRecoveryEditor() {
        pendingRecoveryEditor = null
        val state = mutableRecoveryEditorState.value
        mutableRecoveryEditorState.value = when (state) {
            is RecoveryEditorUiState.Ready -> state.copy(editor = null)
            else -> RecoveryEditorUiState.Idle
        }
    }

    private fun loadPaperAdvice(scope: AccountScope, date: String, request: Long) {
        executor.execute {
            try {
                val referenceDate = LocalDate.parse(date)
                val assessment = repository.buildPaperAdviceAssessment(scope, referenceDate)
                require(assessment.input.referenceDate == referenceDate) {
                    "PaperAdvice 기준일이 요청과 다릅니다."
                }
                mainHandler.post {
                    if (request == reportRequestVersion && reportIdentity == (scope.ownerId to date)) {
                        mutablePaperAdviceState.value = PaperAdviceUiState.Ready(scope.ownerId, assessment)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (request == reportRequestVersion && reportIdentity == (scope.ownerId to date)) {
                        mutablePaperAdviceState.value = PaperAdviceUiState.Error(
                            scope.ownerId,
                            error.message ?: "논문 기반 점검을 불러오지 못했습니다."
                        )
                    }
                }
            }
        }
    }

    private fun nextEditorRequest(): Long {
        editorRequestVersion += 1
        return editorRequestVersion
    }

    override fun onCleared() {
        reportRequestVersion++
        trackingRequestVersion++
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_DATE = "development.date"
        const val KEY_PROFILE_DATE = "development.profile_date"
        const val KEY_GOAL_OWNER_ID = "development.goal_owner_id"
    }
}
