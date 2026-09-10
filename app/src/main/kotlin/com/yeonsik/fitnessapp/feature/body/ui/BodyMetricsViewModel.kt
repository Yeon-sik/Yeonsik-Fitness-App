package com.yeonsik.fitnessapp.feature.body.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.body.application.BodyMetricsApplicationService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface BodyMetricsEditorUiState {
    data object Idle : BodyMetricsEditorUiState
    data class Loading(val requestId: Long, val ownerId: String) : BodyMetricsEditorUiState
    data class Ready(
        val requestId: Long,
        val ownerId: String,
        val editor: BodyMetricsApplicationService.Editor
    ) : BodyMetricsEditorUiState

    data class Saving(val requestId: Long, val ownerId: String) : BodyMetricsEditorUiState
    data class Saved(val requestId: Long, val ownerId: String, val recordId: String) : BodyMetricsEditorUiState
    data class Deleted(val requestId: Long, val ownerId: String, val recordId: String) : BodyMetricsEditorUiState
    data class Error(val requestId: Long, val ownerId: String, val message: String) : BodyMetricsEditorUiState
}

class BodyMetricsViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val applicationService: BodyMetricsApplicationService,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableEditorState = MutableLiveData<BodyMetricsEditorUiState>(BodyMetricsEditorUiState.Idle)
    val editorState: LiveData<BodyMetricsEditorUiState> = mutableEditorState
    private var requestVersion = 0L

    fun open(scope: AccountScope, date: String, recordId: String?) {
        val requestId = nextRequest()
        saveRestoration(scope.ownerId, date, recordId)
        mutableEditorState.value = BodyMetricsEditorUiState.Loading(requestId, scope.ownerId)
        executor.execute {
            try {
                val editor = applicationService.load(scope, date, recordId)
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Ready(requestId, scope.ownerId, editor)
                    )
                }
            } catch (error: Exception) {
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "체중 기록을 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun save(
        scope: AccountScope,
        recordId: String?,
        date: String,
        weightKg: Double,
        memo: String
    ) {
        val requestId = nextRequest()
        saveRestoration(scope.ownerId, date, recordId)
        mutableEditorState.value = BodyMetricsEditorUiState.Saving(requestId, scope.ownerId)
        executor.execute {
            try {
                val savedRecordId = applicationService.save(scope, recordId, date, weightKg, memo)
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Saved(requestId, scope.ownerId, savedRecordId)
                    )
                }
            } catch (error: Exception) {
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "체중 기록을 저장하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun delete(scope: AccountScope, recordId: String) {
        val requestId = nextRequest()
        saveRestoration(scope.ownerId, savedStateHandle[KEY_DATE], recordId)
        mutableEditorState.value = BodyMetricsEditorUiState.Saving(requestId, scope.ownerId)
        executor.execute {
            try {
                applicationService.delete(scope, recordId)
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Deleted(requestId, scope.ownerId, recordId)
                    )
                }
            } catch (error: Exception) {
                if (requestId == requestVersion) {
                    mutableEditorState.postValue(
                        BodyMetricsEditorUiState.Error(
                            requestId,
                            scope.ownerId,
                            error.message ?: "체중 기록을 삭제하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun dismissEditor() {
        mutableEditorState.value = BodyMetricsEditorUiState.Idle
    }

    private fun nextRequest(): Long {
        requestVersion += 1
        return requestVersion
    }

    private fun saveRestoration(ownerId: String, date: String?, recordId: String?) {
        savedStateHandle[KEY_OWNER_ID] = ownerId
        if (date == null) savedStateHandle.remove<String>(KEY_DATE) else savedStateHandle[KEY_DATE] = date
        if (recordId == null) {
            savedStateHandle.remove<String>(KEY_RECORD_ID)
        } else {
            savedStateHandle[KEY_RECORD_ID] = recordId
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_OWNER_ID = "body_metrics.owner_id"
        const val KEY_DATE = "body_metrics.date"
        const val KEY_RECORD_ID = "body_metrics.record_id"
    }
}
