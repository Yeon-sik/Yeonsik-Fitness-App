package com.yeonsik.fitnessapp.feature.development.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.BodyProfile
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.feature.development.application.DevelopmentApplicationService
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReportApi
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface DevelopmentUiState {
    data object Idle : DevelopmentUiState
    data object Loading : DevelopmentUiState
    data class Ready(val ownerId: String, val report: DevelopmentReport) : DevelopmentUiState
    data class Error(val ownerId: String, val message: String) : DevelopmentUiState
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

class DevelopmentViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DevelopmentReportApi,
    private val applicationService: DevelopmentApplicationService,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<DevelopmentUiState>(DevelopmentUiState.Idle)
    val uiState: LiveData<DevelopmentUiState> = mutableState
    private val mutableProfileEditorState =
        MutableLiveData<DevelopmentProfileEditorUiState>(DevelopmentProfileEditorUiState.Idle)
    val profileEditorState: LiveData<DevelopmentProfileEditorUiState> = mutableProfileEditorState
    private val mutableGoalEditorState =
        MutableLiveData<DevelopmentGoalEditorUiState>(DevelopmentGoalEditorUiState.Idle)
    val goalEditorState: LiveData<DevelopmentGoalEditorUiState> = mutableGoalEditorState
    private var reportRequestVersion = 0L
    private var editorRequestVersion = 0L

    fun enter(scope: AccountScope, date: String) {
        savedStateHandle[KEY_DATE] = date
        val request = ++reportRequestVersion
        mutableState.value = DevelopmentUiState.Loading
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
