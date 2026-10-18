package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.meal.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionTotals
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MealRecordDetailsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calendarEditSendsRecordIdentityDateAndTimeInOneAction() {
        val initial = LocalDate.now().minusMonths(1).withDayOfMonth(1)
        val selected = initial.plusDays(1).toString()
        var saved: Triple<String, String, String>? = null
        compose.setContent { FitnessComposeTheme(false) {
            MealRecordEditDialog(MealRecordEditor("record", initial.toString(), "저장한 식단", "12:30"),
                false, null, { id, date, time -> saved = Triple(id, date, time) }, {})
        } }
        compose.onNodeWithTag("meal-record-edit-date").performClick()
        compose.onNode(hasText("2") and hasClickAction()).performClick()
        compose.onNodeWithText("선택").performClick()
        compose.onNodeWithText("날짜 선택 · $selected").assertExists()
        compose.onNodeWithTag("meal-record-edit-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(Triple("record", selected, "12:30"), saved) }
    }

    @Test fun futureDateAndInvalidTimeDisableSavingAndSavingDisablesDateInput() {
        var editor by mutableStateOf(MealRecordEditor("future", LocalDate.now().plusDays(1).toString(), "식단", "12:30"))
        var saving by mutableStateOf(false)
        compose.setContent { FitnessComposeTheme(false) {
            MealRecordEditDialog(editor, saving, null, { _, _, _ -> fail("Invalid input must not save") }, {})
        } }
        compose.onNodeWithTag("meal-record-edit-save").assertIsNotEnabled()
        compose.onNodeWithText("미래 날짜에는 식단을 기록할 수 없습니다.").assertExists()
        compose.runOnIdle { editor = editor.copy(recordId = "bad-time", date = LocalDate.now().toString(), time = "25:00") }
        compose.onNodeWithTag("meal-record-edit-save").assertIsNotEnabled()
        compose.runOnIdle { editor = editor.copy(recordId = "saving", time = "12:30"); saving = true }
        compose.onNodeWithTag("meal-record-edit-date").assertIsNotEnabled()
        compose.onNodeWithTag("meal-record-edit-save").assertIsNotEnabled()
    }

    @Test fun savedItemsShowSnapshotNamesProductIdentityActualAmountsAndIndependentComponents() {
        val first = item("food", "저장 당시 음식", 150.0, "g").copy(brandName = "저장 브랜드", productName = "저장 제품")
        val second = item("menu", "저장 당시 외식 메뉴", 2.0, "serving").copy(
            consumption = MealSnapshotConsumptionRead(2, 0.5, "manual", "user_entered"),
            components = listOf(component("side", "저장 당시 사이드", 30.0, "g", 0.25)))
        val snapshot = MealSnapshotRead("record", "2026-10-01", "dining_out", "{}", listOf(first, second))
        compose.setContent { FitnessComposeTheme(false) { MealSnapshotDetailDialog(MealDetailState.Ready("record", snapshot), {}) } }
        compose.onNodeWithText("저장 당시 음식").assertExists()
        compose.onNodeWithText("저장 브랜드 · 저장 제품").assertExists()
        compose.onNodeWithText("실제 먹은 양 150 g").assertExists()
        compose.onNodeWithText("저장 당시 외식 메뉴").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("실제 먹은 양 1 인분").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기록한 양 2 인분 · 50% 섭취").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("저장 당시 사이드 · 7.5 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("meal-detail-item-food").assertExists()
        compose.onNodeWithTag("meal-detail-item-menu").assertExists()
    }

    @Test fun loadingErrorAndLegacyMissingSnapshotRemainDistinctAndDismissible() {
        var state by mutableStateOf<MealDetailState>(MealDetailState.Loading("legacy"))
        compose.setContent { FitnessComposeTheme(false) { MealSnapshotDetailDialog(state) { state = MealDetailState.Idle } } }
        compose.onNodeWithTag("meal-detail-loading").assertExists()
        compose.runOnIdle { state = MealDetailState.Error("legacy", "식단 기록을 찾을 수 없습니다.") }
        compose.onNodeWithTag("meal-detail-error").assertExists()
        compose.onNodeWithTag("meal-detail-missing").assertDoesNotExist()
        compose.runOnIdle { state = MealDetailState.Ready("legacy", MealSnapshotRead("legacy", "2026-10-01", "food", "{}", emptyList())) }
        compose.onNodeWithText("상세 구성 정보가 저장되어 있지 않습니다.").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("메뉴 자세히 보기").assertDoesNotExist()
    }

    @Test fun dailyChildrenStayUnderTheirParentMacroAtNormalAndLargeFontScale() {
        var scale by mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                FitnessComposeTheme(false) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).testTag("macro-host")) {
                        MealMacroMetrics("50", "20", "8", "0", "?")
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { fontScale ->
            compose.runOnIdle { scale = fontScale }
            compose.onNode(hasText("당류 0 g") and hasAnyAncestor(hasTestTag("meal-macro-탄수화물")), useUnmergedTree = true).assertExists()
            compose.onNode(hasText("포화지방 ?") and hasAnyAncestor(hasTestTag("meal-macro-지방")), useUnmergedTree = true).assertExists()
            compose.onNode(hasTestTag("meal-secondary-당류") and hasAnyAncestor(hasTestTag("meal-macro-단백질")), useUnmergedTree = true).assertDoesNotExist()
            val host = compose.onNodeWithTag("macro-host").fetchSemanticsNode().boundsInRoot
            listOf("탄수화물", "단백질", "지방").forEach { label ->
                val bounds = compose.onNodeWithTag("meal-macro-$label").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= host.left - 1f && bounds.right <= host.right + 1f)
            }
        }
    }

    @Test fun cartChildrenDistinguishKnownZeroPartialAndUnknownAtLargeText() {
        val known = NutritionProfile.builder().from(NutritionProfile.ofMacros(200.0, 20.0, 25.0, 4.0))
            .value(NutritionProfile.SUGARS_GRAMS, 0.0).build()
        val missing = NutritionProfile.ofMacros(200.0, 20.0, 25.0, 4.0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        MealCartNutritionSummary(NutritionTotals.builder().add(known).add(missing).build(), {}, true)
                    }
                }
            }
        }
        compose.onNode(hasText("당류 0 g 이상") and hasAnyAncestor(hasTestTag("meal-cart-macro-탄수")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("포화지방 미확인") and hasAnyAncestor(hasTestTag("meal-cart-macro-지방")), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("탄수 50 g").assertExists()
        compose.onNodeWithText("지방 8 g").assertExists()
    }

    private fun nutrition() = MealSnapshotRead("empty", "2026-10-01", "food", "{}", emptyList()).nutrition

    private fun item(id: String, name: String, quantity: Double, unit: String) = MealSnapshotItemRead(
        id = id, foodId = "catalog-$id", foodName = name, brand = null, manufacturerName = null,
        brandName = null, subBrandName = null, productName = null, packageAmount = null, packageUnit = null,
        packageCount = null, foodKind = "ingredient", quantity = quantity, unit = unit, basisAmount = quantity,
        basisUnit = unit, prepState = null, sourceType = null, sourceReference = null, sourceVersion = null,
        foodDataVersion = null, templateId = null, templateRevision = null, portionBasis = null,
        nominalServings = null, nutrition = nutrition(), components = emptyList(), consumption = null)

    private fun component(id: String, name: String, quantity: Double, unit: String, fraction: Double?) = MealSnapshotComponentRead(
        id = id, foodId = null, foodName = name, brand = null, foodKind = "ingredient", quantity = quantity,
        unit = unit, basisAmount = quantity, basisUnit = unit, prepState = null, groupKey = null,
        groupType = null, provisionType = null, role = null, memberId = null, consumedFraction = fraction,
        sourceType = null, sourceReference = null, sourceVersion = null, foodDataVersion = null, nutrition = nutrition())
}
