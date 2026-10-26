package com.yeonsik.fitnessapp.feature.statistics.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessLoadingTiming
import com.yeonsik.fitnessapp.core.ui.LocalFitnessLoadingTiming
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.statistics.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Fixture-only statistics: no database writes or real record edits. */
class StatisticsUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf<StatisticsUiState>(StatisticsUiState.Ready(snapshot()))
    private val owner = mutableStateOf("owner")
    private val unit = mutableStateOf(MassUnit.KG)
    private val dark = mutableStateOf(true)
    private val scale = mutableStateOf(1f)
    private val width = mutableStateOf(375.dp)
    private val calls = mutableListOf<String>()
    private val actions = object : StatisticsScreenActions {
        override fun selectPeriod(period: StatisticsPeriod) { calls += period.label }
        override fun retry() { calls += "retry" }
    }

    @Test fun pendingPeriodAndRetryRemainSelectedAndOwnerChangeHidesOldData() {
        state.value = StatisticsUiState.Loading("owner", TODAY, StatisticsPeriod.NINETY_DAYS)
        show()
        compose.onNodeWithTag("statistics-period-90").assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("statistics-period-7").performClick()
        compose.runOnIdle { state.value = StatisticsUiState.Error("owner", TODAY, StatisticsPeriod.SEVEN_DAYS, "private diagnostics") }
        compose.onNodeWithTag("statistics-period-7").assertIsSelected()
        compose.onNodeWithText("private diagnostics").assertDoesNotExist()
        compose.onNodeWithTag("statistics-retry").performClick()
        assertEquals(listOf("7일", "retry"), calls)
        compose.runOnIdle { state.value = StatisticsUiState.Ready(snapshot()) }
        compose.onNodeWithTag("statistics-overview-운동 횟수", true).assertTextEquals("9회")
        compose.runOnIdle { owner.value = "other" }
        compose.onNodeWithTag("statistics-overview-운동 횟수", true).assertDoesNotExist()
        compose.onNodeWithTag("statistics-values-volume").assertDoesNotExist()
    }

    @Test fun comparisonKeepsValuesDaysAndMassUnitAndResetsAcrossPeriods() {
        show()
        compose.onNodeWithTag("statistics-comparison").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-difference-운동 기록 일수", true).performScrollTo().assertTextEquals("-5일")
        compose.onNodeWithTag("statistics-difference-식단 기록 일수", true).performScrollTo().assertTextEquals("+4일")
        compose.runOnIdle { unit.value = MassUnit.LB }
        compose.onNodeWithTag("statistics-overview-운동 볼륨", true).assertTextEquals(MassFormatter.withUnit(107278.0, MassUnit.LB))
        compose.runOnIdle { state.value = StatisticsUiState.Ready(snapshot(StatisticsPeriod.SEVEN_DAYS)) }
        compose.onNodeWithTag("statistics-difference-운동 기록 일수", true).assertDoesNotExist()
        compose.onNodeWithTag("statistics-period-7").assertIsSelected()
    }

    @Test fun chartHasAccessiblePointSelectionAndAnExplicitMissingValueList() {
        show()
        val chart = compose.onNodeWithTag("statistics-chart-volume").performScrollTo()
        val pointActions = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(3, pointActions.size)
        compose.runOnIdle { assertTrue(pointActions.first().action()) }
        compose.onNodeWithTag("statistics-selected-volume", true).assertTextContains("2026-09-22 ~ 2026-09-27 · 일부 주", substring = true)
        compose.onNodeWithTag("statistics-values-volume").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("2026-10-19 ~ 2026-10-21 · 일부 주", true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("미기록", substring = false).assertCountEquals(2)
        capture("statistics-values")
    }

    @Test fun exerciseRowsExpandOneChartAndScopeChangeClearsDetails() {
        show()
        compose.onNodeWithTag("statistics-exercise-leg").performScrollTo().assertHeightIsAtLeast(88.dp).performClick()
        compose.onNodeWithTag("statistics-chart-exercise-leg").assertExists()
        compose.onNodeWithTag("statistics-exercise-bench").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-chart-exercise-leg").assertDoesNotExist()
        compose.onNodeWithTag("statistics-chart-exercise-bench").assertExists()
        compose.runOnIdle { owner.value = "other" }
        compose.onNodeWithTag("statistics-chart-exercise-bench").assertDoesNotExist()
    }

    @Test fun emptyAndSingleObservationNeverShowFabricatedTrend() {
        val empty = snapshot().copy(workoutVolumeTrend = emptyList(), workoutFrequencyTrend = emptyList(),
            bodyWeightTrend = listOf(point("2026-10-21", 70.0)), exercisePerformanceTrends = emptyList(),
            sufficiency = StatisticsDataSufficiency(30, 0, 0, 1, 0))
        state.value = StatisticsUiState.Ready(empty)
        show()
        compose.onNodeWithTag("statistics-chart-weight").assertDoesNotExist()
        compose.onNodeWithText("기록 1개 · 추세는 기록이 더 쌓이면 표시됩니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("statistics-values-weight").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-value-weight-0", true).assertExists()
    }

    @Test fun bothThemesAndLargeTextKeepSummaryChartsAndDetailsReadable() {
        show()
        capture("statistics-standard")
        listOf(false, true).forEach { mode ->
            listOf(1f, 2f).forEach { fontScale ->
                compose.runOnIdle { dark.value = mode; scale.value = fontScale; width.value = 320.dp }
                compose.onNodeWithTag("statistics-period-30").performScrollTo().assertHeightIsAtLeast(48.dp)
                capture("statistics-top-$mode-$fontScale")
                assertTextFits("statistics-overview-운동 볼륨")
                compose.onNodeWithTag("statistics-chart-volume").performScrollTo().assertIsDisplayed()
                capture("statistics-chart-$mode-$fontScale")
                compose.onNodeWithTag("statistics-exercise-leg").performScrollTo().assertIsDisplayed()
                assertTextFits("statistics-exercise-title-leg")
                compose.onNodeWithTag("statistics-basis").performScrollTo().assertHeightIsAtLeast(48.dp)
                assertAllTextFits()
            }
        }
    }

    @Test fun actualSystemFontScaleAllowsComparisonAndChartTable() {
        show(useSystemScale = true)
        compose.onNodeWithTag("statistics-comparison").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-difference-운동 기록 일수", true).performScrollTo().assertTextEquals("-5일")
        compose.onNodeWithTag("statistics-values-volume").performScrollTo().performClick()
        compose.onNodeWithText("2026-10-19 ~ 2026-10-21 · 일부 주", true).performScrollTo().assertIsDisplayed()
        capture("statistics-system-font")
    }

    private fun show(useSystemScale: Boolean = false) {
        compose.setContent {
            FitnessComposeTheme(dark.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalFitnessLoadingTiming provides FitnessLoadingTiming(0, 0),
                    LocalDensity provides Density(density.density, if (useSystemScale) density.fontScale else scale.value)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(width.value).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatisticsScreen(state.value, owner.value, unit.value, actions)
                        }
                    }
                }
            }
        }
    }

    private fun assertTextFits(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        assertFalse("$tag clips", text.multiParagraph.height > text.size.height + 1f ||
            (0 until text.lineCount).any { text.isLineEllipsized(it) || text.getLineLeft(it) < -1f || text.getLineRight(it) > text.size.width + 1f })
    }

    private fun assertAllTextFits() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.forEach { text ->
                    assertFalse("Text clips at ${scale.value}: ${text.layoutInput.text}", text.multiParagraph.height > text.size.height + 1f ||
                        (0 until text.lineCount).any { text.isLineEllipsized(it) || text.getLineLeft(it) < -1f || text.getLineRight(it) > text.size.width + 1f })
                }
            }
        }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("statisticsVisualQa") != "true") return
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "statistics-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun point(date: String, value: Double?) = StatisticsTrendPoint(date.substring(5), value, date)
    private fun snapshot(period: StatisticsPeriod = StatisticsPeriod.THIRTY_DAYS): StatisticsSnapshot {
        val weekly = listOf(point("2026-09-21", 27000.0), point("2026-09-28", 21778.0), point("2026-10-05", 58500.0), point("2026-10-12", null), point("2026-10-19", null))
        val daily = listOf(point("2026-09-26", 390.0), point("2026-09-27", 400.0), point("2026-10-09", 420.0))
        return StatisticsSnapshot("owner", TODAY, StatisticsPeriodWindow.around(LocalDate.parse(TODAY), period), weekly,
            weekly.map { it.copy(value = it.value?.let { 3.0 }) }, listOf(
                StatisticsPerformanceTrend("leg", "아주 긴 이름의 레그 프레스 운동", StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX, daily),
                StatisticsPerformanceTrend("bench", "벤치 프레스", StatisticsPerformanceMetric.SESSION_VOLUME, daily)), daily,
            listOf(StatisticsComparisonMetric("운동 횟수", 9.0, 16.0, StatisticsValueKind.COUNT),
                StatisticsComparisonMetric("운동 기록 일수", 9.0, 14.0, StatisticsValueKind.COUNT),
                StatisticsComparisonMetric("운동 볼륨", 107278.0, 87609.0, StatisticsValueKind.MASS_KG),
                StatisticsComparisonMetric("평균 체중", 91.0, 89.9, StatisticsValueKind.MASS_KG),
                StatisticsComparisonMetric("식단 기록 일수", 11.0, 7.0, StatisticsValueKind.COUNT)), listOf("운동 기록 9일 · 세션 9회 / 30일"),
            StatisticsDataSufficiency(period.days, 9, 9, 15, 11))
    }

    private companion object { const val TODAY = "2026-10-21" }
}
