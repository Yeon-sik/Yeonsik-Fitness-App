package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCell
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindow

data class HomeActivityRequestIdentity(val ownerId: String, val today: String, val pageOffset: Int)

sealed interface HomeActivityDayDetailsUiState {
    data object Idle : HomeActivityDayDetailsUiState
    data class Loading(val ownerId: String, val date: String) : HomeActivityDayDetailsUiState
    data class Ready(val ownerId: String, val details: HomeActivityDayDetails) : HomeActivityDayDetailsUiState
    data class Error(val ownerId: String, val date: String) : HomeActivityDayDetailsUiState
}

sealed interface HomeActivityUiState {
    val identity: HomeActivityRequestIdentity?
    data object Idle : HomeActivityUiState { override val identity = null }
    data class Loading(
        override val identity: HomeActivityRequestIdentity,
        val window: HomeActivityWindow? = null,
        val previous: Ready? = null
    ) : HomeActivityUiState
    data class Ready(
        override val identity: HomeActivityRequestIdentity,
        val window: HomeActivityWindow,
        val cells: List<HomeActivityCell>
    ) : HomeActivityUiState
    data class Empty(override val identity: HomeActivityRequestIdentity) : HomeActivityUiState
    data class Error(
        override val identity: HomeActivityRequestIdentity,
        val message: String,
        val window: HomeActivityWindow? = null,
        val previous: Ready? = null
    ) : HomeActivityUiState
}

/** Results are checked again on the main thread, including already queued publications. */
internal class HomeActivityRequestGate {
    private var nextToken = 0L
    @Volatile private var active: Pair<Long, HomeActivityRequestIdentity>? = null

    @Synchronized fun begin(identity: HomeActivityRequestIdentity): Long {
        val token = ++nextToken
        active = token to identity
        return token
    }

    @Synchronized fun invalidate() { active = null }
    fun accepts(token: Long, identity: HomeActivityRequestIdentity) = active == (token to identity)
}
