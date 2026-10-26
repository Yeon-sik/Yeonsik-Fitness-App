package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Foreground expiry belongs to the app, independently of the page displaying the capsule. */
@Composable
internal fun WorkoutRestTimerHost(
    state: WorkoutRestTimerState,
    ownerId: String,
    onElapsed: (WorkoutRestTimerState.Active) -> Unit,
    nowMillis: () -> Long = System::currentTimeMillis
) {
    val active = (state as? WorkoutRestTimerState.Active)?.takeIf { it.ownerId == ownerId }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val elapsed by rememberUpdatedState(onElapsed)
    val clock by rememberUpdatedState(nowMillis)
    LaunchedEffect(active?.ownerId, active?.endsAtMillis, lifecycle) {
        if (active == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (clock() < active.endsAtMillis) {
                delay((active.endsAtMillis - clock()).coerceIn(1L, 250L))
            }
            elapsed(active)
            awaitCancellation()
        }
    }
}
