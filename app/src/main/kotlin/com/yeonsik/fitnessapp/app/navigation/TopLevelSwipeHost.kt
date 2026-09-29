package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.state.FitnessScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Keeps both pages under the finger until the drag settles on the next tab or springs back. */
@Composable
internal fun TopLevelSwipeHost(
    screen: FitnessScreen,
    navigation: AppNavigationViewModel,
    modifier: Modifier = Modifier,
    onSettlingDestinationChange: (FitnessScreen?) -> Unit,
    content: @Composable (FitnessScreen) -> Unit
) {
    val forwardPage = remember(screen) { navigation.adjacentTopLevel(forward = true) }
    val backwardPage = remember(screen) { navigation.adjacentTopLevel(forward = false) }
    val settleThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val scope = rememberCoroutineScope()
    var dragOffset by remember(screen) { mutableFloatStateOf(0f) }
    var settleJob by remember(screen) { mutableStateOf<Job?>(null) }
    val previewPage by remember(screen) {
        derivedStateOf {
            when {
                dragOffset < 0f -> forwardPage
                dragOffset > 0f -> backwardPage
                else -> null
            }
        }
    }

    DisposableEffect(screen) {
        onDispose { settleJob?.cancel() }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val pageWidth = constraints.maxWidth.toFloat()

        fun settle(complete: Boolean) {
            val destination = if (complete) {
                topLevelSwipeDestination(
                    dragOffset, settleThreshold.coerceAtMost(pageWidth * 0.25f),
                    forwardPage, backwardPage
                )
            } else null
            val target = if (destination != null) {
                if (dragOffset < 0f) -pageWidth else pageWidth
            } else {
                0f
            }
            settleJob?.cancel()
            onSettlingDestinationChange(destination)
            settleJob = scope.launch {
                Animatable(dragOffset).animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { dragOffset = value }
                if (destination != null && navigation.currentScreen() == screen) {
                    navigation.selectTopLevel(destination)
                } else {
                    onSettlingDestinationChange(null)
                }
                dragOffset = 0f
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(screen, pageWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            onSettlingDestinationChange(null)
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val proposed = (dragOffset + amount).coerceIn(-pageWidth, pageWidth)
                            dragOffset = when {
                                proposed < 0f && forwardPage != null -> proposed
                                proposed > 0f && backwardPage != null -> proposed
                                else -> 0f
                            }
                        },
                        onDragEnd = { settle(complete = true) },
                        onDragCancel = { settle(complete = false) }
                    )
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragOffset }
            ) {
                content(screen)
            }
            if (previewPage != null) {
                val pagePosition = if (dragOffset < 0f) pageWidth else -pageWidth
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = dragOffset + pagePosition }
                ) {
                    content(previewPage!!)
                }
            }
        }
    }
}

internal fun topLevelSwipeDestination(
    dragOffset: Float,
    threshold: Float,
    forwardPage: FitnessScreen?,
    backwardPage: FitnessScreen?
): FitnessScreen? = when {
    abs(dragOffset) < threshold -> null
    dragOffset < 0f -> forwardPage
    dragOffset > 0f -> backwardPage
    else -> null
}

internal data class TopLevelSwipeSelection(
    val source: FitnessScreen,
    val destination: FitnessScreen
)

internal fun visualActiveTopLevelTab(
    screen: FitnessScreen,
    swipeSelection: TopLevelSwipeSelection?
): FitnessScreen = swipeSelection?.takeIf { it.source == screen }?.destination ?: screen
