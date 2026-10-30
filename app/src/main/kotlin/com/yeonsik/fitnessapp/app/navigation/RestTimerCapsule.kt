package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessGlassLevel
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.core.ui.fitnessGlassSurface

/**
 * Floating rest timer: a glass capsule when collapsed, a 28dp rounded panel when expanded.
 * It must sit outside the page's backdrop source, beside the bottom bar.
 */
@Composable
internal fun RestTimerCapsule(
    timeLabel: String,
    progress: Float,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    val expandedRadius = FitnessUiTokens.REST_TIMER_EXPANDED_RADIUS_DP.dp
    // Collapsed, the radius is half the measured height so the capsule stays round at any font scale.
    val collapsedRadius = with(density) { (heightPx / 2).toDp() }.coerceAtLeast(expandedRadius)
    val corner by animateDpAsState(
        targetValue = if (expanded) expandedRadius else collapsedRadius,
        animationSpec = tween(FitnessUiTokens.REST_TIMER_SHAPE_MILLIS),
        label = "rest-timer-corner"
    )
    val tabularDigits = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
    Column(
        modifier
            .widthIn(max = FitnessUiTokens.REST_TIMER_MAX_WIDTH_DP.dp)
            .fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .fitnessGlassSurface(RoundedCornerShape(corner), FitnessGlassLevel.BAR)
            // Taps on the capsule rim never reach the content underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .animateContentSize(tween(FitnessUiTokens.REST_TIMER_SHAPE_MILLIS))
            .testTag("rest-timer-capsule")
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = FitnessUiTokens.REST_TIMER_MIN_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.weight(1f).heightIn(min = FitnessSpacing.touch)
                        .semantics { stateDescription = if (expanded) "휴식 타이머 펼쳐짐" else "휴식 타이머 접힘" }
                ) {
                    Text(if (expanded) "휴식 · 접기" else "휴식 · 펼치기")
                }
                if (!expanded) Text(timeLabel, style = tabularDigits)
                TextButton(onClick = onSkip) { Text("건너뛰기") }
            }
            if (expanded) {
                Text(
                    timeLabel,
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum")
                )
                Text(
                    "호흡을 정리하고 다음 세트를 준비하세요.",
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = FitnessSpacing.small),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        // The progress line runs along the capsule's inner bottom edge.
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(FitnessUiTokens.REST_TIMER_PROGRESS_HEIGHT_DP.dp)
        )
    }
}
