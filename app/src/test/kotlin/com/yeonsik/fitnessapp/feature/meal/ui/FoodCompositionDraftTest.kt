package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.nutritionTotalText
import org.junit.Assert.*
import org.junit.Test

class FoodCompositionDraftTest {
    @Test fun severalFoodsScaleIndividuallyAndProduceOneTotal() {
        val rice = food("rice", NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0))
        val chicken = food("chicken", NutritionProfile.ofMacros(165.0, 31.0, 0.0, 3.6))
        val total = foodPortionTotals(listOf(FoodPortionDraft(rice, "150"), FoodPortionDraft(chicken, "100")))
        assertEquals(390.0, total.calories(), 0.001)
        assertEquals(35.5, total.proteinGrams(), 0.001)
        assertEquals(49.5, total.carbsGrams(), 0.001)
        assertEquals(5.1, total.fatGrams(), 0.001)
        assertEquals(2, total.itemCount())
    }

    @Test fun addingTheSameFoodIncreasesItsAmountWithoutDuplicatingTheRow() {
        val apple = food("apple", NutritionProfile.ofMacros(52.0, 0.3, 14.0, 0.2))
        val added = addFoodPortion(listOf(FoodPortionDraft(apple, "150")), apple)
        assertEquals(1, added.size)
        assertEquals(250.0, added.single().amount!!, 0.001)
    }

    @Test fun emptyZeroNegativeAndNonFiniteAmountsStayInvalidDrafts() {
        val apple = food("apple", NutritionProfile.ofMacros(52.0, 0.3, 14.0, 0.2))
        listOf("", "0", "-1", "NaN", "Infinity", "1e309", "abc").forEach {
            assertNull(FoodPortionDraft(apple, it).amount)
            assertNull(FoodPortionDraft(apple, it).profile)
        }
    }

    @Test fun missingNutrientsRemainUnknownInThePreview() {
        val known = food("known", NutritionProfile.builder().value(NutritionProfile.SODIUM_MG, 100.0).build())
        val missing = food("missing", NutritionProfile.empty())
        val total = foodPortionTotals(listOf(FoodPortionDraft(known, "100"), FoodPortionDraft(missing, "100")))
        assertNull(total.total(NutritionProfile.SODIUM_MG).completeValue())
        assertEquals(100.0, total.total(NutritionProfile.SODIUM_MG).knownSum(), 0.001)
        assertEquals(1, total.total(NutritionProfile.SODIUM_MG).missingCount())
        assertEquals("미확인", nutritionTotalText(total.total(NutritionProfile.PROTEIN_GRAMS)))
    }

    @Test fun halfDiningPortionScalesKnownValuesAndPreservesMissingValues() {
        val editor = MealUiState.Ready("owner", "2026-10-03", true, true, "", emptyList(),
            DiningOutDraft(calories = "600", protein = "40", carbs = "80", fat = "20"), diningPortion = "0.5")
        val total = diningNutritionTotals(editor)
        assertEquals(300.0, total.calories(), 0.001)
        assertEquals(20.0, total.proteinGrams(), 0.001)
        assertNull(total.total(NutritionProfile.SODIUM_MG).completeValue())
    }

    @Test fun invalidDiningPortionDoesNotShowAMisleadingZero() {
        val editor = MealUiState.Ready("owner", "2026-10-03", true, true, "", emptyList(),
            DiningOutDraft(calories = "600"), diningPortion = "0")
        assertEquals("미확인", nutritionTotalText(diningNutritionTotals(editor).total(NutritionProfile.CALORIES_KCAL)))
    }

    @Test fun mealNumbersContinueBeyondFiveWithoutMealTypes() {
        assertEquals("1끼", MealEntryPolicy.labelForIndex(0))
        assertEquals("6끼", MealEntryPolicy.labelForIndex(5))
        assertEquals("101끼", MealEntryPolicy.labelForIndex(100))
    }

    private fun food(id: String, profile: NutritionProfile): NutritionFood = NutritionFood.builder()
        .id(id).ownerId("nutrition-owner").name(id).kind(NutritionFood.KIND_INGREDIENT)
        .basis(100.0, NutritionUnit.GRAM).profile(profile).build()
}
