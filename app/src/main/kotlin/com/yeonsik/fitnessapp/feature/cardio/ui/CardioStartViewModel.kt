package com.yeonsik.fitnessapp.feature.cardio.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioEnvironment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Pure selection/search policy, shared by the screen holder and its plain content. */
internal data class CardioStartUiState(
    val ownerId: String = "",
    val query: String = "",
    val selectedActivity: CardioActivityType? = null,
    val environment: CardioEnvironment? = null,
    val lastStartedAt: Map<String, Long> = emptyMap(),
    val loading: Boolean = true,
    val notice: String? = null
) {
    val activities: List<CardioActivityType> get() = CardioActivityType.entries
        .filter { type -> query.trim().let { search ->
            search.isEmpty() || (listOf(type.labelKo, type.id) + type.searchAliases)
                .any { it.contains(search, ignoreCase = true) }
        } }
        .sortedWith(compareByDescending<CardioActivityType> { lastStartedAt[it.id] ?: Long.MIN_VALUE }
            .thenBy { it.labelKo })

    val canStart: Boolean get() = !loading && selectedActivity != null && environment != null
        && selectedActivity.supportsEnvironment(environment)

    fun select(type: CardioActivityType): CardioStartUiState = copy(
        selectedActivity = type,
        environment = if (type.supportsOutdoorGps) null else CardioEnvironment.INDOOR
    )
}

/** Owns only a start draft and the account-scoped recent-use read; session writes live elsewhere. */
class CardioStartViewModel @JvmOverloads constructor(
    private val handle: SavedStateHandle,
    private val repository: CardioRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData(CardioStartUiState())
    internal val uiState: LiveData<CardioStartUiState> = mutableState
    private var requestVersion = 0L

    fun enter(scope: AccountScope) {
        val request = ++requestVersion
        if (mutableState.value?.ownerId != scope.ownerId) {
            val restore = handle.get<String>(OWNER) == scope.ownerId
            val activity = if (restore) runCatching {
                CardioActivityType.fromId(handle[ACTIVITY])
            }.getOrNull() else null
            val environment = if (restore) runCatching {
                CardioEnvironment.fromId(handle[ENVIRONMENT])
            }.getOrNull()?.takeIf { activity?.supportsEnvironment(it) == true } else null
            mutableState.value = CardioStartUiState(
                scope.ownerId, if (restore) handle[QUERY] ?: "" else "", activity,
                environment ?: activity?.takeUnless { it.supportsOutdoorGps }?.defaultEnvironment
            )
            persist()
        }
        executor.execute {
            val result = runCatching { repository.lastStartedAtByActivity(scope) }
            mainHandler.post {
                val current = mutableState.value ?: return@post
                if (request != requestVersion || current.ownerId != scope.ownerId) return@post
                mutableState.value = current.copy(
                    lastStartedAt = result.getOrDefault(emptyMap()), loading = false,
                    notice = if (result.isFailure) "최근 기록을 불러오지 못해 이름순으로 표시합니다." else null
                )
            }
        }
    }

    fun search(value: String) = update { it.copy(query = value) }
    fun selectActivity(type: CardioActivityType) = update { it.select(type) }
    fun selectEnvironment(environment: CardioEnvironment) = update {
        if (it.selectedActivity?.supportsOutdoorGps == true) it.copy(environment = environment) else it
    }

    private fun update(change: (CardioStartUiState) -> CardioStartUiState) {
        mutableState.value = change(mutableState.value ?: return)
        persist()
    }

    private fun persist() {
        val state = mutableState.value ?: return
        handle[OWNER] = state.ownerId
        handle[QUERY] = state.query
        handle[ACTIVITY] = state.selectedActivity?.id
        handle[ENVIRONMENT] = state.environment?.id
    }

    override fun onCleared() { requestVersion++; executor.shutdownNow() }
    private companion object {
        const val OWNER = "cardio_start.owner"
        const val QUERY = "cardio_start.query"
        const val ACTIVITY = "cardio_start.activity"
        const val ENVIRONMENT = "cardio_start.environment"
    }
}
