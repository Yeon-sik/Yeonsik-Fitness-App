package com.yeonsik.fitnessapp.core.ui

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class FitnessLoadingVariant { FULL_SCREEN, SECTION, INLINE_CARD }

@Immutable
data class FitnessLoadingTiming(val showDelayMillis: Long = 300, val minVisibleMillis: Long = 500) {
    init { require(showDelayMillis >= 0 && minVisibleMillis >= 0) }
}

val LocalFitnessLoadingTiming = staticCompositionLocalOf { FitnessLoadingTiming() }

/** Keep the loading branch while loading || visible; false during the delay does not mean data is ready. */
@Composable
fun rememberDelayedLoadingVisible(loading: Boolean, identityKey: Any? = Unit): Boolean {
    val timing = LocalFitnessLoadingTiming.current
    var visible by remember(identityKey, timing) { mutableStateOf(false) }
    var minimumElapsed by remember(identityKey, timing) { mutableStateOf(false) }
    LaunchedEffect(loading, identityKey, timing) {
        if (loading && !visible) {
            delay(timing.showDelayMillis)
            minimumElapsed = false
            visible = true
        }
    }
    // This timer survives loading -> ready, but is cancelled when identity changes or the UI leaves composition.
    LaunchedEffect(visible, identityKey, timing) {
        if (visible) {
            delay(timing.minVisibleMillis)
            minimumElapsed = true
        }
    }
    LaunchedEffect(loading, minimumElapsed, identityKey, timing) {
        if (!loading && minimumElapsed) visible = false
    }
    return visible
}

@Composable
internal fun FitnessLoadingState(
    messages: List<String>,
    variant: FitnessLoadingVariant,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    require(messages.isNotEmpty())
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    var messageIndex by remember(messages, visible, motionEnabled) { mutableIntStateOf(0) }
    LaunchedEffect(messages, visible, motionEnabled) {
        if (!visible || !motionEnabled || messages.size == 1) return@LaunchedEffect
        while (true) {
            delay(1_400)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }
    val accessible = modifier.then(if (visible) Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        contentDescription = messages.first()
    } else Modifier.clearAndSetSemantics {})
    val orbSize = when (variant) {
        FitnessLoadingVariant.FULL_SCREEN -> FitnessUiTokens.ORB_SIZE_FULL_SCREEN_DP
        FitnessLoadingVariant.SECTION -> FitnessUiTokens.ORB_SIZE_SECTION_DP
        FitnessLoadingVariant.INLINE_CARD -> FitnessUiTokens.ORB_SIZE_INLINE_DP
    }.dp
    val orb: @Composable () -> Unit = {
        if (visible) ThinkingOrb(size = orbSize) else Spacer(Modifier.size(orbSize))
    }
    if (variant == FitnessLoadingVariant.INLINE_CARD) {
        AppCard(accessible.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(AppSpacing.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.card)) {
                orb()
                RollingLoadingText(messages[messageIndex], motionEnabled,
                    Modifier.weight(1f).alpha(if (visible) 1f else 0f))
            }
        }
    } else {
        Column(accessible.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (variant == FitnessLoadingVariant.FULL_SCREEN) 24.dp else 12.dp,
                Alignment.CenterVertically
            )) {
            orb()
            Text(messages.first(), Modifier.alpha(if (visible) 1f else 0f)
                .testTag("loading-message").clearAndSetSemantics {},
                style = if (variant == FitnessLoadingVariant.FULL_SCREEN) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RollingLoadingText(message: String, motionEnabled: Boolean, modifier: Modifier) {
    val textStyle = MaterialTheme.typography.bodyMedium
    val slotHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() * 2 } + 16.dp
    Box(modifier.height(slotHeight).clipToBounds().testTag("rolling-status-slot")
        .clearAndSetSemantics {}, contentAlignment = Alignment.CenterStart) {
        if (motionEnabled) {
            AnimatedContent(targetState = message, transitionSpec = {
                slideInVertically(tween(380, easing = FastOutSlowInEasing)) { -it } togetherWith
                    slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { it }
            }, label = "rollingLoadingStatus") { current ->
                Text(current, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(message, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
