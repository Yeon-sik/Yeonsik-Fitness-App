package com.yeonsik.fitnessapp.feature.home.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessRecordMarkerColors
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.feature.records.ui.RecordsCalendarLegend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeTodayHeroUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyHeroHasThreeHollowMarkersAndAZeroProgressBar() {
        showHero()
        compose.onNodeWithText("오늘").assertExists()
        compose.onAllNodesWithText("아직", useUnmergedTree = true).assertCountEquals(3)
        compose.onNodeWithContentDescription("오늘 0/3 영역 기록").assertExists()
        listOf("workout", "meal", "body").forEach { key ->
            compose.onNodeWithTag("home-hero-marker-$key", useUnmergedTree = true)
                .assertStateDescriptionEquals("미기록")
        }
    }

    @Test fun mealOnlyFillsItsSharedYellowMarkerAndOneProgressSegment() {
        showHero(HomeTodayHeroStatus(listOf(domain("workout", "운동", "아직"),
            domain("meal", "식단", "2회", recorded = true), domain("body", "체중", "아직")), false))
        compose.onNodeWithText("2회").assertExists()
        compose.onNodeWithTag("home-hero-marker-meal", useUnmergedTree = true).assertStateDescriptionEquals("기록 완료")
        compose.onNodeWithTag("home-hero-marker-workout", useUnmergedTree = true).assertStateDescriptionEquals("미기록")
        assertPixelColor("home-hero-marker-meal", FitnessRecordMarkerColors.byKey.getValue("meal"))
        assertPixelColor("home-hero-segment-meal", FitnessRecordMarkerColors.byKey.getValue("meal"))
        compose.onNodeWithContentDescription("오늘 1/3 영역 기록").assertExists()
    }

    @Test fun weightAndWorkoutMarkersAndSegmentsUseTheExactRecordsCalendarColors() {
        val status = HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "등 · 이두", true),
            domain("meal", "식단", "아직"),
            domain("body", "체중", "88.4 kg", true)
        ), false)
        compose.setContent {
            FitnessComposeTheme(false) {
                Column {
                    HomeTodayHero(status, {})
                    RecordsCalendarLegend(Modifier.fillMaxWidth())
                }
            }
        }
        compose.waitForIdle()
        listOf("workout", "body").forEach { key ->
            compose.onNodeWithTag("home-hero-marker-$key", useUnmergedTree = true).assertStateDescriptionEquals("기록 완료")
            assertPixelColor("home-hero-marker-$key", FitnessRecordMarkerColors.byKey.getValue(key))
            assertPixelColor("home-hero-segment-$key", FitnessRecordMarkerColors.byKey.getValue(key))
        }
        listOf("workout", "meal", "body").forEach { key ->
            assertPixelColor("records-legend-$key", FitnessRecordMarkerColors.byKey.getValue(key))
        }
    }

    @Test fun narrowHeroWithLargeTextKeepsThreeEqualColumnsAndAtMostTwoWorkoutLines() {
        val mode = mutableStateOf(false)
        val muscles = listOf("광배근", "대흉근", "이두근", "삼두근", "대퇴사두근")
        val status = HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "${muscles.take(2).joinToString(" · ")} 외 3", true,
                detail = "유산소 123분", accessibility = "광배근 · 대흉근 · 이두근 · 삼두근 · 대퇴사두근 · 유산소 123분"),
            domain("meal", "식단", "12회", true), domain("body", "체중", "123.4 kg", true)
        ), false)
        compose.setContent {
            FitnessComposeTheme(mode.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Box(Modifier.width(280.dp).testTag("hero-narrow")) {
                        HomeTodayHero(status, {})
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { mode.value = dark }
            compose.waitForIdle()
            val root = compose.onNodeWithTag("hero-narrow").fetchSemanticsNode().boundsInRoot
            val domains = listOf("workout", "meal", "body").map {
                compose.onNodeWithTag("home-hero-domain-$it", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
            }
            domains.forEach { assertTrue(it.left >= root.left - 1f && it.right <= root.right + 1f) }
            assertEquals(domains[0].width, domains[1].width, 1f)
            assertEquals(domains[1].width, domains[2].width, 1f)
            compose.onNodeWithText("오늘").assertExists()
            compose.onNodeWithTag("home-hero-value-workout", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("home-hero-detail-workout", useUnmergedTree = true).assertExists()
            captureHero("${if (dark) "dark" else "light"}-narrow-280-font-1_8")
        }
    }

    @Test fun progressUsesTheRecordsActivityColorAndAnimatesToItsSettledState() {
        val changingStatus = mutableStateOf(HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "아직"), domain("meal", "식단", "아직"),
            domain("body", "체중", "아직")
        ), false))
        val mealRecordColor = FitnessRecordMarkerColors.byKey.getValue("meal")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(primary = Color(0xFF8855DD))) {
                    HomeTodayHero(changingStatus.value, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertNotEquals(mealRecordColor, pixelColor("home-hero-segment-meal"))
        compose.runOnIdle {
            changingStatus.value = HomeTodayHeroStatus(listOf(
                domain("workout", "운동", "아직"), domain("meal", "식단", "2회", true),
                domain("body", "체중", "아직")
            ), false)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        assertColorNear(mealRecordColor, pixelColor("home-hero-segment-meal"))
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertColorNear(mealRecordColor, pixelColor("home-hero-segment-meal"))
        compose.mainClock.autoAdvance = true
    }

    @Test fun activeWorkoutKeepsTheContinueActionAndFinishedProjectionVisible() {
        var continued = 0
        val status = HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "진행 중", true, "등 · 이두"),
            domain("meal", "식단", "아직"), domain("body", "체중", "아직")
        ), true)
        compose.setContent {
            FitnessComposeTheme(false) { HomeTodayHero(status, { continued++ }) }
        }
        compose.onNodeWithText("오늘").assertExists()
        compose.onNodeWithText("진행 중").assertExists()
        compose.onNodeWithText("등 · 이두").assertExists()
        compose.onNodeWithText("운동 이어가기").performClick()
        compose.runOnIdle { assertEquals(1, continued) }
        compose.onNodeWithContentDescription("오늘 1/3 영역 기록").assertExists()
    }

    @Test fun heroTextActionAndRecordedSegmentsStayDistinctInBothColorSchemes() {
        val mode = mutableStateOf(false)
        val active = HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "진행 중", true, "등 · 이두"),
            domain("meal", "식단", "아직"), domain("body", "체중", "아직")
        ), true)
        val status = mutableStateOf(active)
        compose.setContent {
            FitnessComposeTheme(mode.value) {
                Box(Modifier.width(360.dp)) {
                    HomeTodayHero(status.value, {})
                }
            }
        }
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { mode.value = dark; status.value = active }
            compose.waitForIdle()
            compose.onNodeWithText("오늘").assertExists()
            compose.onNodeWithText("진행 중").assertExists()
            val button = compose.onNodeWithText("운동 이어가기").assertHasClickAction()
            val pixels = compose.onNodeWithTag("home-today-hero").captureToImage().toPixelMap()
            // Sample inside the surface, clear of its border, title and divider.
            val surface = pixels[pixels.width / 2, (pixels.height * 0.025f).toInt().coerceAtLeast(2)]
            assertColorNear(Color(FitnessUiTokens.COLOR_BRAND_BLUE), surface)
            val buttonPixels = button.captureToImage().toPixelMap()
            val action = buttonPixels[buttonPixels.width / 2, buttonPixels.height / 5]
            val recorded = pixelColor("home-hero-segment-workout")
            val unrecorded = pixelColor("home-hero-segment-meal")
            assertTrue("Continue button must not blend into Hero ($dark)", contrast(action, surface) >= 1.5f)
            assertTrue("Recorded segment must not blend into Hero ($dark)", contrast(recorded, surface) >= 1.5f)
            assertTrue("Recorded and empty segments must remain distinct ($dark)", contrast(recorded, unrecorded) >= 1.25f)
            assertTrue("Hero title must remain legible ($dark)",
                strongestTextContrast(compose.onNodeWithTag("home-hero-title"), surface) >= 4.5f)
            assertTrue("Continue label must remain legible ($dark)",
                strongestTextContrast(button, action) >= 4.5f)
            if (visualCaptureEnabled()) {
                val scenarios = listOf(
                    "empty" to HomeTodayHeroStatus(listOf(
                        domain("workout", "운동", "아직"), domain("meal", "식단", "아직"),
                        domain("body", "체중", "아직")), false),
                    "partial" to HomeTodayHeroStatus(listOf(
                        domain("workout", "운동", "아직"), domain("meal", "식단", "2회", true),
                        domain("body", "체중", "아직")), false),
                    "complete" to HomeTodayHeroStatus(listOf(
                        domain("workout", "운동", "등 · 이두", true, "유산소 30분"),
                        domain("meal", "식단", "3회", true), domain("body", "체중", "88.4 kg", true)), false),
                    "in-progress" to active
                )
                scenarios.forEach { (name, snapshot) ->
                    compose.runOnIdle { status.value = snapshot }
                    compose.waitForIdle()
                    captureHero("${if (dark) "dark" else "light"}-$name-360")
                }
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }

    private fun strongestTextContrast(node: androidx.compose.ui.test.SemanticsNodeInteraction, background: Color): Float {
        val pixels = node.captureToImage().toPixelMap()
        var strongest = 1f
        // The central text area excludes rounded corners and the surrounding Hero surface.
        for (y in pixels.height / 3 until pixels.height * 2 / 3) {
            for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                strongest = maxOf(strongest, contrast(pixels[x, y], background))
            }
        }
        return strongest
    }

    private fun visualCaptureEnabled() =
        InstrumentationRegistry.getArguments().getString("heroVisualQa") == "true"

    private fun captureHero(name: String) {
        if (!visualCaptureEnabled()) return
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Hero visual captures require a disposable emulator"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "hero-qa").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("home-today-hero").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun showHero(status: HomeTodayHeroStatus = HomeTodayHeroStatus(listOf(
        domain("workout", "운동", "아직"), domain("meal", "식단", "아직"), domain("body", "체중", "아직")
    ), false)) {
        compose.setContent { FitnessComposeTheme(false) { HomeTodayHero(status, {}) } }
        compose.waitForIdle()
    }

    private fun domain(key: String, label: String, value: String, recorded: Boolean = false,
        detail: String? = null, accessibility: String = value) =
        HomeHeroDomainStatus(key, label, value, detail, recorded, accessibility)

    private fun assertPixelColor(tag: String, expected: Color) = assertColorNear(expected, pixelColor(tag))

    private fun pixelColor(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
        assertEquals(expected.alpha, actual.alpha, 0.01f)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertStateDescriptionEquals(value: String) {
        org.junit.Assert.assertEquals(value, fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
    }
}
