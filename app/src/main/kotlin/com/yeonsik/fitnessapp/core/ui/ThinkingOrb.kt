package com.yeonsik.fitnessapp.core.ui

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val dark = LocalFitnessDarkTheme.current
    val colors = remember(dark) {
        (if (dark) intArrayOf(
            FitnessUiTokens.COLOR_D_ORB_0, FitnessUiTokens.COLOR_D_ORB_1,
            FitnessUiTokens.COLOR_D_ORB_2, FitnessUiTokens.COLOR_D_ORB_3
        ) else intArrayOf(
            FitnessUiTokens.COLOR_ORB_0, FitnessUiTokens.COLOR_ORB_1,
            FitnessUiTokens.COLOR_ORB_2, FitnessUiTokens.COLOR_ORB_3
        )).map(::Color)
    }
    // Keep the particle design, but avoid 88 Compose Color/DrawScope conversions per frame.
    val paints = remember(colors) { colors.map { color -> Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() } } }
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

    val glowAlpha = if (dark) FitnessUiTokens.ORB_GLOW_ALPHA_DARK else FitnessUiTokens.ORB_GLOW_ALPHA_LIGHT
    Canvas(modifier.size(size).testTag("thinking-orb").drawWithCache {
        val glowRadius = this.size.minDimension * 0.5f
        val glow = Brush.radialGradient(
            listOf(Color(FitnessUiTokens.COLOR_BRAND_BLUE), Color.Transparent),
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            radius = glowRadius.coerceAtLeast(0.01f)
        )
        onDrawBehind {
            val pulse = if (animate) 0.85f + 0.15f * sin(rotationState?.value ?: 0f) else 1f
            drawCircle(glow, radius = glowRadius, alpha = glowAlpha * pulse)
        }
    }) {
        val rotation = rotationState?.value ?: 0f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.38f *
            (1f + 0.035f * sin(rotation))
        val dotRadius = this.size.minDimension * 0.018f
        drawIntoCanvas { canvas ->
          for (particle in orbParticles) {
            val circleRadius = particle.circleRadius
            val angle = particle.angle + rotation
            val depth = sin(angle) * circleRadius
            val pulse = 0.86f + 0.14f * sin(rotation * 2f + particle.phase)
            val opacity = (0.3f + (depth + 1f) * 0.28f) * pulse
            val paint = paints[particle.colorIndex]
            paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            canvas.nativeCanvas.drawCircle(
                center.x + cos(angle) * circleRadius * radius,
                center.y + particle.latitude * radius,
                dotRadius * particle.scale * (0.8f + (depth + 1f) * 0.22f) * pulse,
                paint
            )
          }
        }
    }
}

@Composable
internal fun StartupOrbScreen(message: String = "기록을 불러오는 중") {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentAlignment = Alignment.Center) {
        FitnessLoadingState(listOf(message), FitnessLoadingVariant.FULL_SCREEN)
    }
}
