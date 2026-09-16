package com.yeonsik.fitnessapp.feature.records.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.records.api.RecordsReadApi
import com.yeonsik.fitnessapp.feature.records.model.RecordsSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface RecordsUiState {
    data object Idle : RecordsUiState

    data class Loading(
        val ownerId: String,
        val displayedMonth: String,
        val selectedDate: String,
        val today: String
    ) : RecordsUiState

    data class Ready(val snapshot: RecordsSnapshot) : RecordsUiState

    data class Error(
        val ownerId: String,
        val displayedMonth: String,
        val selectedDate: String,
        val today: String,
        val message: String
    ) : RecordsUiState
}

internal data class RecordsRequestIdentity(
    val ownerId: String,
    val displayedMonth: String,
    val selectedDate: String,
    val today: String
)

/** Monotonic request identity prevents an older month/account read from winning. */
internal class RecordsRequestGate {
    private var nextToken = 0L
    @Volatile private var active: Pair<Long, RecordsRequestIdentity>? = null

    @Synchronized
    fun begin(identity: RecordsRequestIdentity): Long {
        val token = ++nextToken
        active = token to identity
        return token
    }

    fun accepts(token: Long, identity: RecordsRequestIdentity): Boolean =
        active == (token to identity)
}

class RecordsViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: RecordsReadApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<RecordsUiState>(RecordsUiState.Idle)
    val uiState: LiveData<RecordsUiState> = mutableState
    private val requestGate = RecordsRequestGate()

    fun enter(scope: AccountScope, today: String, selectedDate: String) {
        val safeToday = today.trim()
        val safeSelectedDate = selectedDate.trim()
        load(scope, resolveMonth(scope, safeSelectedDate, safeToday), safeSelectedDate, safeToday)
    }

    fun refresh(scope: AccountScope, today: String, selectedDate: String) {
        enter(scope, today, selectedDate)
    }

    /** Persists the selected date's month before AppNavigation publishes the route date. */
    fun rememberSelectedDate(scope: AccountScope, date: String) {
        val parsed = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return
        savedStateHandle[KEY_OWNER] = scope.ownerId
        savedStateHandle[KEY_MONTH] = YearMonth.from(parsed).toString()
    }

    fun previousMonth(scope: AccountScope, today: String, selectedDate: String) {
        moveMonth(scope, today, selectedDate, -1)
    }

    fun nextMonth(scope: AccountScope, today: String, selectedDate: String) {
        moveMonth(scope, today, selectedDate, 1)
    }

    fun showToday(scope: AccountScope, today: String) {
        val safeToday = today.trim()
        val month = runCatching { YearMonth.from(LocalDate.parse(safeToday)) }
            .getOrElse { return }
        load(scope, month, safeToday, safeToday)
    }

    private fun moveMonth(
        scope: AccountScope,
        today: String,
        selectedDate: String,
        delta: Long
    ) {
        val safeToday = today.trim()
        val safeSelectedDate = selectedDate.trim()
        val currentMonth = resolveMonth(scope, safeSelectedDate, safeToday)
        load(scope, currentMonth.plusMonths(delta), safeSelectedDate, safeToday)
    }

    private fun resolveMonth(scope: AccountScope, selectedDate: String, today: String): YearMonth {
        if (savedStateHandle.get<String>(KEY_OWNER) == scope.ownerId) {
            savedStateHandle.get<String>(KEY_MONTH)?.let { savedMonth ->
                runCatching { return YearMonth.parse(savedMonth) }
            }
        }
        return runCatching { YearMonth.from(LocalDate.parse(selectedDate)) }
            .getOrElse { YearMonth.from(LocalDate.parse(today)) }
    }

    private fun load(
        scope: AccountScope,
        month: YearMonth,
        selectedDate: String,
        today: String
    ) {
        val identity = RecordsRequestIdentity(
            ownerId = scope.ownerId,
            displayedMonth = month.toString(),
            selectedDate = selectedDate,
            today = today
        )
        val request = requestGate.begin(identity)
        savedStateHandle[KEY_OWNER] = identity.ownerId
        savedStateHandle[KEY_MONTH] = identity.displayedMonth
        savedStateHandle[KEY_SELECTED_DATE] = identity.selectedDate
        mutableState.value = RecordsUiState.Loading(
            identity.ownerId,
            identity.displayedMonth,
            identity.selectedDate,
            identity.today
        )
        executor.execute {
            try {
                val snapshot = repository.load(
                    scope,
                    identity.displayedMonth,
                    identity.selectedDate,
                    identity.today
                )
                if (requestGate.accepts(request, identity) &&
                    snapshot.ownerId == identity.ownerId &&
                    snapshot.displayedMonth == identity.displayedMonth &&
                    snapshot.selectedDate == identity.selectedDate &&
                    snapshot.today == identity.today
                ) {
                    mutableState.postValue(RecordsUiState.Ready(snapshot))
                }
            } catch (error: Exception) {
                if (requestGate.accepts(request, identity)) {
                    mutableState.postValue(
                        RecordsUiState.Error(
                            identity.ownerId,
                            identity.displayedMonth,
                            identity.selectedDate,
                            identity.today,
                            error.message ?: "기록을 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_OWNER = "records.owner"
        const val KEY_MONTH = "records.month"
        const val KEY_SELECTED_DATE = "records.selected_date"
    }
}
