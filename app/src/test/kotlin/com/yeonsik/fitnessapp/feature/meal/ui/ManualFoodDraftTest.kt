package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionProfile
import org.junit.Assert.*
import org.junit.Test

class ManualFoodDraftTest {
    private fun draft() = ManualFoodDraft(name = " 테스트 식품 ", calories = "120.5",
        carbs = "15", protein = "8", fat = "3")

    @Test fun unknownOptionalNutrientsRemainNullWhileExplicitZeroIsKnown() {
        val input = draft().copy(sugars = "0").validated()
        assertEquals("테스트 식품", input.name)
        assertNull(input.brand)
        assertEquals(100.0, input.basisAmount, 0.0)
        assertEquals(120.5, input.profile.value(NutritionProfile.CALORIES_KCAL)!!, 0.0)
        assertNull(input.profile.value(NutritionProfile.SODIUM_MG))
        assertNull(input.profile.value(NutritionProfile.SATURATED_FAT_GRAMS))
        assertNull(input.profile.transFatGrams())
        assertEquals(0.0, input.profile.value(NutritionProfile.SUGARS_GRAMS)!!, 0.0)
    }

    @Test fun labelBasisAndUnitArePreservedWithoutAssumingAmountEaten() {
        val input = draft().copy(basisAmount = "250", basisUnit = "ml").validated()
        assertEquals(250.0, input.basisAmount, 0.0)
        assertEquals("ml", input.basisUnit)
        assertEquals(120.5, input.profile.value(NutritionProfile.CALORIES_KCAL)!!, 0.0)
    }

    @Test fun invalidOrMissingMeasurementsCannotCreateFoods() {
        listOf(draft().copy(name = " "), draft().copy(basisAmount = "0"),
            draft().copy(basisAmount = "Infinity"), draft().copy(basisUnit = "알 수 없음"),
            draft().copy(calories = ""), draft().copy(carbs = "-1"),
            draft().copy(protein = "NaN"), draft().copy(sodium = "abc")).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.validated() }
        }
    }

    @Test fun manualInputStoresTotalsAndOptionalTransFatWithoutAddingComponents() {
        val profile = draft().copy(carbs = "43", sugars = "8", fat = "28",
            saturatedFat = "9", transFat = "0.4").validated().profile
        assertEquals(43.0, profile.carbsGrams(), 0.0)
        assertEquals(28.0, profile.fatGrams(), 0.0)
        assertEquals(0.4, profile.transFatGrams()!!, 0.0)
        assertEquals(0.0, draft().copy(transFat = "0").validated().profile.transFatGrams()!!, 0.0)
    }

    @Test fun manualAndDiningInputsRejectComponentsAboveKnownTotals() {
        listOf(draft().copy(sugars = "50"), draft().copy(saturatedFat = "30"),
            draft().copy(transFat = "30"), draft().copy(transFat = "NaN")).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.validated() }
        }
        val dining = DiningOutDraft(store = "식당", menu = "메뉴", calories = "600", protein = "20",
            carbs = "43", sugars = "8", fat = "28", saturatedFat = "9", transFat = "0.4")
        assertNull(diningOutRegistrationError(dining))
        assertNotNull(diningOutRegistrationError(dining.copy(sugars = "50")))
        assertNotNull(diningOutRegistrationError(dining.copy(saturatedFat = "30")))
        assertNotNull(diningOutRegistrationError(dining.copy(transFat = "30")))
        assertNull(diningOutRegistrationError(dining.copy(sugars = "", saturatedFat = "", transFat = "")))
    }
}
