package com.yeonsik.fitnessapp.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * layer that contains it. Root-level bars use the page backdrop from the composition.
 */
@Composable
fun FitnessGlassTabBar(
    tabs: List<FitnessGlassTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = fitnessGlassTabItemHeight(),
    backdrop: FitnessBackdrop? = LocalFitnessPageBackdrop.current
) {
    val dark = LocalFitnessDarkTheme.current
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
            .fitnessGlassSurface(CircleShape, FitnessGlassLevel.BAR, backdrop)
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
                    .fitnessGlassSurface(CircleShape, FitnessGlassLevel.SELECTION, backdrop)
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
                            .fitnessGlassPressFeedback(interactionSource)
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
