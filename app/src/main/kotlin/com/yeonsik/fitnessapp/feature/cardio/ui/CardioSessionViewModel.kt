package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface CardioSessionUiState {
    data object Idle : CardioSessionUiState
    data object Loading : CardioSessionUiState
    data class Ready(val ownerId: String, val session: CardioSessionSnapshot) : CardioSessionUiState
    data class Missing(val ownerId: String, val recordId: String) : CardioSessionUiState
    data class Error(val ownerId: String, val message: String) : CardioSessionUiState
}

/** Persists only the active cardio record id; GPS metrics are always re-read. */
class CardioSessionViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CardioRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<CardioSessionUiState>(CardioSessionUiState.Idle)
    val uiState: LiveData<CardioSessionUiState> = mutableState
    @Volatile private var refreshPending = false
    private var requestVersion = 0L

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
