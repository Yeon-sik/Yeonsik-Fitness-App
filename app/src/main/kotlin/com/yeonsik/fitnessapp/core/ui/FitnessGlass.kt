package com.yeonsik.fitnessapp.core.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight

/** Vendor types remain inside this adapter; callers own the captured content. */
@Stable
class FitnessBackdrop internal constructor(internal val layer: LayerBackdrop)

enum class FitnessGlassLevel { BAR, SELECTION, PLAIN }
val LocalFitnessGlassEnabled = staticCompositionLocalOf { false }
internal val LocalFitnessGlassHighContrast = staticCompositionLocalOf { false }
val LocalFitnessPageBackdrop = staticCompositionLocalOf<FitnessBackdrop?> { null }

internal fun fitnessGlassSupported(apiLevel: Int, highContrast: Boolean): Boolean =
    apiLevel >= 31 && !highContrast

@Composable
fun rememberFitnessLayerBackdrop(): FitnessBackdrop {
    val layer = rememberLayerBackdrop()
    return remember(layer) { FitnessBackdrop(layer) }
}

fun Modifier.fitnessBackdropSource(backdrop: FitnessBackdrop): Modifier = layerBackdrop(backdrop.layer)

@Composable
fun Modifier.fitnessGlassSurface(
    shape: Shape,
    level: FitnessGlassLevel = FitnessGlassLevel.BAR,
    backdrop: FitnessBackdrop? = LocalFitnessPageBackdrop.current
): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = LocalFitnessDarkTheme.current
    val highContrast = LocalFitnessGlassHighContrast.current
    if (!LocalFitnessGlassEnabled.current || backdrop == null) {
        return clip(shape).background(
            if (level == FitnessGlassLevel.SELECTION) colors.primaryContainer else colors.surfaceContainerHigh
        ).border(1.dp, if (highContrast) colors.onSurface else colors.outlineVariant, shape)
    }
    val fullEffects = Build.VERSION.SDK_INT >= 33
    val sheen = Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (dark) FitnessUiTokens.GLASS_BAR_SHEEN_TOP_ALPHA_DARK
            else FitnessUiTokens.GLASS_BAR_SHEEN_TOP_ALPHA_LIGHT),
        Color.Transparent,
        colors.surface.copy(alpha = FitnessUiTokens.GLASS_BAR_SHEEN_BOTTOM_ALPHA)
    ))
    return clip(shape).drawBackdrop(
        backdrop = backdrop.layer,
        shape = { shape },
        effects = {
            when (level) {
                FitnessGlassLevel.PLAIN -> {
                    colorControls(saturation = FitnessUiTokens.GLASS_PLAIN_SATURATION)
                    blur(FitnessUiTokens.GLASS_PLAIN_BLUR_DP.dp.toPx())
                }
                FitnessGlassLevel.BAR -> {
                    if (fullEffects) vibrancy()
                    blur(FitnessUiTokens.GLASS_BAR_BLUR_DP.dp.toPx())
                    if (fullEffects) lens(FitnessUiTokens.GLASS_BAR_LENS_HEIGHT_DP.dp.toPx(),
                        FitnessUiTokens.GLASS_BAR_LENS_AMOUNT_DP.dp.toPx())
                }
                FitnessGlassLevel.SELECTION -> {
                    blur(FitnessUiTokens.GLASS_SELECTION_BLUR_DP.dp.toPx())
                    if (fullEffects) lens(FitnessUiTokens.GLASS_SELECTION_LENS_HEIGHT_DP.dp.toPx(),
                        FitnessUiTokens.GLASS_SELECTION_LENS_AMOUNT_DP.dp.toPx())
                }
            }
        },
        highlight = {
            when (level) {
                FitnessGlassLevel.PLAIN -> Highlight.Plain
                FitnessGlassLevel.BAR -> Highlight.Ambient.copy(alpha = FitnessUiTokens.GLASS_BAR_HIGHLIGHT_ALPHA)
                FitnessGlassLevel.SELECTION -> Highlight.Ambient.copy(alpha = FitnessUiTokens.GLASS_SELECTION_HIGHLIGHT_ALPHA)
            }
        },
        onDrawSurface = {
            when (level) {
                FitnessGlassLevel.PLAIN -> drawRect(colors.surface.copy(alpha = FitnessUiTokens.GLASS_PLAIN_SURFACE_ALPHA))
                FitnessGlassLevel.SELECTION -> drawRect(colors.primaryContainer.copy(alpha = FitnessUiTokens.GLASS_SELECTION_TINT_ALPHA))
                FitnessGlassLevel.BAR -> {
                    drawRect(colors.surface.copy(alpha = if (dark) FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_DARK
                        else FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_LIGHT))
                    drawRect(sheen)
                }
            }
        }
    )
}

@Composable
fun Modifier.fitnessGlassPressFeedback(interactionSource: MutableInteractionSource): Modifier {
    val dark = LocalFitnessDarkTheme.current
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val opacity = animateFloatAsState(
        targetValue = when {
            pressed -> if (dark) FitnessUiTokens.GLASS_PRESS_ALPHA_DARK else FitnessUiTokens.GLASS_PRESS_ALPHA_LIGHT
            focused -> if (dark) FitnessUiTokens.GLASS_FOCUS_ALPHA_DARK else FitnessUiTokens.GLASS_FOCUS_ALPHA_LIGHT
            hovered -> FitnessUiTokens.GLASS_HOVER_ALPHA
            else -> 0f
        },
        animationSpec = tween(if (pressed) FitnessUiTokens.GLASS_PRESS_IN_MILLIS else FitnessUiTokens.GLASS_PRESS_OUT_MILLIS),
        label = "glass-press-feedback"
    )
    val tint = if (dark) Color.White else Color(FitnessUiTokens.GLASS_PRESS_TINT_LIGHT)
    return drawWithCache {
        val radius = size.minDimension / 2f
        onDrawBehind { drawRoundRect(tint.copy(alpha = opacity.value), cornerRadius = CornerRadius(radius, radius)) }
    }
}
