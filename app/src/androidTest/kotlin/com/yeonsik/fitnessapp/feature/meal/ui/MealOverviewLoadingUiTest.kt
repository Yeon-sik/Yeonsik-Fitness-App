package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MealOverviewLoadingUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "meal-overview-test-owner"
    private val firstDate = "2026-10-01"
    private val secondDate = "2026-10-02"

    @Test fun changingDatesKeepsOverviewAndFollowingControlsInPlace() {
        val date = mutableStateOf(firstDate)
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0))
        show(date, home)
        val before = overviewBounds()
        val buttonBefore = compose.onNodeWithText("식단 기록하기").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("1200").assertIsDisplayed()

        compose.runOnIdle { date.value = secondDate; home.value = HomeUiState.Loading }
        assertSameBounds(before, overviewBounds())
        assertSameBounds(buttonBefore, compose.onNodeWithText("식단 기록하기").fetchSemanticsNode().boundsInRoot)
        assertLoading()
        compose.onNodeWithText("1200").assertDoesNotExist()
        savePreview("meal-overview-loading.png")

        compose.runOnIdle { home.value = ready(secondDate, 850.0) }
        assertSameBounds(before, overviewBounds())
        compose.onNodeWithText("850").assertIsDisplayed()
        compose.onNodeWithTag("meal-day-overview-loading").assertDoesNotExist()
        savePreview("meal-overview-ready.png")
    }

    @Test fun rapidDateSwitchesHideStaleDatesAndOtherOwners() {
        val date = mutableStateOf(secondDate)
        val home = mutableStateOf<HomeUiState>(HomeUiState.Loading)
        show(date, home)
        val before = overviewBounds()
        assertLoading()

        compose.runOnIdle { home.value = ready(firstDate, 1200.0) }
        assertLoading()
        compose.onNodeWithText("1200").assertDoesNotExist()
        compose.runOnIdle { home.value = ready(secondDate, 999.0, "another-owner") }
        assertLoading()
        compose.onNodeWithText("999").assertDoesNotExist()
        compose.runOnIdle { date.value = "2026-10-03"; home.value = ready(secondDate, 850.0) }
        assertLoading()
        compose.onNodeWithText("850").assertDoesNotExist()
        compose.runOnIdle { home.value = ready(date.value, 700.0) }
        assertSameBounds(before, overviewBounds())
        compose.onNodeWithText("700").assertIsDisplayed()
        compose.runOnIdle { home.value = ready(secondDate, 850.0) }
        assertLoading()
        compose.onNodeWithText("700").assertDoesNotExist()
        compose.onNodeWithText("850").assertDoesNotExist()
    }

    @Test fun refreshAndFailureKeepTheSameBoxAndHideOldMetrics() {
        val date = mutableStateOf(firstDate)
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0))
        show(date, home)
        val before = overviewBounds()
        compose.runOnIdle { home.value = HomeUiState.Loading }
        assertLoading()
        assertSameBounds(before, overviewBounds())
        compose.onNodeWithText("1200").assertDoesNotExist()

        compose.runOnIdle { home.value = HomeUiState.Error(owner, "read failure", firstDate) }
        assertSameBounds(before, overviewBounds())
        compose.onNodeWithText("식단을 불러오지 못했습니다.").assertIsDisplayed()
        compose.onNodeWithTag("meal-day-overview-loading").assertDoesNotExist()
        compose.onNodeWithText("1200").assertDoesNotExist()
        compose.runOnIdle { home.value = ready(firstDate, 1300.0) }
        compose.onNodeWithText("1300").assertIsDisplayed()
        assertSameBounds(before, overviewBounds())
    }

    @Test fun largeTextAndDarkModeReserveTheMetricSpaceWhileLoading() {
        val date = mutableStateOf(firstDate)
        val home = mutableStateOf<HomeUiState>(HomeUiState.Loading)
        show(date, home, fontScale = 2f, dark = true)
        val before = overviewBounds()
        assertLoading()
        savePreview("meal-overview-loading-large-dark.png")
        compose.runOnIdle { home.value = ready(firstDate, 1200.0) }
        assertSameBounds(before, overviewBounds())
        compose.onNodeWithText("1200").assertIsDisplayed()
    }

    @Test fun asynchronousEditorDateChangesKeepRegistrationAndManagementInPlace() {
        val date = mutableStateOf(firstDate)
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0))
        val editor = mutableStateOf<MealUiState>(baseEditor(firstDate))
        show(date, home, editorState = editor)
        val labels = listOf("식단 기록하기", "내 식단 · 식품 관리", "외식 영양정보 관리", "기록한 식단")
        val before = labels.associateWith { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        val nodeIds = labels.associateWith { compose.onNodeWithText(it).fetchSemanticsNode().id }
        compose.runOnIdle { date.value = secondDate; home.value = HomeUiState.Loading }
        fun assertFixedControls() {
            labels.forEach {
                val node = compose.onNodeWithText(it).fetchSemanticsNode()
                assertSameBounds(before.getValue(it), node.boundsInRoot)
                assertEquals("Control must remain composed: $it", nodeIds.getValue(it), node.id)
            }
            compose.onNodeWithText("식단 입력을 준비하는 중입니다.").assertDoesNotExist()
        }
        assertFixedControls()
        compose.onNodeWithText("식단 기록하기").assertIsNotEnabled()
        compose.runOnIdle { editor.value = MealUiState.Idle }
        assertFixedControls()
        compose.runOnIdle { editor.value = baseEditor(secondDate) }
        assertFixedControls()
        compose.onNodeWithText("식단 기록하기").assertIsEnabled()
        compose.runOnIdle { home.value = ready(secondDate, 850.0) }
        assertFixedControls()
    }

    @Test fun existingRecordsStayInPlaceUntilTheSelectedDateFinishesLoading() {
        val date = mutableStateOf(firstDate)
        val oldMeal = meal(firstDate, "old-meal", "이전 날짜 점심")
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0, meals = listOf(oldMeal)))
        val editor = mutableStateOf<MealUiState>(baseEditor(firstDate))
        show(date, home, editorState = editor)
        val footer = "외식 영양정보 연결 · 공개"
        val before = compose.onNodeWithText(footer).fetchSemanticsNode().boundsInRoot
        val oldCardId = compose.onNodeWithTag("meal-record-old-meal").fetchSemanticsNode().id
        compose.runOnIdle { date.value = secondDate; home.value = HomeUiState.Loading }
        compose.onNodeWithText(oldMeal.previewTitle).assertExists()
        assertEquals(oldCardId, compose.onNodeWithTag("meal-record-old-meal").fetchSemanticsNode().id)
        compose.onNodeWithText("아직 기록한 식단이 없어요. 위에서 첫 식단을 기록해 보세요.").assertDoesNotExist()
        assertSameBounds(before, compose.onNodeWithText(footer).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("수정").assertIsNotEnabled()
        compose.onNodeWithText("삭제").assertIsNotEnabled()
        saveHistoryPreview("meal-history-loading.png")
        compose.runOnIdle { editor.value = baseEditor(secondDate); home.value = ready(firstDate, 1400.0) }
        compose.onNodeWithText(oldMeal.previewTitle).assertExists()
        assertSameBounds(before, compose.onNodeWithText(footer).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { home.value = HomeUiState.Error(owner, "read failure", secondDate) }
        compose.onNodeWithText(oldMeal.previewTitle).assertExists()
        compose.onNodeWithText("삭제").assertIsNotEnabled()
        assertSameBounds(before, compose.onNodeWithText(footer).fetchSemanticsNode().boundsInRoot)
        val newMeal = meal(secondDate, "new-meal", "새 날짜 저녁")
        compose.runOnIdle { home.value = ready(secondDate, 850.0, meals = listOf(newMeal)) }
        compose.onNodeWithText(oldMeal.previewTitle).assertDoesNotExist()
        compose.onNodeWithText(newMeal.previewTitle).assertExists()
        compose.onNodeWithText("수정").assertIsEnabled()
        compose.onNodeWithText("삭제").assertIsEnabled()
        saveHistoryPreview("meal-history-ready.png")
    }

    @Test fun noRecordsMessageAppearsOnlyAfterTheNewDateCompletes() {
        val date = mutableStateOf(firstDate)
        val oldMeal = meal(firstDate, "old-meal", "이전 날짜 점심")
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0, meals = listOf(oldMeal)))
        show(date, home)
        compose.runOnIdle { date.value = secondDate; home.value = HomeUiState.Loading }
        compose.onNodeWithText(oldMeal.previewTitle).assertExists()
        val emptyMessage = "아직 기록한 식단이 없어요. 위에서 첫 식단을 기록해 보세요."
        compose.onNodeWithText(emptyMessage).assertDoesNotExist()
        compose.runOnIdle { home.value = ready(secondDate, 0.0) }
        compose.onNodeWithText(oldMeal.previewTitle).assertDoesNotExist()
        compose.onNodeWithText(emptyMessage).assertExists()
        compose.onNodeWithTag("meal-record-history-pending").assertDoesNotExist()
    }

    @Test fun accountChangesDiscardRetainedRecordsAndRejectThePreviousOwnersResults() {
        val date = mutableStateOf(firstDate)
        val selectedOwner = mutableStateOf(owner)
        val oldMeal = meal(firstDate, "old-meal", "이전 계정 점심")
        val home = mutableStateOf<HomeUiState>(ready(firstDate, 1200.0, meals = listOf(oldMeal)))
        show(date, home, ownerState = selectedOwner)
        compose.runOnIdle { selectedOwner.value = "new-owner"; home.value = HomeUiState.Loading }
        compose.onNodeWithText(oldMeal.previewTitle).assertDoesNotExist()
        compose.onNodeWithText("식단 기록하기").assertIsNotEnabled()
        compose.runOnIdle { home.value = ready(firstDate, 1200.0, meals = listOf(oldMeal)) }
        compose.onNodeWithText(oldMeal.previewTitle).assertDoesNotExist()
        compose.onNodeWithTag("meal-record-history-pending").assertExists()
        compose.runOnIdle { home.value = ready(firstDate, 850.0, ownerId = "new-owner") }
        compose.onNodeWithTag("meal-record-history-pending").assertDoesNotExist()
    }

    private fun baseEditor(date: String) = MealUiState.Ready(owner, date, false, false, "", emptyList(), DiningOutDraft())

    private fun show(date: MutableState<String>, home: MutableState<HomeUiState>, fontScale: Float = 1f,
                     dark: Boolean = false, editorState: MutableState<MealUiState>? = null,
                     ownerState: MutableState<String>? = null) {
        val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader,
            arrayOf(MealScreenActions::class.java)) { proxy, method, args -> when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "MealOverviewActions"
                else -> null
            } } as MealScreenActions
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FitnessComposeTheme(dark) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        MealScreen(home.value, editorState?.value ?: baseEditor(date.value), PriceTraceUiState.Idle,
                            NutritionPublicationUiState(), ownerState?.value ?: owner, date.value, actions)
                    }
                }
            }
        }
    }

    private fun ready(date: String, calories: Double, ownerId: String = owner,
                      meals: List<HomeMealSummary> = emptyList()): HomeUiState.Ready {
        val totals = HomeNutritionTotals(1, mapOf(
            "calories_kcal" to HomeNutritionTotal(calories, 1, 0),
            "carbs_grams" to HomeNutritionTotal(45.0, 1, 0),
            "protein_grams" to HomeNutritionTotal(60.0, 1, 0),
            "fat_grams" to HomeNutritionTotal(20.0, 1, 0)
        ))
        return HomeUiState.Ready(HomeSnapshot(ownerId, date, emptyList(), null, emptyList(),
            emptyMap(), emptyMap(), null, emptyMap(), emptyMap(), mapOf(date to totals),
            null, null, emptyList(), meals))
    }

    private fun meal(date: String, id: String, title: String) = HomeMealSummary(id, date, "점심", title,
        500, 30.0, 60.0, 12.0, 1, title, "12:30", "FOOD", null, "", "", title,
        "known", "", "", true, null, "12:30 · 식품 1개", title)

    private fun assertLoading() {
        compose.onNodeWithTag("meal-day-overview").assertIsDisplayed()
        compose.onNodeWithText("이날의 식단").assertIsDisplayed()
        val insideOverview = hasAnyAncestor(hasTestTag("meal-day-overview"))
        compose.onNode(hasTestTag("thinking-orb") and insideOverview).assertIsDisplayed()
        compose.onNode(hasText("불러오는 중") and insideOverview).assertIsDisplayed()
        compose.onNodeWithTag("meal-day-overview-loading").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        compose.onNodeWithText("?").assertDoesNotExist()
    }

    private fun overviewBounds() = compose.onNodeWithTag("meal-day-overview").fetchSemanticsNode().boundsInRoot
    private fun assertSameBounds(expected: Rect, actual: Rect) {
        assertEquals("left", expected.left, actual.left, 1f)
        assertEquals("top", expected.top, actual.top, 1f)
        assertEquals("width", expected.width, actual.width, 1f)
        assertEquals("height", expected.height, actual.height, 1f)
    }

    private fun savePreview(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-overview")!!
        folder.mkdirs()
        File(folder, name).outputStream().use { stream ->
            compose.onNodeWithTag("meal-day-overview").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun saveHistoryPreview(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-overview")!!
        folder.mkdirs()
        File(folder, name).outputStream().use { stream ->
            compose.onNodeWithTag("meal-record-history").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
