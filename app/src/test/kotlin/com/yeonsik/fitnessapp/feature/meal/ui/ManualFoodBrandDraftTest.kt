package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.junit.Assert.*
import org.junit.Test

class ManualFoodBrandDraftTest {
    private val brand = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "CJ", null, null)
    private val product = ManualFoodDraft(name = "제품", brand = "씨제이", calories = "100", carbs = "10", protein = "10", fat = "2",
        registrationKind = NutritionRegistrationKind.PRODUCT, packageAmount = "100")

    @Test fun arbitraryTextSavesWithoutCanonicalIdentity() {
        val input = product.validated()
        assertEquals("씨제이", input.brand)
        assertNull(input.priceTraceBrand)
    }
    @Test fun explicitSelectionSuppliesCanonicalDisplayAndIdentityToInitialSave() {
        val input = product.copy(priceTraceBrand = brand).validated()
        assertEquals("CJ", input.brand)
        assertEquals(brand, input.priceTraceBrand)
    }
    @Test fun nonProductCannotCarryCanonicalBrandSelection() {
        val input = product.copy(registrationKind = NutritionRegistrationKind.INGREDIENT, priceTraceBrand = brand).validated()
        assertNull(input.priceTraceBrand)
        assertEquals("씨제이", input.brand)
    }
}
