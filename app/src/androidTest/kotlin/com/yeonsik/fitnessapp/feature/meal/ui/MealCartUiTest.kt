package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
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

class MealCartUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneFiveAndTenItemsStayCompactAndKeepTotalsAndSaveVisible() {
        var state by mutableStateOf(state(1))
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions(), state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithTag("meal-cart-review").performClick()
        listOf(1, 5, 10).forEach { count ->
            compose.runOnIdle { state = state(count) }
            compose.onNodeWithText("이번 식단 · 음식 ${count}개").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("meal-food-quantity-food-0").assertDoesNotExist()
            compose.onNodeWithTag("meal-cart-food-food-${count - 1}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("meal-entry-totals").assertIsDisplayed()
            compose.onNodeWithTag("meal-entry-save").assertIsDisplayed().assertIsEnabled()
            if (count == 5) savePreview("meal-cart-five-light.png")
        }
        savePreview("meal-cart-ten-light.png")
    }

    @Test fun quantityEditingAndRestorationRemainAvailableThroughTheCart() {
        val initial = state(1).copy(query = "밥", searchResults = listOf(food(0)))
        var added = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, _ -> if (name == "selectFood") added++ },
            initial, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithTag("food-result-food-0").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "담김"))
        compose.onNodeWithText("담기 취소").assertExists()
        compose.onNodeWithTag("meal-cart-review").performClick()
        compose.onNodeWithContentDescription("음식 0 수정").performScrollTo().performClick()
        compose.onNodeWithTag("meal-food-quantity-food-0").performScrollTo().assertTextContains("100")
        compose.runOnIdle { assertEquals(0, added) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("meal-food-quantity-food-0").performScrollTo().assertTextContains("100")
        compose.onNodeWithTag("meal-entry-find").performScrollTo().performClick()
        compose.onNodeWithTag("meal-catalog-query").performScrollTo().assertTextContains("밥")
        compose.onNodeWithTag("meal-category-ALL").assertIsSelected()
    }

    @Test fun returningFromTheCartRestoresBothSearchScrollPositions() {
        val initial = state(1).copy(searchResults = (0 until 30).map(::food))
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions(), initial, PriceTraceUiState.Idle, "6끼") } }
        val results = compose.onNodeWithTag("meal-food-results").performScrollTo().performScrollToIndex(20)
        val innerBefore = results.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val outerBefore = compose.onNodeWithTag("nutrition-entry-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("meal-cart-review").performClick()
        compose.onNodeWithTag("meal-entry-find").performScrollTo().performClick()
        assertEquals(innerBefore, compose.onNodeWithTag("meal-food-results").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        assertEquals(outerBefore, compose.onNodeWithTag("nutrition-entry-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
    }

    @Test fun removalOffersUndoAndRestoresTheOriginalDraft() {
        var state by mutableStateOf(state(5))
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args -> when (name) {
            "removeFood" -> {
                val items = mealFoodItems(state)
                val index = items.indexOfFirst { it.food.id == args!![0] }
                state = state.copy(foodItems = items.filterIndexed { i, _ -> i != index }, foodPortions = emptyList(),
                    removedItem = RemovedMealDraftItem(index, food = items[index]))
            }
            "undoRemoveItem" -> {
                val removed = state.removedItem!!
                state = state.copy(foodItems = state.foodItems.toMutableList().apply { add(removed.index, removed.food!!) }, removedItem = null)
            }
        } }, state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithTag("meal-cart-review").performClick()
        compose.onNodeWithContentDescription("음식 2 수정").performScrollTo().performClick()
        compose.onNodeWithTag("meal-cart-remove-food-food-2").performScrollTo().performClick()
        compose.onNodeWithTag("meal-cart-undo").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals((0 until 5).map { "food-$it" }, state.foodItems.map { it.food.id }); assertNull(state.removedItem) }
        compose.onNodeWithTag("meal-entry-save").assertIsEnabled()
    }

    @Test fun darkLargeTextKeepsPartialNutritionAndSaveReachable() {
        val missing = NutritionFood.builder().id("missing").ownerId("nutrition-owner").name("영양정보 미확인 음식")
            .kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM).profile(NutritionProfile.empty()).build()
        val initial = state(1).copy(foodPortions = listOf(FoodPortionDraft(food(0), "100"), FoodPortionDraft(missing, "100")))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) { MealEntryDialog(actions(), initial, PriceTraceUiState.Idle, "6끼") }
            }
        }
        compose.onNodeWithTag("meal-cart-review").performClick()
        compose.onNode(hasText("150 kcal 이상") and hasAnyAncestor(hasTestTag("meal-entry-totals"))).assertIsDisplayed()
        compose.onNodeWithText("확인된 합계 · 일부 영양정보 미확인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("meal-entry-save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        savePreview("meal-cart-dark-large-text.png")
    }

    @Test fun invalidQuantityExplainsWhySavingIsUnavailableAndSavingDisablesEdits() {
        var state by mutableStateOf(state(1).copy(foodPortions = listOf(FoodPortionDraft(food(0), "0"))))
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions(), state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithTag("meal-entry-error").assertTextContains("섭취량을 확인하세요.", substring = true)
        compose.onNodeWithTag("meal-entry-save").assertIsNotEnabled()
        compose.runOnIdle { state = state(1).copy(saving = true) }
        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        compose.onNodeWithTag("meal-cart-review").assertIsNotEnabled()
        compose.onNodeWithTag("meal-category-ALL").assertIsNotEnabled()
    }

    private fun state(count: Int) = MealUiState.Ready("fitness-owner", "2026-10-12", true, false, "", emptyList(),
        DiningOutDraft(time = "12:30"), foodPortions = (0 until count).map { FoodPortionDraft(food(it), "100") })
    private fun food(index: Int) = NutritionFood.builder().id("food-$index").ownerId("nutrition-owner").name("음식 $index")
        .kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM)
        .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build()
    private fun actions(onAction: (String, Array<out Any?>?) -> Unit = { _, _ -> }): MealScreenActions =
        Proxy.newProxyInstance(MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)) { proxy, method, args -> when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "MealCartCallbacks"
            else -> { onAction(method.name, args); null }
        } } as MealScreenActions
    private fun savePreview(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "6끼 기록")).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
