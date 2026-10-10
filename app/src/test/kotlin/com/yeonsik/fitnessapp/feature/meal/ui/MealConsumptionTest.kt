package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.*
import org.junit.Assert.*
import org.junit.Test

class MealConsumptionTest {
    @Test fun percentScalesTheSelectedAmountOnceIncludingDecimalValues() {
        assertEquals(100.0, mealConsumedQuantity("200", "50")!!, 0.0)
        assertEquals(0.25, mealConsumedQuantity("0.5", "50")!!, 0.0)
        assertEquals(0.667, mealConsumedFraction(" 66.7 ")!!, 0.000001)
        assertEquals(200.0, mealConsumedQuantity("200", "100")!!, 0.0)
    }

    @Test fun invalidRatiosCannotProduceAConsumedQuantity() {
        listOf("", " ", "0", "-1", "101", "NaN", "Infinity", "1e999", "abc").forEach {
            assertNotNull(it, mealConsumedPercentError(it))
            assertNull(it, mealConsumedQuantity("100", it))
        }
        assertNull(mealConsumedQuantity("Infinity", "50"))
        assertNull(mealConsumedQuantity("0", "50"))
    }

    @Test fun multiFoodTotalsScaleAllNutrientsWithoutChangingCatalogProfilesOrPortions() {
        val rice = food("rice", 600.0, 40.0)
        val egg = food("egg", 200.0, 20.0)
        val portions = listOf(MealFoodDraftItem("rice", rice, "200", "50"), MealFoodDraftItem("egg", egg, "100", "100"))
        val totals = mealFoodNutritionTotals(portions)
        assertEquals(800.0, totals.calories(), 0.0)
        assertEquals(60.0, totals.proteinGrams(), 0.0)
        assertEquals(600.0, rice.profile.calories(), 0.0)
        assertEquals("200", portions[0].quantity)
        assertEquals(1400.0, mealFoodNutritionTotals(portions.map { it.copy(consumedPercent = "100") }).calories(), 0.0)
    }

    @Test fun unknownNutrientsRemainUnknownAndInvalidRatioDoesNotBecomeKnownZero() {
        val portion = MealFoodDraftItem("rice", food("rice", 600.0, 40.0), "100", "50")
        val totals = mealFoodNutritionTotals(listOf(portion))
        assertEquals(300.0, totals.calories(), 0.0)
        assertNull(totals.total(NutritionProfile.SODIUM_MG).completeValue())
        assertNull(mealFoodNutritionTotals(listOf(portion.copy(consumedPercent = ""))).total(NutritionProfile.CALORIES_KCAL).completeValue())
    }

    @Test fun diningMenusUseSeparateNutritionAndPercentWithoutChangingEitherBaseline() {
        val first = MealDiningDraftItem("a", DiningOutDraft(store = "식당", menu = "볶음밥", calories = "600", protein = "40", carbs = "80", fat = "20"), consumedPercent = "50")
        val second = MealDiningDraftItem("b", DiningOutDraft(store = "식당", menu = "만두", calories = "200", protein = "10", carbs = "30", fat = "5"), consumedPercent = "100")
        val state = MealUiState.Ready("owner", "2026-10-09", true, true, "", emptyList(), DiningOutDraft(), diningMenus = listOf(first, second))
        assertEquals(500.0, diningNutritionTotals(state).calories(), 0.0)
        assertEquals(30.0, diningNutritionTotals(state).proteinGrams(), 0.0)
        assertEquals("600", first.draft.calories); assertEquals("200", second.draft.calories)
        assertNull(diningMealRegistrationError(state))
        assertNotNull(diningMealRegistrationError(state.copy(diningMenus = listOf(first.copy(consumedPercent = "101"), second))))
    }

    private fun food(id: String, calories: Double, protein: Double) = NutritionFood.builder()
        .id(id).ownerId("nutrition-owner").name(id).kind(NutritionFood.KIND_INGREDIENT)
        .basis(100.0, NutritionUnit.GRAM).profile(NutritionProfile.ofMacros(calories, protein, 80.0, 20.0)).build()
}
