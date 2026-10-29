package com.yeonsik.fitnessapp.feature.home.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Read-only fixtures. No account, workout or record mutations. */
class HomeUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val home = mutableStateOf<HomeUiState>(HomeUiState.Ready(snapshot()))
    private val history = mutableStateOf<HomeActivityUiState>(activity())
    private val details = mutableStateOf<HomeActivityDayDetailsUiState>(HomeActivityDayDetailsUiState.Idle)
    private val owner = mutableStateOf("owner")
    private val dark = mutableStateOf(true)
    private val scale = mutableStateOf(1f)
    private val calls = mutableListOf<String>()
    private val actions = object : HomeScreenActions {
        override fun continueWorkout() { calls += "continue" }
        override fun navigate(screen: FitnessScreen) { calls += screen.name }
        override fun showBodyMetric() { calls += "weight" }
        override fun openMealManagement(date: String, returnScreen: FitnessScreen) {
            calls += "meal:$date:$returnScreen"
        }
    }

    @Test fun matchingErrorCanRetryAndKeepsRecordingDestinationsAvailable() {
        home.value = HomeUiState.Error("owner", "private diagnostics", TODAY)
        show()
        compose.onNodeWithTag("home-status-error").assertIsDisplayed()
        compose.onNodeWithText("private diagnostics").assertDoesNotExist()
        compose.onNodeWithText("오늘 상태를 불러오는 중입니다.").assertDoesNotExist()
        compose.onNodeWithTag("home-status-retry").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("home-quick-meal").performScrollTo().performClick()
        compose.onNodeWithTag("home-quick-weight").performScrollTo().performClick()
        assertEquals(listOf("retry", "meal:$TODAY:HOME", "weight"), calls)
        compose.runOnIdle { home.value = HomeUiState.Error("previous-owner", "stale", TODAY) }
        compose.onNodeWithTag("home-status-retry").assertDoesNotExist()
        compose.runOnIdle { home.value = HomeUiState.Error("owner", "stale", "2026-09-30") }
        compose.onNodeWithTag("home-status-retry").assertDoesNotExist()
        compose.onNodeWithText("오늘 상태를 불러오는 중입니다.").assertExists()
    }

    @Test fun summaryExcludesFutureAndBeforeTrackingAndDateListOpensTheSamePreview() {
        show()
        compose.onNodeWithTag("home-activity-recorded-days", useUnmergedTree = true).performScrollTo()
            .assertTextEquals("기록한 날 2일")
        compose.onNodeWithText("이 기간 집계 4일 중").assertExists()
        compose.onNodeWithTag("home-activity-date-list").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("home-activity-date-option-2026-09-27").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-date-option-2026-10-02").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-date-option-2026-09-30")
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("home-activity-preview-2026-09-30").assertExists()
        compose.onNodeWithTag("home-activity-open-records")
            .assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("select:2026-09-30", "records:2026-09-30"), calls)
    }

    @Test fun dateListAndPreviewDoNotLeakAcrossOwnerOrLoadingChanges() {
        show()
        compose.onNodeWithTag("home-activity-date-list").performScrollTo().performClick()
        compose.onNodeWithText("날짜 선택").assertExists()
        compose.runOnIdle {
            history.value = HomeActivityUiState.Loading(activity().identity, previous = activity())
        }
        compose.onNodeWithText("날짜 선택").assertDoesNotExist()
        compose.runOnIdle { history.value = activity() }
        compose.onNodeWithText("날짜 선택").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-date-list").performScrollTo().performClick()
        compose.runOnIdle {
            owner.value = "next-owner"
            home.value = HomeUiState.Ready(snapshot().copy(ownerId = "next-owner"))
        }
        compose.onNodeWithText("날짜 선택").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-preview-2026-09-30").assertDoesNotExist()
        assertTrue(calls.isEmpty())
    }

    @Test fun largeTextKeepsMetricsAndQuickActionsReadableInBothThemes() {
        show()
        listOf(false, true).forEach { mode ->
            listOf(1f, 1.3f, 2f).forEach { fontScale ->
                compose.runOnIdle { dark.value = mode; scale.value = fontScale }
                compose.onNodeWithTag("home-hero-title").performScrollTo()
                listOf("workout", "meal", "body").forEach { domain ->
                    assertNoTextOverflow("home-hero-value-$domain")
                    assertNoTextOverflow("home-hero-criterion-$domain")
                }
                capture("home-$mode-$fontScale")
                listOf("strength", "cardio", "meal", "weight").forEach { action ->
                    compose.onNodeWithTag("home-quick-$action").performScrollTo()
                        .assertHeightIsAtLeast(48.dp).performClick()
                }
                compose.onNodeWithText("운동 이어가기").assertDoesNotExist()
                compose.onNodeWithTag("home-activity-period").performScrollTo()
                    .assertHeightIsAtLeast(48.dp)
                compose.onNodeWithTag("home-activity-legend-meaning").performScrollTo()
                assertNoTextOverflow("home-activity-legend-meaning")
                capture("history-$mode-$fontScale")
            }
        }
        assertEquals(6, calls.count { it == "STRENGTH" })
        assertEquals(6, calls.count { it == "CARDIO" })
        assertEquals(6, calls.count { it == "weight" })
    }

    @Test fun systemFontScaleKeepsDateSelectionAndPreviewCloseReachable() {
        show(useSystemScale = true)
        listOf(false, true).forEach { mode ->
            compose.runOnIdle { dark.value = mode }
            compose.onNodeWithTag("home-activity-date-list").performScrollTo().performClick()
            capture("system-dates-$mode", dialog = true)
            compose.onNodeWithTag("home-activity-date-option-2026-09-30").performScrollTo().performClick()
            compose.onNodeWithTag("home-activity-preview-close").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
            compose.onNodeWithTag("home-activity-open-records").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
            val scroll = compose.onNodeWithTag("home-activity-preview-scroll").fetchSemanticsNode().boundsInRoot
            assertTrue(scroll.height <= with(compose.density) { 180.dp.toPx() } + 1)
            capture("system-preview-$mode", popup = true)
            compose.onNodeWithTag("home-activity-preview-close").performClick()
            compose.onNodeWithTag("home-activity-preview-2026-09-30").assertDoesNotExist()
        }
        assertEquals(listOf("select:2026-09-30", "select:2026-09-30"), calls)
    }

    private fun show(useSystemScale: Boolean = false) {
        compose.setContent {
            FitnessComposeTheme(dark.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,
                    if (useSystemScale) density.fontScale else scale.value)) {
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).testTag("home-fixture")) {
                        HomeDestination(home.value, owner.value, TODAY, actions,
                            activityState = history.value, activityDayDetails = details.value,
                            onRetry = { calls += "retry" },
                            onActivityDateSelected = { date ->
                                calls += "select:$date"
                                details.value = HomeActivityDayDetailsUiState.Ready(owner.value,
                                    HomeActivityDayDetails(date, listOf(
                                        HomeActivityRecordSummary(HomeActivityKind.EXERCISE,
                                            category = List(8) { "등 · 가슴 · 어깨" }.joinToString(" · "),
                                            workoutType = "strength"),
                                        HomeActivityRecordSummary(HomeActivityKind.MEAL, name = "식사"),
                                        HomeActivityRecordSummary(HomeActivityKind.WEIGHT, weightKg = 80.5)
                                    )))
                            },
                            onActivityOpenRecords = { calls += "records:$it" })
                    }
                }
            }
        }
    }

    private fun assertNoTextOverflow(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        // Text measures in fractional pixels; the final layout rounds to whole pixels.
        val clipped = text.didOverflowHeight || text.multiParagraph.width > text.size.width + 1f ||
            (0 until text.lineCount).any { text.isLineEllipsized(it) }
        assertFalse("$tag must show every character (scale=${scale.value}, size=${text.size}, " +
            "width=${text.didOverflowWidth}, height=${text.didOverflowHeight}, lines=${text.lineCount})",
            clipped)
    }

    private fun capture(name: String, dialog: Boolean = false, popup: Boolean = false) {
        if (InstrumentationRegistry.getArguments().getString("homeVisualQa") != "true") return
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val target = when {
            dialog -> compose.onNode(isDialog())
            popup -> compose.onNodeWithTag("home-activity-preview-2026-09-30")
            else -> compose.onRoot()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use {
            target.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun snapshot() = HomeSnapshot("owner", TODAY, emptyList(), null, emptyList(), emptyMap(),
        emptyMap(), "active", emptyMap(), emptyMap(), emptyMap(), null,
        HomeBodyMetric("weight", TODAY, 123.4, ""), emptyList(), emptyList(),
        todayWorkoutStatus = HomeTodayWorkoutStatus(true, true, listOf("등", "가슴"), true, 1_920, 1_250.5))

    private fun activity(): HomeActivityUiState.Ready {
        val window = HomeActivityWindowPolicy.window(LocalDate.parse(TODAY), LocalDate.parse("2026-09-28"), 0)
        return HomeActivityUiState.Ready(HomeActivityRequestIdentity("owner", TODAY, 0), window,
            window.cells(mapOf("2026-09-29" to setOf(HomeActivityKind.EXERCISE),
                "2026-09-30" to HomeActivityKind.entries.toSet())))
    }

    private companion object { const val TODAY = "2026-10-01" }
}
