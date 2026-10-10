package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MealEntryDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingMultipleFoodsKeepsBothScrollPositionsAndTheSearchOpen() {
        val foods = (1..24).map { index -> NutritionFood.builder().id("food-$index")
            .ownerId("nutrition-owner").name("테스트 식품 $index")
            .kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build() }
        var state by mutableStateOf(foodState().copy(foodPortions = emptyList(), searchResults = foods,
            searchCompleted = true))
        val actions = actions { name, args ->
            if (name == "selectFood") {
                val food = args!![0] as NutritionFood
                state = state.copy(foodPortions = state.foodPortions + FoodPortionDraft(food, "100"))
            }
        }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "식단") } }
        val results = compose.onNodeWithTag("meal-food-results")
        results.performScrollTo()
        results.performScrollToNode(hasText("테스트 식품 12"))
        val outer = compose.onNodeWithTag("nutrition-entry-scroll")
        val outerBefore = outer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val resultsBefore = results.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithText("테스트 식품 12").performClick()
        compose.runOnIdle { assertEquals(1, state.foodPortions.size) }
        assertEquals(outerBefore, outer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        assertEquals(resultsBefore, results.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        results.assertIsDisplayed()
        results.performScrollToNode(hasText("테스트 식품 13"))
        compose.onNodeWithText("테스트 식품 13").performClick()
        compose.runOnIdle { assertEquals(listOf("food-12", "food-13"), state.foodPortions.map { it.food.id }) }
        compose.onNodeWithText("식품 2개").assertExists()
    }

    @Test fun topButtonReturnsTheLongRegistrationFormToItsStart() {
        val menus = (1..24).map { index -> NutritionFood.builder().id("menu-$index")
            .ownerId("nutrition-owner").name("저장된 메뉴 $index")
            .kind(NutritionFood.KIND_EXTERNAL_MENU).basis(1.0, NutritionUnit.SERVING)
            .profile(NutritionProfile.ofMacros(600.0, 30.0, 70.0, 20.0)).build() }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { _, _ -> },
            foodState().copy(diningOut = true, searchResults = menus, searchCompleted = true),
            PriceTraceUiState.Idle, "식단") } }
        compose.onNodeWithText("저장된 메뉴에서 선택").performScrollTo().performClick()
        compose.onNodeWithText("저장된 메뉴 1").assertExists()
        val outer = compose.onNodeWithTag("nutrition-entry-scroll")
        outer.performTouchInput { swipeUp() }
        compose.onNodeWithTag("meal-entry-top").assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            outer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() == 0f
        }
        compose.onNodeWithText("등록 순서대로 기록해요").assertIsDisplayed()
        compose.onNodeWithTag("meal-entry-top").assertDoesNotExist()
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
    }

    @Test fun sixthMealSupportsQuantityEditingAndLiveTotals() {
        var state by mutableStateOf(foodState())
        var saved = false
        val actions = actions { name, args ->
            when (name) {
                "updateFoodQuantity" -> state = state.copy(foodPortions = state.foodPortions.map {
                    if (it.food.id == args!![0]) it.copy(quantity = args[1] as String) else it
                })
                "removeFood" -> state = state.copy(foodPortions = emptyList())
                "saveFood" -> saved = true
            }
        }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithText("6끼 기록").assertIsDisplayed()
        compose.onNodeWithText("6끼 기록하기").assertIsDisplayed()
        compose.onNodeWithText("아침").assertDoesNotExist()
        compose.onNodeWithText("점심").assertDoesNotExist()
        compose.onNodeWithText("저녁").assertDoesNotExist()
        savePreview("meal-entry-light.png", "6끼 기록")
        compose.onNodeWithTag("meal-food-portions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("밥 전체 양 늘리기").performScrollTo().assertIsDisplayed().performClick()
        savePreview("meal-entry-quantity-click.png", "6끼 기록")
        compose.runOnIdle { assertEquals("175", state.foodPortions.single().quantity) }
        compose.onNodeWithText("175").assertExists()
        compose.onNodeWithText("262.5 kcal").assertExists()
        compose.onNodeWithText("전체 양").performClick().performTextReplacement("200")
        compose.onNodeWithText("300 kcal").assertExists()
        compose.onNodeWithText("6끼 기록하기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(saved) }
        compose.onNodeWithTag("meal-food-portions").performScrollTo()
        compose.onNodeWithContentDescription("밥 구성에서 삭제").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(state.foodPortions.isEmpty()) }
        compose.onNodeWithText("6끼 기록하기").assertIsNotEnabled()
    }

    @Test fun darkAppearanceAndLargeTextKeepTheSaveActionVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) { MealEntryDialog(actions { _, _ -> }, foodState(), PriceTraceUiState.Idle, "12끼") }
            }
        }
        compose.onNodeWithText("12끼 기록").assertIsDisplayed()
        compose.onNodeWithText("12끼 기록하기").assertIsDisplayed()
        compose.onNodeWithText("225 kcal").assertIsDisplayed()
        savePreview("meal-entry-dark-large-text.png", "12끼 기록")
    }

    @Test fun diningPercentUpdatesTheSummaryWithoutChangingFullNutrition() {
        var state by mutableStateOf(MealUiState.Ready("fitness-owner", "2026-10-03", true, true, "", emptyList(),
            DiningOutDraft(store = "식당", menu = "비빔밥", calories = "600", protein = "40", carbs = "80", fat = "20")))
        val actions = actions { name, args -> if (name == "updateDiningConsumedPercent") state = state.copy(diningConsumedPercent = args!![0] as String) }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "7끼") } }
        compose.onNodeWithTag("meal-dining-current-percent-50").performScrollTo().performClick()
        compose.onNodeWithText("300 kcal").assertIsDisplayed()
        compose.onNodeWithText("20 g").assertIsDisplayed()
        compose.onNodeWithText("7끼 기록하기").assertIsDisplayed()
        compose.runOnIdle { assertEquals("600", state.draft.calories); assertEquals("40", state.draft.protein) }
        savePreview("meal-entry-dining-percent.png", "7끼 기록")
    }

    @Test fun foodPercentUpdatesCaloriesAndRejectsInvalidInput() {
        var state by mutableStateOf(foodState())
        val actions = actions { name, args -> if (name == "updateFoodConsumedPercent") {
            state = state.copy(foodItems = mealFoodItems(state).map { if (it.id == args!![0]) it.copy(consumedPercent = args[1] as String) else it })
        } }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "식단") } }
        compose.onNodeWithTag("meal-food-portions").performScrollTo()
        compose.onNodeWithTag("meal-food-percent-rice-50").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("50", state.foodItems.single().consumedPercent) }
        assertTotal("112.5 kcal")
        compose.runOnIdle { assertEquals("150", state.foodPortions.single().quantity) }
        savePreview("meal-entry-food-percent.png", "식단 기록")
        compose.onNodeWithTag("meal-food-percent-rice").performScrollTo().performTextReplacement("25")
        assertTotal("56.3 kcal")
        compose.onNodeWithTag("meal-food-percent-rice").performTextReplacement("101")
        compose.onNodeWithTag("meal-entry-save").assertIsNotEnabled()
        compose.onNodeWithText("먹은 비율은 0보다 크고 100 이하인 숫자로 입력하세요.").assertExists()
        compose.onNodeWithTag("meal-food-percent-rice").performTextReplacement("")
        compose.onNodeWithTag("meal-entry-save").assertIsNotEnabled()
    }

    private fun assertTotal(value: String) = compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("meal-entry-totals")))
        .assertIsDisplayed()

    private fun foodState(): MealUiState.Ready {
        val rice = NutritionFood.builder().id("rice").ownerId("nutrition-owner").name("밥")
            .kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build()
        return MealUiState.Ready("fitness-owner", "2026-10-03", true, false, "", emptyList(), DiningOutDraft(time = "12:30"),
            foodPortions = listOf(FoodPortionDraft(rice, "150")))
    }

    private fun actions(onAction: (String, Array<out Any?>?) -> Unit): MealScreenActions = Proxy.newProxyInstance(
        MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "MealEntryTestActions"
            else -> { onAction(method.name, args); null }
        }
    } as MealScreenActions

    private fun savePreview(name: String, title: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
