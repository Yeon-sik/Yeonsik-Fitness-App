package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Owned above the swipe host so both current and preview pages share the first entrance. */
@Stable
internal class HomeEntranceState(played: Boolean = false) {
    var played by mutableStateOf(played)
        private set

    fun play() { played = true }

    companion object {
        val Saver = Saver<HomeEntranceState, Boolean>(
            save = { it.played },
            restore = { HomeEntranceState(it) }
        )
    }
}

@Composable
internal fun rememberHomeEntranceState(ownerId: String): HomeEntranceState =
    rememberSaveable(ownerId, saver = HomeEntranceState.Saver) { HomeEntranceState() }
