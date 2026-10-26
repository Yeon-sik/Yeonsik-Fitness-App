package com.yeonsik.fitnessapp.feature.workout.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Pure UI fixtures; never create, complete or modify a real workout. */
class WorkoutOverviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf<HomeUiState>(HomeUiState.Ready(snapshot()))
    private val owner = mutableStateOf("owner")
    private val cardio = mutableStateOf<Boolean?>(false)
    private val dark = mutableStateOf(true)
    private val scale = mutableStateOf(1f)
    private val width = mutableStateOf(375.dp)
    private val unit = mutableStateOf(MassUnit.KG)
    private val calls = mutableListOf<String>()
    private val actions = object : WorkoutOverviewActions {
        override fun continueWorkout() { calls += "continue" }
        override fun navigate(screen: FitnessScreen) { calls += screen.name }
        override fun showBodyMetric() { calls += "weight" }
        override fun openMeals() { calls += "meal" }
        override fun retryOverview() { calls += "retry" }
    }

    @Test fun fourDestinationsKeepTheirActionsAndSummaryUsesOriginalValuesAndUnit() {
        show()
        listOf("strength", "cardio", "weight", "meal").forEach { name ->
            compose.onNodeWithTag("fitness-action-$name").performScrollTo()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
        }
        assertEquals(listOf("STRENGTH", "CARDIO", "weight", "meal"), calls)
        compose.onNodeWithTag("fitness-value-volume", true).assertTextEquals(MassFormatter.withUnit(12345.6, MassUnit.KG))
        compose.onNodeWithTag("fitness-value-meals", true).assertTextEquals("3끼")
        compose.runOnIdle { unit.value = MassUnit.LB }
        compose.onNodeWithTag("fitness-value-volume", true).assertTextEquals(MassFormatter.withUnit(12345.6, MassUnit.LB))
        compose.onNodeWithTag("fitness-action-resume").assertDoesNotExist()
    }

    @Test fun inProgressStrengthAndCardioResumeWithoutStartingAnotherWorkout() {
        state.value = HomeUiState.Ready(snapshot().copy(inProgressSessionId = "active"))
        show()
        compose.onNodeWithTag("fitness-action-strength").assertHeightIsAtLeast(96.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "진행 중"))
            .performClick()
        compose.onNodeWithTag("fitness-action-cardio").performClick()
        compose.runOnIdle { cardio.value = true }
        compose.onNodeWithTag("fitness-action-cardio").assertHeightIsAtLeast(96.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "진행 중"))
            .performClick()
        compose.onNodeWithTag("fitness-action-strength").performClick()
        assertEquals(listOf("continue", "CARDIO", "continue", "STRENGTH"), calls)
    }

    @Test fun unresolvedTypeCanResumeAndDoesNotLeakAfterOwnerOrDateChange() {
        state.value = HomeUiState.Ready(snapshot().copy(inProgressSessionId = "active"))
        cardio.value = null
        show()
        compose.onNodeWithTag("fitness-action-resume").performClick()
        assertEquals(listOf("continue"), calls)
        compose.runOnIdle { owner.value = "new-owner" }
        compose.onNodeWithTag("fitness-action-resume").assertDoesNotExist()
        compose.onNodeWithTag("fitness-value-volume", true).assertDoesNotExist()
        compose.runOnIdle {
            owner.value = "owner"
            state.value = HomeUiState.Ready(snapshot().copy(today = "2026-10-20", inProgressSessionId = "stale"))
        }
        compose.onNodeWithTag("fitness-action-resume").assertDoesNotExist()
        compose.onNodeWithTag("fitness-value-meals", true).assertDoesNotExist()
    }

    @Test fun loadingAndMatchingErrorKeepEntriesAvailableWithoutShowingFalseZero() {
        state.value = HomeUiState.Loading
        show()
        compose.onNodeWithTag("fitness-summary-status", true).assertContentDescriptionEquals("오늘 기록을 불러오는 중")
        compose.onNodeWithTag("fitness-value-volume", true).assertDoesNotExist()
        compose.onNodeWithTag("fitness-action-weight").performScrollTo().performClick()
        compose.runOnIdle { state.value = HomeUiState.Error("owner", "private diagnostics", TODAY) }
        compose.onNodeWithText("private diagnostics").assertDoesNotExist()
        compose.onNodeWithTag("fitness-summary-retry").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("fitness-action-meal").performScrollTo().performClick()
        assertEquals(listOf("weight", "retry", "meal"), calls)
        compose.runOnIdle { state.value = HomeUiState.Error("previous-owner", "stale", TODAY) }
        compose.onNodeWithTag("fitness-summary-retry").assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUiState.Error("owner", "stale", "2026-10-20") }
        compose.onNodeWithTag("fitness-summary-retry").assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUiState.Ready(snapshot().copy(dayMetrics = emptyMap(), mealCounts = emptyMap())) }
        compose.onNodeWithTag("fitness-value-volume", true).assertTextEquals("0kg")
        compose.onNodeWithTag("fitness-value-meals", true).assertTextEquals("0끼")
    }

    @Test fun activeActionKeepsBrandBlueAndOtherActionsUseExistingSurfaceInBothThemes() {
        state.value = HomeUiState.Ready(snapshot().copy(inProgressSessionId = "active"))
        show()
        listOf(false, true).forEach { mode ->
            compose.runOnIdle { dark.value = mode }
            assertFill("fitness-action-strength", Color(FitnessUiTokens.COLOR_BRAND_BLUE))
            assertFill("fitness-action-cardio", Color(if (mode) FitnessUiTokens.COLOR_D_SURFACE else FitnessUiTokens.COLOR_SURFACE))
            capture("fitness-theme-$mode")
        }
    }

    @Test fun narrowScreenAndLargeTextKeepEveryLabelAndAllActionsReachable() {
        state.value = HomeUiState.Ready(snapshot().copy(inProgressSessionId = "active"))
        show()
        listOf(false, true).forEach { mode ->
            listOf(1f, 2f).forEach { fontScale ->
                compose.runOnIdle { dark.value = mode; scale.value = fontScale; width.value = 320.dp }
                capture("fitness-narrow-before-$mode-$fontScale")
                listOf("strength", "cardio", "weight", "meal").forEach { name ->
                    compose.onNodeWithTag("fitness-action-$name").performScrollTo()
                        .assertHeightIsAtLeast(48.dp).assertIsDisplayed()
                    assertNoTextOverflow("fitness-title-$name")
                    assertNoTextOverflow("fitness-description-$name")
                }
                listOf("volume", "meals").forEach { name ->
                    compose.onNodeWithTag("fitness-value-$name", true).performScrollTo()
                    assertNoTextOverflow("fitness-value-$name")
                }
                compose.onNodeWithTag("fitness-action-strength").performScrollTo()
                capture("fitness-narrow-$mode-$fontScale")
            }
        }
    }

    @Test fun systemFontScaleAndReducedMotionAllowReentryAndRetry() {
        show(useSystemScale = true)
        compose.onNodeWithTag("fitness-action-cardio").performScrollTo().performClick()
        compose.runOnIdle { state.value = HomeUiState.Error("owner", "unavailable", TODAY) }
        compose.onNodeWithTag("fitness-summary-retry").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = HomeUiState.Ready(snapshot().copy(inProgressSessionId = "active")) }
        compose.onNodeWithTag("fitness-action-strength").performScrollTo().performClick()
        assertNoTextOverflow("fitness-title-strength")
        assertNoTextOverflow("fitness-description-strength")
        capture("fitness-system-font")
        assertEquals(listOf("CARDIO", "retry", "continue"), calls)
    }

    private fun show(useSystemScale: Boolean = false) {
        compose.setContent {
            FitnessComposeTheme(dark.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalFitnessLoadingTiming provides FitnessLoadingTiming(0, 0),
                    LocalDensity provides Density(density.density,
                    if (useSystemScale) density.fontScale else scale.value)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(width.value).verticalScroll(rememberScrollState())
                            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkoutOverview(state.value, owner.value, TODAY, unit.value, actions,
                                inProgressIsCardio = cardio.value)
                        }
                    }
                }
            }
        }
    }

    private fun assertNoTextOverflow(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        // Paragraph constraints can be wider than a short Text's measured width. Check glyph
        // lines, not the constraint width; allow one pixel for fractional text measurement.
        assertFalse("$tag clips at font scale ${scale.value}: size=${text.size}, " +
            "paragraph=${text.multiParagraph.width}x${text.multiParagraph.height}, " +
            "lines=${(0 until text.lineCount).map { text.getLineLeft(it) to text.getLineRight(it) }}",
            text.multiParagraph.height > text.size.height + 1f ||
                (0 until text.lineCount).any { text.isLineEllipsized(it) ||
                    text.getLineLeft(it) < -1f || text.getLineRight(it) > text.size.width + 1f })
    }

    private fun assertFill(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag).performScrollTo().captureToImage().toPixelMap()
        val actual = pixels[8, pixels.height / 2]
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("fitnessVisualQa") != "true") return
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "fitness-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun snapshot() = HomeSnapshot("owner", TODAY, emptyList(), null, emptyList(), emptyMap(),
        emptyMap(), null, mapOf(TODAY to HomeDayWorkoutMetrics(1, 9, 12345.6, 3600)),
        mapOf(TODAY to 3), emptyMap(), null, null, emptyList(), emptyList())

    private companion object { const val TODAY = "2026-10-21" }
}
