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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight

/*
 * Liquid glass for floating controls. This is the only file that imports the alpha backdrop
 * library (BackdropImportIsolationTest); everything else uses the API below.
 */

/** Strength of a glass surface: floating bar, selection inside a bar, or a quiet frost. */
enum class FitnessGlassLevel { BAR, SELECTION, PLAIN }

/** Glass may render: API 31+ and high-contrast text off. FitnessComposeTheme provides it. */
val LocalFitnessGlassEnabled = staticCompositionLocalOf { false }

/** Backdrop of the page under root-level floating controls; null inside the page itself. */
val LocalFitnessPageBackdrop = staticCompositionLocalOf<FitnessBackdrop?> { null }

/** A recorded layer that glass samples. Record it with [fitnessBackdropSource]. */
@Stable
class FitnessBackdrop internal constructor(
    internal val sample: Backdrop,
    internal val record: Modifier
)

@Composable
fun rememberFitnessBackdrop(): FitnessBackdrop {
    val layer = rememberLayerBackdrop()
    return remember(layer) { FitnessBackdrop(layer, Modifier.layerBackdrop(layer)) }
}

/**
 * Records this node's drawing for glass to sample. Glass cannot sample the layer that contains
 * it, so the source and the glass must be siblings.
 */
fun Modifier.fitnessBackdropSource(backdrop: FitnessBackdrop): Modifier = then(backdrop.record)

/**
 * Draws a glass surface clipped to [shape]. Without glass support or a [backdrop] it falls back
 * to an opaque surface with a 1dp outline (onSurface with high-contrast text), keeping the same
 * hierarchy and touch behavior.
 */
@Composable
fun Modifier.fitnessGlassSurface(
    shape: Shape,
    level: FitnessGlassLevel = FitnessGlassLevel.BAR,
    backdrop: FitnessBackdrop? = LocalFitnessPageBackdrop.current
): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = LocalFitnessDarkTheme.current
    val highContrast = rememberHighContrastTextEnabled()
    // Floating bars cast a soft shadow; selections and plain frost sit flat.
    val clipped = (if (level == FitnessGlassLevel.BAR) {
        shadow(FitnessUiTokens.GLASS_BAR_SHADOW_ELEVATION_DP.dp, shape)
    } else this).clip(shape)
    if (!LocalFitnessGlassEnabled.current || backdrop == null) {
        return if (level == FitnessGlassLevel.SELECTION) {
            clipped.background(colors.primaryContainer)
        } else {
            clipped.background(colors.surfaceContainerHigh)
                .border(1.dp, if (highContrast) colors.onSurface else colors.outlineVariant, shape)
        }
    }
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return when (level) {
        FitnessGlassLevel.BAR -> {
            val sheen = Brush.verticalGradient(
                listOf(
                    Color.White.copy(
                        alpha = if (dark) FitnessUiTokens.GLASS_BAR_SHEEN_TOP_ALPHA_DARK
                        else FitnessUiTokens.GLASS_BAR_SHEEN_TOP_ALPHA_LIGHT
                    ),
                    Color.Transparent,
                    colors.surface.copy(alpha = FitnessUiTokens.GLASS_BAR_SHEEN_BOTTOM_ALPHA)
                )
            )
            // The rim keeps the bar distinct over a plain page, like iOS 27's darkened glass edge.
            val edge = if (dark) Color.White.copy(alpha = FitnessUiTokens.GLASS_BAR_EDGE_ALPHA_DARK)
            else Color.Black.copy(alpha = FitnessUiTokens.GLASS_BAR_EDGE_ALPHA_LIGHT)
            clipped.drawBackdrop(
                backdrop = backdrop.sample,
                shape = { shape },
                effects = {
                    if (fullEffects) vibrancy()
                    blur(FitnessUiTokens.GLASS_BAR_BLUR_DP.dp.toPx())
                    if (fullEffects) {
                        lens(
                            FitnessUiTokens.GLASS_BAR_LENS_HEIGHT_DP.dp.toPx(),
                            FitnessUiTokens.GLASS_BAR_LENS_AMOUNT_DP.dp.toPx()
                        )
                    }
                },
                highlight = { Highlight.Ambient.copy(alpha = FitnessUiTokens.GLASS_BAR_HIGHLIGHT_ALPHA) },
                onDrawSurface = {
                    drawRect(
                        colors.surface.copy(
                            alpha = if (dark) FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_DARK
                            else FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_LIGHT
                        )
                    )
                    drawRect(sheen)
                }
            ).border(FitnessUiTokens.GLASS_BAR_EDGE_WIDTH_DP.dp, edge, shape)
        }
        FitnessGlassLevel.SELECTION -> {
            val tint = colors.primaryContainer
            clipped.drawBackdrop(
                backdrop = backdrop.sample,
                shape = { shape },
                effects = {
                    blur(FitnessUiTokens.GLASS_SELECTION_BLUR_DP.dp.toPx())
                    if (fullEffects) {
                        lens(
                            FitnessUiTokens.GLASS_SELECTION_LENS_HEIGHT_DP.dp.toPx(),
                            FitnessUiTokens.GLASS_SELECTION_LENS_AMOUNT_DP.dp.toPx()
                        )
                    }
                },
                highlight = { Highlight.Ambient.copy(alpha = FitnessUiTokens.GLASS_SELECTION_HIGHLIGHT_ALPHA) },
                onDrawSurface = { drawRect(tint.copy(alpha = FitnessUiTokens.GLASS_SELECTION_TINT_ALPHA)) }
            )
        }
        FitnessGlassLevel.PLAIN -> clipped.drawBackdrop(
            backdrop = backdrop.sample,
            shape = { shape },
            effects = {
                colorControls(saturation = FitnessUiTokens.GLASS_PLAIN_SATURATION)
                blur(FitnessUiTokens.GLASS_PLAIN_BLUR_DP.dp.toPx())
            },
            highlight = { Highlight.Plain },
            onDrawSurface = { drawRect(colors.surface.copy(alpha = FitnessUiTokens.GLASS_PLAIN_SURFACE_ALPHA)) }
        )
    }
}

/** Pressed, focused and hovered tint for controls on glass, rounded to the control's height. */
@Composable
fun Modifier.fitnessGlassPressFeedback(interactionSource: MutableInteractionSource): Modifier {
    val dark = LocalFitnessDarkTheme.current
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val opacity by animateFloatAsState(
        targetValue = when {
            pressed -> if (dark) FitnessUiTokens.GLASS_PRESS_ALPHA_DARK else FitnessUiTokens.GLASS_PRESS_ALPHA_LIGHT
            focused -> if (dark) FitnessUiTokens.GLASS_FOCUS_ALPHA_DARK else FitnessUiTokens.GLASS_FOCUS_ALPHA_LIGHT
            hovered -> FitnessUiTokens.GLASS_HOVER_ALPHA
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (pressed) FitnessUiTokens.GLASS_PRESS_IN_MILLIS else FitnessUiTokens.GLASS_PRESS_OUT_MILLIS
        ),
        label = "glass-press-feedback"
    )
    val tint = if (dark) Color.White else Color(FitnessUiTokens.GLASS_PRESS_TINT_LIGHT)
    // Round only the feedback layer. Clipping the whole control cuts off large Korean labels.
    return drawWithCache {
        val radius = size.minDimension / 2f
        onDrawBehind {
            drawRoundRect(
                color = tint.copy(alpha = opacity),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

/**
 * Scroll edge effect: fades page content into the background where it passes under a floating
 * glass bar. [top] and [bottom] are the space each bar occupies; the fade then runs one card gap
 * further, which is where page content starts at rest. Apply it inside the backdrop source so
 * the glass samples the softened content as well.
 */
@Composable
fun Modifier.fitnessGlassScrollEdge(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier {
    val background = MaterialTheme.colorScheme.background
    return drawWithCache {
        val edge = background.copy(alpha = FitnessUiTokens.GLASS_SCROLL_EDGE_ALPHA)
        val bar = background.copy(alpha = FitnessUiTokens.GLASS_SCROLL_EDGE_BAR_ALPHA)
        val tail = FitnessSpacing.gap.toPx()
        val topHeight = top.toPx() + tail
        val bottomHeight = bottom.toPx() + tail
        val bottomStart = size.height - bottomHeight
        val topBrush = if (top > 0.dp) Brush.verticalGradient(
            0f to edge, top.toPx() / topHeight to bar, 1f to Color.Transparent,
            startY = 0f, endY = topHeight
        ) else null
        val bottomBrush = if (bottom > 0.dp) Brush.verticalGradient(
            0f to Color.Transparent, tail / bottomHeight to bar, 1f to edge,
            startY = bottomStart, endY = size.height
        ) else null
        onDrawWithContent {
            drawContent()
            topBrush?.let { drawRect(it, size = Size(size.width, topHeight)) }
            bottomBrush?.let { drawRect(it, topLeft = Offset(0f, bottomStart), size = Size(size.width, bottomHeight)) }
        }
    }
}
