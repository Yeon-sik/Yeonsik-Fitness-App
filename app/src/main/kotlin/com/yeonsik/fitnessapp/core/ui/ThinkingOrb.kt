package com.yeonsik.fitnessapp.core.ui

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class OrbParticle(
    val latitude: Float,
    val circleRadius: Float,
    val angle: Float,
    val phase: Float,
    val scale: Float,
    val colorIndex: Int
)

private val orbParticles = List(88) { index ->
    val height = 1f - 2f * (index + 0.5f) / 88f
    OrbParticle(
        latitude = height,
        circleRadius = sqrt(1f - height * height),
        angle = (index * 2.3999632f),
        phase = index * 0.37f,
        scale = 0.72f + (index % 7) * 0.075f,
        colorIndex = index % 4
    )
}

@Composable
internal fun ThinkingOrb(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary, scheme.onSurfaceVariant) {
        arrayOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.onSurfaceVariant)
    }
    val animate = ValueAnimator.areAnimatorsEnabled()
    val rotationState = if (animate) {
        val transition = rememberInfiniteTransition(label = "thinkingOrb")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbPhase"
        )
    } else null

    Canvas(modifier.size(size).testTag("thinking-orb")) {
        val rotation = rotationState?.value ?: 0f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.38f *
            (1f + 0.035f * sin(rotation * 1.4f))
        val dotRadius = this.size.minDimension * 0.018f
        for (particle in orbParticles) {
            val circleRadius = particle.circleRadius
            val angle = particle.angle + rotation * (0.75f + circleRadius * 0.25f)
            val depth = sin(angle) * circleRadius
            val pulse = 0.86f + 0.14f * sin(rotation * 2f + particle.phase)
            val opacity = (0.3f + (depth + 1f) * 0.28f) * pulse
            drawCircle(
                color = colors[particle.colorIndex],
                radius = dotRadius * particle.scale * (0.8f + (depth + 1f) * 0.22f) * pulse,
                center = Offset(
                    center.x + cos(angle) * circleRadius * radius,
                    center.y + particle.latitude * radius
                ),
                alpha = opacity.coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
internal fun StartupOrbScreen() {
    val message = "기록을 불러오는 중"
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .semantics { stateDescription = message },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ThinkingOrb(size = 96.dp)
            Box(Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun OrbLoadingStatus(messages: List<String>, modifier: Modifier = Modifier) {
    require(messages.isNotEmpty())
    var messageIndex by remember(messages) { mutableIntStateOf(0) }
    LaunchedEffect(messages) {
        while (true) {
            delay(1_400)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }
    val message = messages[messageIndex]
    AppCard(modifier.fillMaxWidth().testTag("orb-loading-card")
        .semantics { stateDescription = message }) {
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.card)
        ) {
            ThinkingOrb(size = 52.dp)
            RollingStatusText(message, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun RollingStatusText(message: String, modifier: Modifier = Modifier) {
    val textBox = modifier.height(56.dp).clipToBounds()
    if (!ValueAnimator.areAnimatorsEnabled()) {
        Box(textBox, contentAlignment = Alignment.CenterStart) {
            Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Box(textBox, contentAlignment = Alignment.CenterStart) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                slideInVertically(
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ) togetherWith slideOutVertically(
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    targetOffsetY = { it }
                )
            },
            label = "rollingLoadingStatus"
        ) { currentMessage ->
            Text(currentMessage, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
