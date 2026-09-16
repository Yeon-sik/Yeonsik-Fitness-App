package com.yeonsik.fitnessapp.feature.statistics.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.statistics.api.StatisticsReadApi
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface StatisticsUiState {
    data object Idle : StatisticsUiState
    data class Loading(
        val ownerId: String,
        val referenceDate: String,
        val period: StatisticsPeriod
    ) : StatisticsUiState

    data class Ready(val snapshot: StatisticsSnapshot) : StatisticsUiState

    data class Error(
        val ownerId: String,
        val referenceDate: String,
        val period: StatisticsPeriod,
        val message: String
    ) : StatisticsUiState
}

internal data class StatisticsRequestIdentity(
    val ownerId: String,
    val referenceDate: String,
    val period: StatisticsPeriod
)

/** Prevents a late account or period read from replacing the currently selected statistics. */
internal class StatisticsRequestGate {
    private var nextToken = 0L
    @Volatile private var active: Pair<Long, StatisticsRequestIdentity>? = null

    @Synchronized
    fun begin(identity: StatisticsRequestIdentity): Long {
        val token = ++nextToken
        active = token to identity
        return token
    }

    fun accepts(token: Long, identity: StatisticsRequestIdentity): Boolean =
        active == (token to identity)
}

class StatisticsViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: StatisticsReadApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<StatisticsUiState>(StatisticsUiState.Idle)
    val uiState: LiveData<StatisticsUiState> = mutableState
    private val requestGate = StatisticsRequestGate()

    fun enter(scope: AccountScope, referenceDate: String) {
        load(scope, referenceDate, savedPeriod(scope))
    }

    fun refresh(scope: AccountScope, referenceDate: String) {
        load(scope, referenceDate, savedPeriod(scope))
    }

    fun selectPeriod(
        scope: AccountScope,
        referenceDate: String,
        period: StatisticsPeriod
    ) {
        savedStateHandle[KEY_OWNER] = scope.ownerId
        savedStateHandle[KEY_PERIOD] = period.name
        load(scope, referenceDate, period)
    }

    private fun savedPeriod(scope: AccountScope): StatisticsPeriod {
        if (savedStateHandle.get<String>(KEY_OWNER) == scope.ownerId) {
            return StatisticsPeriod.fromName(savedStateHandle[KEY_PERIOD])
        }
        return StatisticsPeriod.THIRTY_DAYS
    }

    private fun load(
        scope: AccountScope,
        referenceDate: String,
        period: StatisticsPeriod
    ) {
        val identity = StatisticsRequestIdentity(
            ownerId = scope.ownerId,
            referenceDate = referenceDate.trim(),
            period = period
        )
        val request = requestGate.begin(identity)
        savedStateHandle[KEY_OWNER] = identity.ownerId
        savedStateHandle[KEY_REFERENCE_DATE] = identity.referenceDate
        savedStateHandle[KEY_PERIOD] = identity.period.name
        mutableState.value = StatisticsUiState.Loading(
            ownerId = identity.ownerId,
            referenceDate = identity.referenceDate,
            period = identity.period
        )
        executor.execute {
            try {
                val snapshot = repository.load(
                    scope,
                    identity.referenceDate,
                    identity.period
                )
                if (requestGate.accepts(request, identity) &&
                    snapshot.ownerId == identity.ownerId &&
                    snapshot.referenceDate == identity.referenceDate &&
                    snapshot.window.period == identity.period
                ) {
                    mutableState.postValue(StatisticsUiState.Ready(snapshot))
                }
            } catch (error: Exception) {
                if (requestGate.accepts(request, identity)) {
                    mutableState.postValue(
                        StatisticsUiState.Error(
                            ownerId = identity.ownerId,
                            referenceDate = identity.referenceDate,
                            period = identity.period,
                            message = error.message ?: "통계를 불러오지 못했습니다."
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
        const val KEY_OWNER = "statistics.owner"
        const val KEY_REFERENCE_DATE = "statistics.reference_date"
        const val KEY_PERIOD = "statistics.period"
    }
}
