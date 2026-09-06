package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.routine.application.EnsureActiveRoutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface RoutineEntryUiState {
    data object Idle : RoutineEntryUiState
    data object Loading : RoutineEntryUiState
    data class Ready(val ownerId: String, val activeRoutineId: String) : RoutineEntryUiState
    data class Error(val ownerId: String, val message: String) : RoutineEntryUiState
}

/** Runs the legacy default-routine initialization before the Strength screen reads it. */
class RoutineEntryViewModel @JvmOverloads constructor(
    @Suppress("unused") private val savedStateHandle: SavedStateHandle,
    private val ensureActiveRoutine: EnsureActiveRoutine,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<RoutineEntryUiState>(RoutineEntryUiState.Idle)
    val uiState: LiveData<RoutineEntryUiState> = mutableState
    @Volatile private var requestVersion = 0L

    fun enter(scope: AccountScope) {
        val request = ++requestVersion
        mutableState.value = RoutineEntryUiState.Loading
        executor.execute {
            try {
                val activeRoutineId = ensureActiveRoutine.execute(scope)
                if (request == requestVersion) {
                    mutableState.postValue(RoutineEntryUiState.Ready(scope.ownerId, activeRoutineId))
                }
            } catch (error: Exception) {
                if (request == requestVersion) {
                    mutableState.postValue(RoutineEntryUiState.Error(
                        scope.ownerId,
                        error.message ?: "루틴을 불러오지 못했습니다."
                    ))
                }
            }
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }
}
