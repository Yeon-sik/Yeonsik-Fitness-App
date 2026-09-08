package com.yeonsik.fitnessapp.feature.supplement.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress
import com.yeonsik.fitnessapp.supplement.SupplementPlan
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface SupplementUiState {
    data object Idle : SupplementUiState
    data object Loading : SupplementUiState
    data class Ready(
        val ownerId: String,
        val date: String,
        val plans: List<SupplementPlan>,
        val progress: SupplementProgress,
        val adherence: SupplementAdherence
    ) : SupplementUiState
    data class Error(val ownerId: String, val message: String) : SupplementUiState
}

class SupplementViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SupplementRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<SupplementUiState>(SupplementUiState.Idle)
    val uiState: LiveData<SupplementUiState> = mutableState
    private var requestVersion = 0L

    fun enter(scope: AccountScope, date: String) {
        savedStateHandle[KEY_DATE] = date
        load(scope, date, ++requestVersion)
    }

    fun record(scope: AccountScope, date: String, scheduleId: String, status: String) {
        val request = ++requestVersion
        executor.execute {
            try {
                repository.recordNextDose(scheduleId, date, status)
                publish(scope, date, request)
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(
                    SupplementUiState.Error(scope.ownerId,
                        error.message ?: "복용 기록을 저장하지 못했습니다.")
                )
            }
        }
    }

    fun undo(scope: AccountScope, date: String, scheduleId: String) {
        val request = ++requestVersion
        executor.execute {
            try {
                repository.undoLatestRecord(scheduleId, date)
                publish(scope, date, request)
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(
                    SupplementUiState.Error(scope.ownerId,
                        error.message ?: "복용 기록을 되돌리지 못했습니다.")
                )
            }
        }
    }

    private fun load(scope: AccountScope, date: String, request: Long) {
        mutableState.value = SupplementUiState.Loading
        executor.execute { publish(scope, date, request) }
    }

    private fun publish(scope: AccountScope, date: String, request: Long) {
        try {
            val state = SupplementUiState.Ready(
                scope.ownerId,
                date,
                repository.activePlans(date),
                repository.loadProgress(date),
                repository.loadAdherence(LocalDate.parse(date), 14)
            )
            if (request == requestVersion) mutableState.postValue(state)
        } catch (error: Exception) {
            if (request == requestVersion) mutableState.postValue(
                SupplementUiState.Error(scope.ownerId,
                    error.message ?: "보충제 기록을 불러오지 못했습니다.")
            )
        }
    }

    override fun onCleared() { executor.shutdownNow() }

    private companion object { const val KEY_DATE = "supplement.date" }
}
