package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import org.junit.Assert.*
import org.junit.Test

class MealCartPresentationTest {
    @Test fun allIncludesTheFourCatalogTypesButExcludesReusableMenuComponents() {
        val ingredient = food("ingredient", NutritionFood.KIND_INGREDIENT)
        val recipe = food("recipe", NutritionFood.KIND_RECIPE)
        val product = food("product", NutritionFood.KIND_EXTERNAL_MENU, "manual")
        val dining = food("dining", NutritionFood.KIND_EXTERNAL_MENU, "manual_estimate")
        listOf(ingredient, recipe, product, dining).forEach { assertTrue(MealCatalogCategory.ALL.includes(it)) }
        assertFalse(MealCatalogCategory.ALL.includes(food("component", NutritionFood.KIND_EXTERNAL_MENU, "manual_option")))
        assertEquals(listOf("재료", "내 요리", "제품", "외식"), listOf(ingredient, recipe, product, dining).map(::mealCatalogLabel))
        assertEquals("모두", MealCatalogCategory.entries.first().label)
    }

    @Test fun packagedCountAndGramsRepresentTheSameActualIntake() {
        val food = NutritionFood.builder().id("pack").ownerId("nutrition-owner").name("제품")
            .kind(NutritionFood.KIND_EXTERNAL_MENU).source("manual", null).basis(100.0, NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0))
            .packageAmount(90.0).packageUnit(NutritionUnit.GRAM).build()
        val count = MealFoodDraftItem("item", food, "2", "50", PortionInputMode.COUNT)
        val grams = count.copy(quantity = "180", amountMode = PortionInputMode.BASIS)
        assertEquals(90.0, mealFoodConsumedQuantity(count)!!, 0.0)
        assertEquals(mealFoodConsumedQuantity(count), mealFoodConsumedQuantity(grams))
        assertEquals("2 개 중 50% · 실제 1 개 (90 g)", mealFoodPortionSummary(count))
        assertEquals("180 g 중 50% · 실제 90 g", mealFoodPortionSummary(grams))
        assertEquals(135.0, mealFoodNutritionTotals(listOf(count)).calories(), 0.0)
    }

    @Test fun diningAndFoodFractionsContributeOnceToTheMixedMeal() {
        val ingredient = food("rice", NutritionFood.KIND_INGREDIENT)
        val menu = MealDiningDraftItem("menu", DiningOutDraft(store = "식당", menu = "메뉴",
            calories = "600", protein = "40", carbs = "80", fat = "20"), "2", "25")
        val state = MealUiState.Ready("owner", "2026-10-12", true, false, "", emptyList(), DiningOutDraft(),
            foodItems = listOf(MealFoodDraftItem("rice-item", ingredient, "200", "50")), diningMenus = listOf(menu))
        assertEquals(450.0, mealEntryNutritionTotals(state).calories(), 0.0)
        assertEquals("2 인분 중 25% · 실제 0.5 인분", mealDiningPortionSummary(menu))
        assertNull(mealEntryRegistrationError(state))
    }

    @Test fun missingNutritionIsDifferentFromKnownZeroAndPartialTotals() {
        val known = NutritionProfile.ofMacros(0.0, 0.0, 0.0, 0.0)
        val complete = NutritionTotals.builder().add(known).build()
        val partial = NutritionTotals.builder().add(known).add(NutritionProfile.empty()).build()
        assertEquals("0", mealMacroTotalText(complete.total(NutritionProfile.CALORIES_KCAL)))
        assertEquals("0 이상", mealMacroTotalText(partial.total(NutritionProfile.CALORIES_KCAL)))
        assertEquals("미확인", mealMacroTotalText(NutritionTotals.builder().add(NutritionProfile.empty()).build().total(NutritionProfile.CALORIES_KCAL)))
        assertNull(partial.total(NutritionProfile.CALORIES_KCAL).completeValue())
    }

    @Test fun invalidAmountsAndPercentDoNotLookLikeKnownZero() {
        val item = MealFoodDraftItem("item", food("rice", NutritionFood.KIND_INGREDIENT), "100")
        assertEquals("양을 입력하세요", mealFoodPortionSummary(item.copy(quantity = "")))
        assertEquals("먹은 비율을 확인하세요", mealFoodPortionSummary(item.copy(consumedPercent = "101")))
        assertEquals("미확인", mealMacroTotalText(mealFoodNutritionTotals(listOf(item.copy(quantity = "0"))).total(NutritionProfile.CALORIES_KCAL)))
    }

    private fun food(id: String, kind: String, source: String = "manual") = NutritionFood.builder()
        .id(id).ownerId("nutrition-owner").name(id).kind(kind).source(source, null)
        .basis(100.0, NutritionUnit.GRAM).profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build()
}
