package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface RoutineEntryUiState {
    data object Idle : RoutineEntryUiState
    data object Loading : RoutineEntryUiState
    data class Ready(
        val ownerId: String,
        val activeRoutineId: String,
        val routines: List<RoutineSummary> = emptyList(),
        val notice: String? = null
    ) : RoutineEntryUiState
    data class Error(val ownerId: String, val message: String) : RoutineEntryUiState
}

/** Runs account-scoped routine reads and writes before Compose consumes the result. */
class RoutineEntryViewModel @JvmOverloads constructor(
    @Suppress("unused") private val savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepositoryApi,
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
                publish(scope, request, null)
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

    fun createRoutine(scope: AccountScope, name: String) {
        mutate(scope, "루틴을 생성했습니다.") { repository.createRoutine(scope, name) != null }
    }

    fun renameRoutine(scope: AccountScope, routineId: String, name: String) {
        mutate(scope, "루틴 이름을 변경했습니다.") {
            repository.renameRoutine(scope, routineId, name)
        }
    }

    fun copyRoutine(scope: AccountScope, routineId: String, name: String) {
        mutate(scope, "루틴을 복사했습니다.") {
            repository.copyRoutine(scope, routineId, name) != null
        }
    }

    fun deleteRoutine(scope: AccountScope, routineId: String) {
        mutate(scope, "루틴을 삭제했습니다.") {
            repository.deleteRoutine(scope, routineId)
        }
    }

    private fun mutate(scope: AccountScope, successMessage: String, operation: () -> Boolean) {
        val request = ++requestVersion
        executor.execute {
            try {
                if (!operation()) {
                    throw IllegalStateException("루틴 변경을 저장하지 못했습니다.")
                }
                publish(scope, request, successMessage)
            } catch (error: Exception) {
                if (request == requestVersion) {
                    mutableState.postValue(RoutineEntryUiState.Error(
                        scope.ownerId,
                        error.message ?: "루틴 변경을 저장하지 못했습니다."
                    ))
                }
            }
        }
    }

    private fun publish(scope: AccountScope, request: Long, notice: String?) {
        if (request != requestVersion) return
        val activeRoutineId = repository.ensureActiveRoutine(scope)
        val routines = repository.routines(scope)
        mutableState.postValue(RoutineEntryUiState.Ready(
            scope.ownerId,
            activeRoutineId,
            routines,
            notice
        ))
    }

    override fun onCleared() {
        executor.shutdownNow()
    }
}
