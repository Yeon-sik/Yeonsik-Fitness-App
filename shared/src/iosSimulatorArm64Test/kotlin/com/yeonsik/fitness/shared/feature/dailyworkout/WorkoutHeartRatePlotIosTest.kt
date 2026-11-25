package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class WorkoutHeartRatePlotIosTest {
    @Test
    fun tapsAndScrubsExactExtremes() = runComposeUiTest(testTimeout = 30.seconds) {
        val samples = listOf(93, 81, 108, 89, 94, 132, 157, 143, 119, 131).mapIndexed { index, bpm ->
            DailyHeartRateSample("test.watch", index * 1_000L, bpm.toDouble())
        }
        setContent {
            MaterialTheme {
                WorkoutHeartRatePlot(samples, null, null, Modifier.width(240.dp), showInstructions = false)
            }
        }
        onNodeWithText("막대를 누르거나", substring = true).assertDoesNotExist()
        val plot = onNodeWithContentDescription("High는 최고 구간", substring = true)
        plot.performTouchInput { click(Offset(width * 0.25f, center.y)) }
        plot.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "최저 81 bpm · 최고 108 bpm"))
        plot.performTouchInput { swipe(Offset(width * 0.25f, center.y), Offset(width * 0.75f, center.y)) }
        plot.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "최저 119 bpm · 최고 157 bpm"))
    }
}
