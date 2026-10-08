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
}
