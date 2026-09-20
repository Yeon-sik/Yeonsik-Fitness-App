package com.yeonsik.fitnessapp.feature.development.ui

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
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface DevelopmentUiState {
    data object Idle : DevelopmentUiState
    data object Loading : DevelopmentUiState
    data class Ready(val ownerId: String, val report: DevelopmentReport) : DevelopmentUiState
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
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
    private var editorRequestVersion = 0L
    private var recoveryRequestVersion = 0L

    fun enter(scope: AccountScope, date: String) {
        savedStateHandle[KEY_DATE] = date
        val request = ++reportRequestVersion
        mutableState.value = DevelopmentUiState.Loading
        mutablePaperAdviceState.value = PaperAdviceUiState.Loading(scope.ownerId)
        executor.execute {
            try {
                val report = repository.buildReport(scope, LocalDate.parse(date))
                if (request == reportRequestVersion) {
                    mutableState.postValue(DevelopmentUiState.Ready(scope.ownerId, report))
                }
            } catch (error: Exception) {
                if (request == reportRequestVersion) mutableState.postValue(
                    DevelopmentUiState.Error(scope.ownerId,
                        error.message ?: "발전 리포트를 불러오지 못했습니다.")
                )
            }
        }
        loadPaperAdvice(scope, date, request)
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
        mutableRecoveryEditorState.value =
            RecoveryEditorUiState.Loading(requestId, scope.ownerId, date)
        executor.execute {
            try {
                val goal = recoveryRepository.nutritionGoal(scope)
                val checkIn = recoveryRepository.checkIn(scope, date)
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Ready(
                            requestId,
                            scope.ownerId,
                            checkIn.date,
                            goal,
                            checkIn
                        )
                    )
                }
            } catch (error: Exception) {
                if (requestId == recoveryRequestVersion) {
                    mutableRecoveryEditorState.postValue(
                        RecoveryEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            date,
                            error.message ?: "영양·회복 정보를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun openNutritionGoalEditor(scope: AccountScope) {
        val state = mutableRecoveryEditorState.value as? RecoveryEditorUiState.Ready ?: return
        if (state.ownerId == scope.ownerId) {
            mutableRecoveryEditorState.value =
                state.copy(editor = RecoveryEditorKind.NUTRITION_GOAL)
        }
    }

    fun openCheckInEditor(scope: AccountScope) {
        val state = mutableRecoveryEditorState.value as? RecoveryEditorUiState.Ready ?: return
        if (state.ownerId == scope.ownerId) {
            mutableRecoveryEditorState.value = state.copy(editor = RecoveryEditorKind.CHECK_IN)
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
                if (request == reportRequestVersion) {
                    mutablePaperAdviceState.postValue(
                        PaperAdviceUiState.Ready(scope.ownerId, assessment)
                    )
                }
            } catch (error: Exception) {
                if (request == reportRequestVersion) {
                    mutablePaperAdviceState.postValue(
                        PaperAdviceUiState.Error(
                            scope.ownerId,
                            error.message ?: "논문 기반 점검을 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    private fun nextEditorRequest(): Long {
        editorRequestVersion += 1
        return editorRequestVersion
    }

    override fun onCleared() { executor.shutdownNow() }

    private companion object {
        const val KEY_DATE = "development.date"
        const val KEY_PROFILE_DATE = "development.profile_date"
        const val KEY_GOAL_OWNER_ID = "development.goal_owner_id"
    }
}
