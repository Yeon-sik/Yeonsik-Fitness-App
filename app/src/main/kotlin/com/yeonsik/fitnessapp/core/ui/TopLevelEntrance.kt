package com.yeonsik.fitnessapp.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val ENTRANCE_DURATION_MS = 320
private const val ENTRANCE_STAGGER_MS = 50
private const val ENTRANCE_MAX_STEP = 4
private const val ENTRANCE_TOTAL_MS = ENTRANCE_DURATION_MS + ENTRANCE_STAGGER_MS * ENTRANCE_MAX_STEP

/** Saved consumption survives recreation; the animation itself always restores fully visible. */
@Stable
internal class TopLevelEntranceState(consumedToken: Long = 0L) {
    var lastConsumedToken by mutableLongStateOf(consumedToken)
        private set
    val clock = Animatable(1f)

    fun consume(token: Long) { lastConsumedToken = token }

    companion object {
        val Saver = Saver<TopLevelEntranceState, Long>(
            save = { it.lastConsumedToken }, restore = { TopLevelEntranceState(it) }
        )
    }
}

@Composable
internal fun rememberTopLevelEntranceState(destinationKey: String): TopLevelEntranceState =
    rememberSaveable(destinationKey, saver = TopLevelEntranceState.Saver) { TopLevelEntranceState() }

internal fun shouldConsumeTopLevelEntrance(
    token: Long?, lastConsumedToken: Long, isActive: Boolean, contentReady: Boolean
): Boolean = isActive && contentReady && token != null && token > lastConsumedToken

internal fun topLevelEntranceDelayMs(order: Int): Int =
    order.coerceIn(0, ENTRANCE_MAX_STEP) * ENTRANCE_STAGGER_MS

internal fun topLevelEntranceProgress(clock: Float, order: Int): Float {
    val fraction = ((clock * ENTRANCE_TOTAL_MS - topLevelEntranceDelayMs(order)) /
        ENTRANCE_DURATION_MS).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(fraction)
}

@Stable
internal class TopLevelEntranceMotion(
    private val state: TopLevelEntranceState,
    private val token: Long?,
    private val enabled: Boolean
) {
    fun progress(order: Int): Float {
        if (!enabled || token == null || token < state.lastConsumedToken) return 1f
        // Hold new ready content at the starting pose until its effect starts the shared clock.
        if (token > state.lastConsumedToken) return 0f
        return topLevelEntranceProgress(state.clock.value, order)
    }
}

@Composable
internal fun rememberTopLevelEntranceMotion(
    state: TopLevelEntranceState, token: Long?, isActive: Boolean, contentReady: Boolean
): TopLevelEntranceMotion {
    LaunchedEffect(state, token, isActive, contentReady) {
        if (shouldConsumeTopLevelEntrance(token, state.lastConsumedToken, isActive, contentReady)) {
            state.clock.snapTo(0f)
            state.consume(requireNotNull(token))
            state.clock.animateTo(1f, tween(ENTRANCE_TOTAL_MS, easing = LinearEasing))
        } else {
            state.clock.snapTo(1f)
        }
    }
    return remember(state, token, isActive, contentReady) {
        TopLevelEntranceMotion(state, token, isActive && contentReady)
    }
}

/** One component block. Frame reads stay in the layer phase and delay stops growing after step 4. */
@Composable
internal fun TopLevelEntranceContent(
    motion: TopLevelEntranceMotion,
    order: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxWidth().graphicsLayer {
        val progress = motion.progress(order)
        alpha = progress
        translationY = 12.dp.toPx() * (1f - progress)
        scaleX = 0.985f + 0.015f * progress
        scaleY = scaleX
    }) { content() }
}
