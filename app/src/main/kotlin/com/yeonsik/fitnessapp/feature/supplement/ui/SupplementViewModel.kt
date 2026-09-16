package com.yeonsik.fitnessapp.feature.supplement.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementEffectCheckin
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementHistoryEntry
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress
import com.yeonsik.fitnessapp.supplement.SupplementPlan
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface SupplementUiState {
    data object Idle : SupplementUiState
    data class Loading(
        val ownerId: String,
        val today: String,
        val selectedDate: String
    ) : SupplementUiState
    data class Ready(
        val ownerId: String,
        val today: String,
        val selectedDate: String,
        val plans: List<SupplementPlan>,
        val progress: SupplementProgress,
        val adherence: SupplementAdherence,
        val history: List<SupplementHistoryEntry>,
        val effects: Map<String, SupplementEffectCheckin?>,
        val notice: String? = null
    ) : SupplementUiState
    data class Error(
        val ownerId: String,
        val today: String,
        val selectedDate: String,
        val message: String
    ) : SupplementUiState
}

internal data class SupplementRequestIdentity(
    val ownerId: String,
    val today: String,
    val selectedDate: String
)

internal class SupplementRequestGate {
    private var nextToken = 0L
    private var current: Pair<Long, SupplementRequestIdentity>? = null

    @Synchronized
    fun begin(identity: SupplementRequestIdentity): Long {
        val token = ++nextToken
        current = token to identity
        return token
    }

    @Synchronized
    fun accepts(token: Long, identity: SupplementRequestIdentity): Boolean =
        current == (token to identity)
}

class SupplementViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SupplementRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<SupplementUiState>(SupplementUiState.Idle)
    val uiState: LiveData<SupplementUiState> = mutableState
    private val requestGate = SupplementRequestGate()

    fun enter(scope: AccountScope, today: String, selectedDate: String? = null) {
        val normalizedToday = normalizeDate(today, LocalDate.now())
        val requested = selectedDate
            ?: savedStateHandle.get<String>(KEY_DATE)
            ?: normalizedToday
        val normalizedSelected = normalizeDate(requested, LocalDate.parse(normalizedToday))
        savedStateHandle[KEY_DATE] = normalizedSelected
        load(scope, normalizedToday, normalizedSelected)
    }

    fun refresh(scope: AccountScope, today: String) {
        enter(scope, today, savedStateHandle.get(KEY_DATE))
    }

    fun selectDate(scope: AccountScope, today: String, date: String) {
        val normalizedToday = normalizeDate(today, LocalDate.now())
        val normalizedSelected = normalizeDate(date, LocalDate.parse(normalizedToday))
        savedStateHandle[KEY_DATE] = normalizedSelected
        load(scope, normalizedToday, normalizedSelected)
    }

    fun record(scope: AccountScope, today: String, date: String, scheduleId: String, status: String) {
        mutate(scope, today, date, "복용 기록을 저장했습니다.") {
            repository.recordNextDose(scheduleId, date, status)
        }
    }

    fun undo(scope: AccountScope, today: String, date: String, scheduleId: String) {
        mutate(scope, today, date, "마지막 복용 기록을 되돌렸습니다.") {
            repository.undoLatestRecord(scheduleId, date)
        }
    }

    fun savePlan(scope: AccountScope, today: String, existing: SupplementPlan?, draft: SupplementPlanDraft) {
        val selectedDate = selectedDate(today)
        val request = begin(scope, today, selectedDate)
        executor.execute {
            try {
                val result = repository.savePlanDraft(existing, draft)
                val notice = if (result.startsTomorrow) {
                    "오늘 기록을 보존하고 내일부터 새 계획을 적용합니다."
                } else if (existing == null) {
                    "복용 계획을 추가했습니다."
                } else {
                    "복용 계획을 수정했습니다."
                }
                publish(scope, today, selectedDate, request, notice)
            } catch (error: Exception) {
                publishError(scope, today, selectedDate, request, error, "복용 계획을 저장하지 못했습니다.")
            }
        }
    }

    fun archive(scope: AccountScope, today: String, itemId: String) {
        val selectedDate = selectedDate(today)
        val request = begin(scope, today, selectedDate)
        executor.execute {
            try {
                repository.archivePlan(itemId)
                publish(scope, today, selectedDate, request, "복용 계획을 종료했습니다. 이전 기록은 유지됩니다.")
            } catch (error: Exception) {
                publishError(scope, today, selectedDate, request, error, "복용 계획을 종료하지 못했습니다.")
            }
        }
    }

    fun updateHistory(scope: AccountScope, today: String, recordId: String, status: String) {
        val selectedDate = selectedDate(today)
        mutate(scope, today, selectedDate, "복용 기록을 수정했습니다.") {
            repository.updateRecordStatus(recordId, status)
        }
    }

    fun deleteHistory(scope: AccountScope, today: String, recordId: String) {
        val selectedDate = selectedDate(today)
        mutate(scope, today, selectedDate, "복용 기록을 삭제했습니다.") {
            repository.deleteRecord(recordId)
        }
    }

    fun saveEffectCheckin(
        scope: AccountScope,
        today: String,
        itemId: String,
        effectScore: Int,
        adverseEffects: String,
        note: String
    ) {
        val selectedDate = selectedDate(today)
        mutate(scope, today, selectedDate, "효능·이상반응 점검을 저장했습니다.") {
            repository.saveEffectCheckin(itemId, selectedDate, effectScore, adverseEffects, note)
        }
    }

    private fun load(scope: AccountScope, today: String, selectedDate: String) {
        val request = begin(scope, today, selectedDate)
        mutableState.value = SupplementUiState.Loading(scope.ownerId, today, selectedDate)
        executor.execute { publish(scope, today, selectedDate, request, null) }
    }

    private fun mutate(
        scope: AccountScope,
        today: String,
        selectedDate: String,
        notice: String,
        action: () -> Unit
    ) {
        val request = begin(scope, today, selectedDate)
        executor.execute {
            try {
                action()
                publish(scope, today, selectedDate, request, notice)
            } catch (error: Exception) {
                publishError(scope, today, selectedDate, request, error, "보충제 기록을 저장하지 못했습니다.")
            }
        }
    }

    private fun begin(scope: AccountScope, today: String, selectedDate: String): Long =
        requestGate.begin(SupplementRequestIdentity(scope.ownerId, today, selectedDate))

    private fun selectedDate(today: String): String {
        val todayDate = LocalDate.parse(today)
        return normalizeDate(savedStateHandle.get(KEY_DATE) ?: today, todayDate)
    }

    private fun publish(
        scope: AccountScope,
        today: String,
        selectedDate: String,
        request: Long,
        notice: String?
    ) {
        try {
            val identity = SupplementRequestIdentity(scope.ownerId, today, selectedDate)
            val plans = repository.activePlans(selectedDate)
            val state = SupplementUiState.Ready(
                ownerId = scope.ownerId,
                today = today,
                selectedDate = selectedDate,
                plans = plans,
                progress = repository.loadProgress(selectedDate),
                adherence = repository.loadAdherence(LocalDate.parse(selectedDate), 14),
                history = repository.loadHistory(LocalDate.parse(selectedDate), 7),
                effects = plans.associate { it.itemId to repository.loadLatestEffectCheckin(it.itemId) },
                notice = notice
            )
            if (requestGate.accepts(request, identity)) mutableState.postValue(state)
        } catch (error: Exception) {
            publishError(scope, today, selectedDate, request, error, "보충제 기록을 불러오지 못했습니다.")
        }
    }

    private fun publishError(
        scope: AccountScope,
        today: String,
        selectedDate: String,
        request: Long,
        error: Exception,
        fallback: String
    ) {
        val identity = SupplementRequestIdentity(scope.ownerId, today, selectedDate)
        if (requestGate.accepts(request, identity)) {
            mutableState.postValue(
                SupplementUiState.Error(scope.ownerId, today, selectedDate, error.message ?: fallback)
            )
        }
    }

    private fun normalizeDate(value: String, maxDate: LocalDate): String {
        val parsed = runCatching { LocalDate.parse(value) }.getOrDefault(maxDate)
        return minOf(parsed, maxDate).toString()
    }

    override fun onCleared() { executor.shutdownNow() }

    private companion object { const val KEY_DATE = "supplement.date" }
}
