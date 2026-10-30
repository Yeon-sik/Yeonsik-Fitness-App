package com.yeonsik.fitnessapp.core.ui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight

/** One tab of [FitnessGlassTabBar]. [weight] gives a long label more room. */
@Immutable
data class FitnessGlassTab(
    val label: String,
    val icon: ImageVector?,
    val weight: Float = 1f,
    val showsBadge: Boolean = false,
    val stateDescription: String? = null
)

/** Height of one tab: the icon plus a label line that grows with the font scale. */
@Composable
fun fitnessGlassTabItemHeight(): Dp = with(LocalDensity.current) {
    FitnessUiTokens.GLASS_TAB_ICON_BLOCK_DP.dp + MaterialTheme.typography.labelMedium.lineHeight.toDp()
}.coerceAtLeast(FitnessUiTokens.GLASS_TAB_ITEM_MIN_HEIGHT_DP.dp)

/** Space a [FitnessGlassTabBar] with [itemHeight] tabs occupies, margins included. */
fun fitnessGlassTabBarSpace(itemHeight: Dp): Dp = itemHeight +
    (FitnessUiTokens.GLASS_TAB_BAR_INSET_DP.dp + FitnessUiTokens.GLASS_TAB_BAR_MARGIN_VERTICAL_DP.dp) * 2

/**
 * The floating liquid glass tab bar. The bottom navigation and the records hub tabs both use it,
 * so material, geometry, selection motion and press feedback stay identical.
 *
 * [backdrop] must record the content under the bar in a sibling layer: glass cannot sample the
 * layer that contains it.
 */
@Composable
fun FitnessGlassTabBar(
    tabs: List<FitnessGlassTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    highContrastEnabled: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
    itemHeight: Dp = fitnessGlassTabItemHeight()
) {
    val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer
    // Unselected tabs use the primary ink, as iOS does, so they stay legible over bright content.
    val unselectedContent = MaterialTheme.colorScheme.onSurface
    val badgeTint = LocalFitnessColors.current.action
    val badgeStroke = if (dark) Color(FitnessUiTokens.GLASS_BADGE_STROKE_DARK) else Color.White
    val weights = tabs.map { it.weight }
    val indicatorIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    Box(
        modifier
            .padding(
                horizontal = FitnessUiTokens.GLASS_TAB_BAR_MARGIN_HORIZONTAL_DP.dp,
                vertical = FitnessUiTokens.GLASS_TAB_BAR_MARGIN_VERTICAL_DP.dp
            )
            .widthIn(max = FitnessUiTokens.GLASS_TAB_BAR_MAX_WIDTH_DP.dp)
            .fillMaxWidth()
            .glassTabBarSurface(backdrop, glassEnabled, highContrastEnabled, dark)
            // Consume taps on the glass rim rather than activating content underneath it.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(FitnessUiTokens.GLASS_TAB_BAR_INSET_DP.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
        ) {
            val widthPerWeight = maxWidth / weights.sum()
            val indicatorWidth by animateDpAsState(
                targetValue = widthPerWeight * weights[indicatorIndex],
                animationSpec = tween(FitnessUiTokens.GLASS_TAB_INDICATOR_MILLIS, easing = FastOutSlowInEasing),
                label = "glass-tab-indicator-width"
            )
            val indicatorOffset by animateDpAsState(
                targetValue = widthPerWeight * weights.take(indicatorIndex).sum(),
                animationSpec = tween(FitnessUiTokens.GLASS_TAB_INDICATOR_MILLIS, easing = FastOutSlowInEasing),
                label = "glass-tab-indicator"
            )
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .glassTabSelection(backdrop, glassEnabled)
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                tabs.forEachIndexed { index, tab ->
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    val selected = index == selectedIndex
                    val contentColor = if (selected) selectedContent else unselectedContent
                    Column(
                        Modifier
                            .weight(tab.weight)
                            .fillMaxHeight()
                            .glassTabFeedback(dark, interactionSource)
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onTabSelected(index) }
                            )
                            .semantics { tab.stateDescription?.let { stateDescription = it } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Box(Modifier.size(FitnessUiTokens.GLASS_TAB_ICON_DP.dp), contentAlignment = Alignment.Center) {
                            // Decorative: the label supplies the tab's accessible name.
                            tab.icon?.let { Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.fillMaxSize()) }
                            if (tab.showsBadge) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-1).dp)
                                        .size(FitnessUiTokens.NAV_PROGRESS_MARKER_SIZE_DP.dp)
                                        .background(badgeTint, CircleShape)
                                        .border(1.dp, badgeStroke, CircleShape)
                                )
                            }
                        }
                        Text(
                            tab.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scroll edge effect: fades page content into the background where it passes under a glass tab
 * bar. [top] and [bottom] are the space each bar occupies; the fade then runs one card gap
 * further, which is where page content starts at rest. Apply it inside the backdrop layer so
 * the bar samples the softened content as well.
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

@Composable
private fun Modifier.glassTabBarSurface(
    backdrop: Backdrop,
    glassEnabled: Boolean,
    highContrastEnabled: Boolean,
    dark: Boolean
): Modifier {
    val colors = MaterialTheme.colorScheme
    val lifted = shadow(FitnessUiTokens.GLASS_BAR_SHADOW_ELEVATION_DP.dp, CircleShape).clip(CircleShape)
    if (!glassEnabled) {
        return lifted
            .background(colors.surfaceContainerHigh)
            .border(1.dp, if (highContrastEnabled) colors.onSurface else colors.outlineVariant, CircleShape)
    }
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
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
    // The rim keeps the bar distinct over a plain page, like the darkened glass edge on iOS 27.
    val edge = if (dark) Color.White.copy(alpha = FitnessUiTokens.GLASS_BAR_EDGE_ALPHA_DARK)
    else Color.Black.copy(alpha = FitnessUiTokens.GLASS_BAR_EDGE_ALPHA_LIGHT)
    return lifted.drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            if (fullEffects) vibrancy()
            blur(FitnessUiTokens.GLASS_BAR_BLUR_DP.dp.toPx())
            if (fullEffects) {
                lens(FitnessUiTokens.GLASS_BAR_LENS_HEIGHT_DP.dp.toPx(), FitnessUiTokens.GLASS_BAR_LENS_AMOUNT_DP.dp.toPx())
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
    ).border(FitnessUiTokens.GLASS_BAR_EDGE_WIDTH_DP.dp, edge, CircleShape)
}

@Composable
private fun Modifier.glassTabSelection(backdrop: Backdrop, glassEnabled: Boolean): Modifier {
    val tint = MaterialTheme.colorScheme.primaryContainer
    if (!glassEnabled) return clip(CircleShape).background(tint)
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return clip(CircleShape).drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
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

@Composable
private fun Modifier.glassTabFeedback(
    dark: Boolean,
    interactionSource: MutableInteractionSource
): Modifier {
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
        label = "glass-tab-feedback"
    )
    val tint = if (dark) Color.White else Color(FitnessUiTokens.GLASS_PRESS_TINT_LIGHT)
    // Round only the feedback layer. Clipping the full tab cuts off large Korean labels.
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
