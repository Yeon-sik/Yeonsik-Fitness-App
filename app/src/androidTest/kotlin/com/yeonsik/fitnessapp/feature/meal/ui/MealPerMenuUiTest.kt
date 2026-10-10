package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MealPerMenuUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoDiningMenusHaveIndependentPercentAndNutritionEditors() {
        val first = diningMenu("a", "볶음밥", "600", "50")
        val second = diningMenu("b", "만두", "200", "100")
        var state by mutableStateOf(base(true).copy(diningMenus = listOf(first, second)))
        val actions = actions { name, args -> when (name) {
            "updateDiningMenu" -> { val changed = args!![0] as MealDiningDraftItem; state = state.copy(diningMenus = state.diningMenus.map { if (it.id == changed.id) changed else it }) }
            "removeDiningMenu" -> state = state.copy(diningMenus = state.diningMenus.filterNot { it.id == args!![0] })
        } }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "식단") } }
        assertTotal("500 kcal")
        compose.onNodeWithTag("meal-dining-menus").performScrollTo()
        compose.onNodeWithTag("meal-dining-percent-a-25").performScrollTo().performClick()
        assertTotal("350 kcal")
        compose.runOnIdle { assertEquals("100", state.diningMenus[1].consumedPercent) }
        compose.onNodeWithTag("meal-dining-edit-a").performScrollTo().performClick()
        compose.onNodeWithTag("meal-dining-calories-a").performScrollTo().performTextReplacement("800")
        assertTotal("400 kcal")
        compose.runOnIdle { assertEquals("200", state.diningMenus[1].draft.calories) }
        compose.onNodeWithTag("meal-dining-edit-a").performScrollTo().performClick()
        compose.onNodeWithTag("meal-dining-percent-b-50").performScrollTo().performClick()
        assertTotal("300 kcal")
        compose.onNodeWithTag("meal-entry-save").assertIsEnabled()
        savePreview("meal-per-menu-dining.png")
        compose.onNodeWithTag("meal-dining-percent-b").performScrollTo().performTextReplacement("101")
        compose.onNodeWithTag("meal-entry-save").assertIsNotEnabled()
        compose.onNodeWithTag("meal-dining-remove-b").performScrollTo().performClick()
        assertTotal("200 kcal")
        compose.onNodeWithTag("meal-entry-save").assertIsEnabled()
    }

    @Test fun twoFoodsCanUseDifferentPercentWithoutChangingTheOtherFood() {
        val first = food("rice", "볶음밥", 600.0)
        val second = food("dumpling", "만두", 200.0)
        var state by mutableStateOf(base(false).copy(
            foodItems = listOf(MealFoodDraftItem("rice", first, "100", "50"), MealFoodDraftItem("dumpling", second, "100", "100")),
            foodPortions = listOf(FoodPortionDraft(first, "100"), FoodPortionDraft(second, "100"))))
        val actions = actions { name, args -> if (name == "updateFoodConsumedPercent") {
            state = state.copy(foodItems = state.foodItems.map { if (it.id == args!![0]) it.copy(consumedPercent = args[1] as String) else it })
        } }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "식단") } }
        assertTotal("500 kcal")
        compose.onNodeWithTag("meal-food-portions").performScrollTo()
        compose.onNodeWithTag("meal-food-percent-rice-25").performScrollTo().performClick()
        assertTotal("350 kcal")
        compose.runOnIdle { assertEquals("100", state.foodItems[1].consumedPercent); assertEquals("100", state.foodItems[0].quantity) }
        compose.onNodeWithTag("meal-food-portions").performScrollToNode(hasTestTag("meal-food-percent-dumpling-50"))
        compose.onNodeWithTag("meal-food-percent-dumpling-50").performScrollTo().performClick()
        assertTotal("250 kcal")
        compose.onNodeWithTag("meal-entry-save").assertIsEnabled()
        savePreview("meal-per-menu-food.png")
    }

    @Test fun sameStoreOffersOneAddMenuActionAndScrollsToTheNewMenuInput() {
        val first = diningMenu("a", "볶음밥", "600", "50")
        val second = diningMenu("b", "만두", "200", "100")
        var state by mutableStateOf(base(true).copy(diningMenus = listOf(first, second)))
        var sourceId: String? = null
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "startAnotherDiningMenu") {
                sourceId = args!![0] as String
                state = state.copy(draft = DiningOutDraft(store = "식당", time = "12:30"))
            }
        }, state, PriceTraceUiState.Idle, "식단") } }
        compose.onAllNodesWithText("식당 메뉴 추가").assertCountEquals(1)
        compose.onNodeWithTag("meal-dining-add-at-a").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onNodeWithTag("meal-dining-menu-name").isDisplayed() }
        compose.runOnIdle { assertEquals("a", sourceId); assertEquals(2, state.diningMenus.size) }
        assertTotal("500 kcal")
        compose.onNodeWithTag("meal-entry-save").assertIsEnabled()
        savePreview("meal-same-store-add.png")
    }

    @Test fun savedMenuSelectionKeepsTheListAvailableForTheSecondMenu() {
        val first = menuFood("rice", "볶음밥", 600.0)
        val second = menuFood("dumpling", "만두", 200.0)
        var state by mutableStateOf(base(true).copy(searchResults = listOf(first, second), searchCompleted = true))
        val selected = mutableListOf<String>()
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "useDiningOutFood") {
                val food = args!![0] as NutritionFood
                selected.add(food.id)
                state = state.copy(selectedFood = food, draft = DiningOutDraft(store = "식당", menu = food.name,
                    calories = food.profile.calories().toInt().toString(), protein = "20", carbs = "50", fat = "10"))
            }
        }, state, PriceTraceUiState.Idle, "식단") } }
        compose.onNodeWithText("저장된 메뉴에서 선택").performScrollTo().performClick()
        compose.onNodeWithTag("meal-dining-results").performScrollTo()
        compose.onNodeWithTag("food-result-rice").performClick()
        compose.onNodeWithTag("meal-dining-results").assertIsDisplayed()
        compose.onNodeWithTag("food-result-dumpling").performClick()
        compose.runOnIdle { assertEquals(listOf("rice", "dumpling"), selected) }
        compose.onNodeWithTag("meal-dining-results").assertExists()
    }

    private fun base(dining: Boolean) = MealUiState.Ready("owner", "2026-10-09", true, dining, "", emptyList(), DiningOutDraft(time = "12:30"))
    private fun diningMenu(id: String, name: String, calories: String, percent: String) = MealDiningDraftItem(id,
        DiningOutDraft(store = "식당", menu = name, calories = calories, protein = "20", carbs = "50", fat = "10"), consumedPercent = percent)
    private fun food(id: String, name: String, calories: Double) = NutritionFood.builder().id(id).ownerId("nutrition-owner")
        .name(name).kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM)
        .profile(NutritionProfile.ofMacros(calories, 20.0, 50.0, 10.0)).build()
    private fun menuFood(id: String, name: String, calories: Double) = NutritionFood.builder().id(id).ownerId("nutrition-owner")
        .name(name).brand("식당").kind(NutritionFood.KIND_EXTERNAL_MENU).basis(1.0, NutritionUnit.SERVING)
        .profile(NutritionProfile.ofMacros(calories, 20.0, 50.0, 10.0)).build()
    private fun actions(onAction: (String, Array<out Any?>?) -> Unit): MealScreenActions = Proxy.newProxyInstance(
        MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)) { proxy, method, args -> when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "PerMenuActions"
            else -> { onAction(method.name, args); null }
        } } as MealScreenActions
    private fun assertTotal(value: String) = compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("meal-entry-totals")))
        .assertIsDisplayed()
    private fun savePreview(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "식단 기록"),
            useUnmergedTree = true).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
