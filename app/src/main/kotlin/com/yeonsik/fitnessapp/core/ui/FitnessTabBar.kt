package com.yeonsik.fitnessapp.core.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight

@Composable
private fun Modifier.bottomNavigationGlassSurface(
    backdrop: Backdrop,
    useBackdropGlass: Boolean,
    highContrastEnabled: Boolean,
    dark: Boolean
): Modifier {
    val colors = MaterialTheme.colorScheme
    if (!useBackdropGlass) {
        return clip(CircleShape).background(colors.surfaceContainerHigh)
            .border(1.dp, if (highContrastEnabled) colors.onSurface else colors.outlineVariant, CircleShape)
    }
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.10f else 0.28f),
            Color.Transparent,
            colors.surface.copy(alpha = 0.10f)
        )
    )
    return clip(CircleShape).drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            if (fullEffects) vibrancy()
            blur(8.dp.toPx())
            if (fullEffects) {
                lens(10.dp.toPx(), 16.dp.toPx())
            }
        },
        highlight = { Highlight.Ambient.copy(alpha = 0.65f) },
        onDrawSurface = {
            drawRect(colors.surface.copy(alpha = if (dark) 0.34f else 0.30f))
            drawRect(sheen)
        }
    )
}

@Composable
private fun Modifier.bottomNavigationGlassSelection(
    backdrop: Backdrop,
    useBackdropGlass: Boolean
): Modifier {
    val tint = MaterialTheme.colorScheme.primaryContainer
    if (!useBackdropGlass) return clip(CircleShape).background(tint)
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return clip(CircleShape).drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            blur(3.dp.toPx())
            if (fullEffects) {
                lens(4.dp.toPx(), 7.dp.toPx())
            }
        },
        highlight = { Highlight.Ambient.copy(alpha = 0.55f) },
        onDrawSurface = { drawRect(tint.copy(alpha = 0.72f)) }
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
            pressed -> if (dark) 0.08f else 0.10f
            focused -> if (dark) 0.07f else 0.09f
            hovered -> 0.05f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (pressed) 80 else 160)
    )
    val tint = if (dark) Color.White else Color(0xFF28313A)
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

@Composable
internal fun FitnessTextTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    highContrastEnabled: Boolean = Build.VERSION.SDK_INT >= 36 &&
        LocalContext.current.getSystemService(AccessibilityManager::class.java)?.isHighContrastTextEnabled == true,
    useBackdropGlass: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !highContrastEnabled,
    dark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val itemHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelLarge.lineHeight.toDp() + FitnessSpacing.micro * 2
    }.coerceAtLeast(FitnessSpacing.touch)
    FitnessTabBar(selectedIndex, List(labels.size) { 1f }, itemHeight, onSelected, backdrop,
        useBackdropGlass, highContrastEnabled, dark,
        modifier.padding(horizontal = 24.dp, vertical = FitnessSpacing.small), tag) { index, highlighted ->
        Text(labels[index], style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tabs keep their committed selection while tracking a finger. Only an in-bounds release changes it. */
@Composable
internal fun FitnessTabBar(
    selectedIndex: Int,
    tabWeights: List<Float>,
    itemHeight: Dp,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop?,
    useBackdropGlass: Boolean,
    highContrastEnabled: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
    tag: String,
    tabModifier: (Int) -> Modifier = { Modifier },
    content: @Composable (index: Int, highlighted: Boolean) -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectTab by rememberUpdatedState(onSelected)
    var dragIndex by remember(selectedIndex, tabWeights, rtl) { mutableStateOf<Int?>(null) }
    val visibleIndex = dragIndex ?: selectedIndex
    val inlineLayer = if (backdrop == null) rememberLayerBackdrop() else null
    val layer = backdrop ?: requireNotNull(inlineLayer)
    Box(modifier.widthIn(max = 400.dp).fillMaxWidth()) {
        if (inlineLayer != null && useBackdropGlass) {
            // The inline tab bar samples a separate layer to avoid drawing itself into its backdrop.
            Box(Modifier.matchParentSize().clip(CircleShape).layerBackdrop(inlineLayer)
                .background(MaterialTheme.colorScheme.surface))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()
            .bottomNavigationGlassSurface(layer, useBackdropGlass, highContrastEnabled, dark)
            .testTag(tag)
            .pointerInput(selectedIndex, tabWeights, rtl) {
                fun tabAt(position: Offset): Int? {
                    if (position.x !in 0f..size.width.toFloat() || position.y !in 0f..size.height.toFloat()) return null
                    val inset = 4.dp.toPx()
                    val usableWidth = size.width - inset * 2
                    if (usableWidth <= 0f) return null
                    val x = if (rtl) size.width - position.x else position.x
                    val weight = ((x - inset) / usableWidth).coerceIn(0f, 1f) * tabWeights.sum()
                    var boundary = 0f
                    return tabWeights.indexOfFirst {
                        boundary += it
                        weight < boundary
                    }.takeIf { it >= 0 } ?: tabWeights.lastIndex
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture
                    try {
                        while (true) {
                            dragIndex = tabAt(drag.position)
                            val next = awaitHorizontalDragOrCancellation(drag.id) ?: break
                            if (next.changedToUp()) {
                                tabAt(next.position)?.takeIf { it != selectedIndex }?.let { selectTab(it) }
                                break
                            }
                            next.consume()
                            drag = next
                        }
                    } finally {
                        dragIndex = null
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(4.dp)
            .height(itemHeight)
        ) {
            val widthPerWeight = maxWidth / tabWeights.sum()
            val indicatorWidth by animateDpAsState(
                targetValue = widthPerWeight * tabWeights[visibleIndex],
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "bottom-navigation-indicator-width"
            )
            val indicatorOffset by animateDpAsState(
                targetValue = widthPerWeight * tabWeights.take(visibleIndex).sum(),
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "bottom-navigation-indicator"
            )
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .bottomNavigationGlassSelection(layer, useBackdropGlass)
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                tabWeights.forEachIndexed { index, weight ->
                    val interactions = remember(index) { MutableInteractionSource() }
                    Box(Modifier.weight(weight).fillMaxHeight().glassTabFeedback(dark, interactions)
                        .selectable(selected = index == selectedIndex, interactionSource = interactions,
                            indication = null, role = Role.Tab, onClick = { onSelected(index) })
                        .then(tabModifier(index)), contentAlignment = Alignment.Center) {
                        content(index, index == visibleIndex)
                    }
                }
            }
        }
    }
}
