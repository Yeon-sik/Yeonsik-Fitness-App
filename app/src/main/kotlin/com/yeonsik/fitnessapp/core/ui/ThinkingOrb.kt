package com.yeonsik.fitnessapp.core.ui

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
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
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // A blue family per theme; Paint objects are reused instead of converting 88 colors per frame.
    val paints = remember(dark) {
        val palette = if (dark) {
            intArrayOf(FitnessUiTokens.COLOR_D_ORB_0, FitnessUiTokens.COLOR_D_ORB_1,
                FitnessUiTokens.COLOR_D_ORB_2, FitnessUiTokens.COLOR_D_ORB_3)
        } else {
            intArrayOf(FitnessUiTokens.COLOR_ORB_0, FitnessUiTokens.COLOR_ORB_1,
                FitnessUiTokens.COLOR_ORB_2, FitnessUiTokens.COLOR_ORB_3)
        }
        palette.map { color -> Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color } }
    }
    val glowAlpha = if (dark) FitnessUiTokens.ORB_GLOW_ALPHA_DARK else FitnessUiTokens.ORB_GLOW_ALPHA_LIGHT
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

    Spacer(modifier.size(size).testTag("thinking-orb").drawWithCache {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val glowRadius = this.size.minDimension * 0.5f
        val glow = Brush.radialGradient(
            listOf(Color(FitnessUiTokens.COLOR_BRAND_BLUE), Color.Transparent),
            center = center,
            radius = glowRadius
        )
        onDrawBehind {
            val rotation = rotationState?.value ?: 0f
            // The glow breathes with the rotation; with animations off it stays static.
            val pulse = if (rotationState == null) 1f else 0.85f + 0.15f * sin(rotation)
            drawCircle(glow, radius = glowRadius, center = center, alpha = glowAlpha * pulse)
            val radius = this.size.minDimension * 0.38f *
                (1f + 0.035f * sin(rotation))
            val dotRadius = this.size.minDimension * 0.018f
            drawIntoCanvas { canvas ->
                for (particle in orbParticles) {
                    val circleRadius = particle.circleRadius
                    val angle = particle.angle + rotation
                    val depth = sin(angle) * circleRadius
                    val particlePulse = 0.86f + 0.14f * sin(rotation * 2f + particle.phase)
                    val opacity = (0.3f + (depth + 1f) * 0.28f) * particlePulse
                    val paint = paints[particle.colorIndex]
                    paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
                    canvas.nativeCanvas.drawCircle(
                        center.x + cos(angle) * circleRadius * radius,
                        center.y + particle.latitude * radius,
                        dotRadius * particle.scale * (0.8f + (depth + 1f) * 0.22f) * particlePulse,
                        paint
                    )
                }
            }
        }
    })
}

@Composable
internal fun StartupOrbScreen(message: String = "기록을 불러오는 중") {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentAlignment = Alignment.Center) {
        FitnessLoadingState(listOf(message), FitnessLoadingVariant.FULL_SCREEN)
    }
}
