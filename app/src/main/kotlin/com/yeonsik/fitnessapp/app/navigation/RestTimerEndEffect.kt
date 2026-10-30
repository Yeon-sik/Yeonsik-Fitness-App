package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRestTimerState
import kotlinx.coroutines.delay

/**
 * Ends [ownerId]'s rest timer on time whatever screen is showing. The capsule only appears on
 * session screens, so the end check must not live inside it.
 *
 * It re-reads [clock] at least every [REST_TIMER_END_CHECK_MILLIS] so a device sleep, which
 * pauses coroutine delays, cannot postpone the end.
 */
@Composable
internal fun RestTimerEndEffect(
    state: WorkoutRestTimerState,
    ownerId: String,
    onRestEnded: () -> Unit,
    clock: () -> Long = System::currentTimeMillis
) {
    val active = (state as? WorkoutRestTimerState.Active)?.takeIf { it.ownerId == ownerId }
    val currentOnRestEnded by rememberUpdatedState(onRestEnded)
    val currentClock by rememberUpdatedState(clock)
    LaunchedEffect(active?.ownerId, active?.endsAtMillis) {
        if (active == null) return@LaunchedEffect
        while (true) {
            val remaining = active.endsAtMillis - currentClock()
            if (remaining <= 0) break
            delay(remaining.coerceAtMost(REST_TIMER_END_CHECK_MILLIS))
        }
        currentOnRestEnded()
    }
}

private const val REST_TIMER_END_CHECK_MILLIS = 250L
