package com.yeonsik.fitnessapp.feature.development.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.DevelopmentRepository
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface DevelopmentUiState {
    data object Idle : DevelopmentUiState
    data object Loading : DevelopmentUiState
    data class Ready(val ownerId: String, val report: DevelopmentReport) : DevelopmentUiState
    data class Error(val ownerId: String, val message: String) : DevelopmentUiState
}

class DevelopmentViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DevelopmentRepository,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<DevelopmentUiState>(DevelopmentUiState.Idle)
    val uiState: LiveData<DevelopmentUiState> = mutableState
    private var requestVersion = 0L

    fun enter(scope: AccountScope, date: String) {
        savedStateHandle[KEY_DATE] = date
        val request = ++requestVersion
        mutableState.value = DevelopmentUiState.Loading
        executor.execute {
            try {
                val report = repository.buildReport(LocalDate.parse(date))
                if (request == requestVersion) {
                    mutableState.postValue(DevelopmentUiState.Ready(scope.ownerId, report))
                }
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(
                    DevelopmentUiState.Error(scope.ownerId,
                        error.message ?: "발전 리포트를 불러오지 못했습니다.")
                )
            }
        }
    }

    override fun onCleared() { executor.shutdownNow() }

    private companion object { const val KEY_DATE = "development.date" }
}
