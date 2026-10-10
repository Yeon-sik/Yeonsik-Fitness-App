package com.yeonsik.fitnessapp.feature.home.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessRecordMarkerColors
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.feature.records.ui.RecordsCalendarLegend
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeTodayWorkoutStatus
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.data.NutritionProfile
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeTodayHeroUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completedVolumeAndWeightChangeKeepTheirOrderAndRequestedColorsInBothThemes() {
        val darkMode = mutableStateOf(false)
        val status = mutableStateOf(completedStatus(80.5))
        compose.setContent { FitnessComposeTheme(darkMode.value) { HomeTodayHero(status.value, {}) } }
        listOf(false, true).forEach { dark ->
            listOf(80.5, 79.5, 80.0).forEach { weight ->
                compose.runOnIdle { darkMode.value = dark; status.value = completedStatus(weight) }
                compose.waitForIdle()
                compose.onNodeWithText("1,250.5 kg", useUnmergedTree = true).assertIsDisplayed()
                val muscleBounds = compose.onNodeWithTag("home-hero-value-workout", true)
                    .fetchSemanticsNode().boundsInRoot
                val volumeBounds = compose.onNodeWithTag("home-hero-detail-workout", true)
                    .fetchSemanticsNode().boundsInRoot
                val cardioBounds = compose.onNodeWithTag("home-hero-additional-detail-workout", true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(muscleBounds.bottom <= volumeBounds.top)
                assertTrue(volumeBounds.bottom <= cardioBounds.top)
                assertProteinStaysInsideMealAndUsesTheVolumeFont()
                val currentProtein = textLayout("home-hero-protein-current")
                val currentText = currentProtein.layoutInput.text.text
                assertEquals("Protein amount and percentage must share a line",
                    currentProtein.getLineForOffset(currentText.indexOf("60g")),
                    currentProtein.getLineForOffset(currentText.indexOf("(60%)")))
                val delta = when (weight) { 80.5 -> "+0.5"; 79.5 -> "-0.5"; else -> "0" }
                compose.onNodeWithText("어제보다 $delta kg", useUnmergedTree = true).assertIsDisplayed()
                val detail = textLayout("home-hero-detail-body")
                assertFalse(detail.hasVisualOverflow)
                val expected = when (weight) {
                    80.5 -> HomeHeroWeightIncreaseColor
                    79.5 -> HomeHeroWeightDecreaseColor
                    else -> textLayout("home-hero-detail-workout").layoutInput.style.color
                }
                assertEquals(expected, detail.layoutInput.style.color)
                if (weight != 80.0) {
                    assertTrue("Weight change must remain legible on Hero", contrast(expected,
                        Color(FitnessUiTokens.COLOR_BRAND_BLUE)) >= 4.5f)
                    captureHero("${if (dark) "dark" else "light"}-completed-${if (weight > 80.0) "gain" else "loss"}")
                }
            }
        }
    }

    @Test fun missingYesterdayShowsAnExplicitLabelWithoutInventingAChange() {
        val source = heroSnapshot().copy(todayWeight = HomeBodyMetric("today", "2026-10-09", 80.5, ""))
        showHero(homeTodayHeroStatus(source))
        compose.onNodeWithText("어제 기록 없음", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun proteinProgressFillsOnlyItsFractionAndCompletesOnlyAtTheGoal() {
        val changing = mutableStateOf(proteinStatus(0.0))
        compose.mainClock.autoAdvance = false
        compose.setContent { FitnessComposeTheme(false) { HomeTodayHero(changing.value, {}) } }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { changing.value = proteinStatus(60.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        assertProteinStaysInsideMealAndUsesTheVolumeFont()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("현재 : 1끼, 60g\u00A0(60%)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("목표 단백질 : 100g", useUnmergedTree = true).assertIsDisplayed()
        val segment = compose.onNodeWithTag("home-hero-segment-meal", useUnmergedTree = true)
        assertEquals(0.6f, segment.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
        assertEquals(0.2f, compose.onNodeWithTag("home-hero-progress").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
        compose.onNodeWithTag("home-hero-marker-meal", useUnmergedTree = true)
            .assertStateDescriptionEquals("단백질 목표 달성률 60%")
        val pixels = segment.captureToImage().toPixelMap()
        val mealColor = FitnessRecordMarkerColors.byKey.getValue("meal")
        assertColorNear(mealColor, pixels[(pixels.width * 0.3f).toInt(), pixels.height / 2])
        assertNotEquals(mealColor, pixels[(pixels.width * 0.8f).toInt(), pixels.height / 2])
        captureHero("protein-current-60-percent")
        assertProteinStaysInsideMealAndUsesTheVolumeFont()
        compose.runOnIdle { changing.value = proteinStatus(120.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("현재 : 1끼, 120g\u00A0(120%)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home-hero-marker-meal", useUnmergedTree = true)
            .assertStateDescriptionEquals("단백질 목표 달성")
        assertEquals(1f, segment.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertPixelColor("home-hero-segment-meal", mealColor)
        compose.mainClock.autoAdvance = true
    }

    @Test fun emptyHeroHasThreeHollowMarkersAndAZeroProgressBar() {
        showHero()
        compose.onNodeWithText("오늘").assertExists()
        listOf("운동", "식단", "체중").forEach { label ->
            compose.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(1)
            compose.onNodeWithContentDescription("$label 기록 표시", useUnmergedTree = true).assertExists()
        }
        compose.onAllNodesWithText("아직", useUnmergedTree = true).assertCountEquals(3)
        compose.onNodeWithContentDescription("오늘 0/3 영역 완료").assertExists()
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
        compose.onNodeWithContentDescription("오늘 1/3 영역 완료").assertExists()
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

    @Test fun narrowHeroWithLargeTextKeepsThreeEqualColumnsAndNewDetailsVisible() {
        val mode = mutableStateOf(false)
        val muscles = listOf("광배근", "대흉근", "이두근", "삼두근", "대퇴사두근")
        val status = HomeTodayHeroStatus(listOf(
            domain("workout", "운동", "${muscles.take(2).joinToString(" · ")} 외 3", true,
                detail = "1,250.5 kg", accessibility = "광배근 · 대흉근 · 이두근 · 삼두근 · 대퇴사두근 · 총 운동량 1,250.5 kg · 유산소 123분")
                .copy(additionalDetail = "유산소 123분"),
            proteinStatus(60.0).domains.single { it.key == "meal" },
            domain("body", "체중", "123.4 kg", true, detail = "어제보다 +0.5 kg")
                .copy(detailTone = HomeHeroDetailTone.INCREASE)
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
            compose.onNodeWithText("현재 : 1끼, 60g\u00A0(60%)", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("목표 단백질 : 100g", useUnmergedTree = true).assertIsDisplayed()
            captureHero("${if (dark) "dark" else "light"}-narrow-280-font-1_8")
            assertProteinStaysInsideMealAndUsesTheVolumeFont()
            listOf("home-hero-detail-workout", "home-hero-additional-detail-workout", "home-hero-detail-body")
                .forEach { assertFalse("$it must show every character", textLayout(it).hasVisualOverflow) }
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

    @Test fun activeWorkoutKeepsItsStatusWithoutAContinueButton() {
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
        compose.onNodeWithText("운동 이어가기").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, continued) }
        compose.onNodeWithContentDescription("오늘 1/3 영역 완료").assertExists()
    }

    @Test fun heroTextAndRecordedSegmentsStayDistinctInBothColorSchemes() {
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
            compose.onNodeWithText("운동 이어가기").assertDoesNotExist()
            val pixels = compose.onNodeWithTag("home-today-hero").captureToImage().toPixelMap()
            // Sample inside the surface, clear of its border, title and divider.
            val surface = pixels[pixels.width / 2, (pixels.height * 0.025f).toInt().coerceAtLeast(2)]
            assertColorNear(Color(FitnessUiTokens.COLOR_BRAND_BLUE), surface)
            val recorded = pixelColor("home-hero-segment-workout")
            val unrecorded = pixelColor("home-hero-segment-meal")
            assertTrue("Recorded segment must not blend into Hero ($dark)", contrast(recorded, surface) >= 1.5f)
            assertTrue("Recorded and empty segments must remain distinct ($dark)", contrast(recorded, unrecorded) >= 1.25f)
            assertTrue("Hero title must remain legible ($dark)",
                strongestTextContrast(compose.onNodeWithTag("home-hero-title"), surface) >= 4.5f)
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

    private fun textLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun assertProteinStaysInsideMealAndUsesTheVolumeFont() {
        val meal = compose.onNodeWithTag("home-hero-domain-meal", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val workout = compose.onNodeWithTag("home-hero-domain-workout", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("home-hero-domain-body", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val targets = compose.onAllNodesWithTag("home-hero-protein-target", true).fetchSemanticsNodes()
        val currents = compose.onAllNodesWithTag("home-hero-protein-current", true).fetchSemanticsNodes()
        if (targets.size == 1 && currents.size == 1) {
            assertTrue("Protein target must appear before current intake",
                targets.single().boundsInRoot.bottom <= currents.single().boundsInRoot.top + 1f)
        }
        listOf("home-hero-protein-current", "home-hero-protein-target").forEach { tag ->
            val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$tag must be present", nodes.isNotEmpty())
            nodes.forEach { node ->
                val bounds = node.boundsInRoot
                assertTrue("$tag must stay inside the meal column horizontally",
                    bounds.left >= meal.left - 1f && bounds.right <= meal.right + 1f)
                assertTrue("$tag must stay inside the meal column vertically",
                    bounds.top >= meal.top - 1f && bounds.bottom <= meal.bottom + 1f)
                assertTrue("$tag must not occupy workout or body space",
                    bounds.left >= workout.right - 1f && bounds.right <= body.left + 1f)
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                val text = layouts.single()
                assertEquals("$tag must use the workout volume font size", 12.sp, text.layoutInput.style.fontSize)
                assertFalse("$tag must wrap without losing characters", text.hasVisualOverflow)
                val parentTag = generateSequence(node.parent) { it.parent }
                    .any { it.config.contains(SemanticsProperties.TestTag) &&
                        it.config[SemanticsProperties.TestTag] == "home-hero-domain-meal" }
                assertTrue("$tag must belong to the meal subtree", parentTag)
            }
        }
        val volume = compose.onAllNodesWithTag("home-hero-detail-workout", useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (volume.isNotEmpty()) {
            assertEquals(textLayout("home-hero-detail-workout").layoutInput.style.fontSize,
                textLayout("home-hero-protein-current").layoutInput.style.fontSize)
        }
    }

    private fun proteinStatus(amount: Double): HomeTodayHeroStatus {
        val today = "2026-10-09"
        return homeTodayHeroStatus(heroSnapshot().copy(mealCounts = mapOf(today to 1),
            nutritionGoal = HomeNutritionGoal("maintenance", 2000.0, 100.0, 250.0, 60.0, 25.0, 2000.0, 2000),
            mealNutritionTotals = mapOf(today to HomeNutritionTotals(1,
                mapOf(NutritionProfile.PROTEIN_GRAMS to HomeNutritionTotal(amount, 1, 0))))))
    }

    private fun heroSnapshot() = HomeSnapshot("owner", "2026-10-09", emptyList(), null, emptyList(), emptyMap(),
        emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())

    private fun completedStatus(weight: Double): HomeTodayHeroStatus {
        val records = homeTodayHeroStatus(heroSnapshot().copy(
            todayWorkoutStatus = HomeTodayWorkoutStatus(true, true, listOf("등", "이두"), true, 1_920, 1_250.5),
            todayWeight = HomeBodyMetric("today", "2026-10-09", weight, ""),
            yesterdayWeight = HomeBodyMetric("yesterday", "2026-10-08", 80.0, "")))
        return records.copy(domains = records.domains.map { domain ->
            if (domain.key == "meal") proteinStatus(60.0).domains.single { it.key == "meal" } else domain
        })
    }

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
