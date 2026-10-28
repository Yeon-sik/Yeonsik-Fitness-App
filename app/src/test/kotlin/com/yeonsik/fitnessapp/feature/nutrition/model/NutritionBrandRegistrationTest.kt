package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import org.junit.Assert.*
import org.junit.Test

class NutritionBrandRegistrationTest {
    private val selected = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "농심")
    private val input = NutritionFoodInput("직접 입력한 이름", "농심", 100.0, "g",
        NutritionProfile.ofMacros(100.0, 10.0, 10.0, 2.0), "manual", priceTraceBrand = selected)

    @Test fun linkedProductsAndIngredientsRequireTheSelectedCanonicalName() {
        NutritionRegistrationKind.entries.forEach { kind ->
            val draft = input.copy(registrationKind = kind)
            assertEquals("농심", draft.validatedBrand())
            assertEquals(selected.brandId, draft.priceTraceBrandId)
            assertThrows(IllegalArgumentException::class.java) { draft.copy(brand = "Nongshim").validatedBrand() }
            assertThrows(IllegalArgumentException::class.java) { draft.copy(brand = null).validatedBrand() }
        }
        assertEquals(selected.brandId, NutritionFood.builder().name("식품").brand("농심")
            .priceTraceBrandId(selected.brandId, "농심").build().priceTraceBrandId)
    }
    @Test fun offlineTextBrandsAndUnlinkKeepTextWithoutAnId() {
        val draft = input.copy(priceTraceBrand = null, brand = "  직접 적은 브랜드  ")
        assertEquals("직접 적은 브랜드", draft.validatedBrand())
        assertNull(draft.priceTraceBrandId)
        assertNull(draft.copy(brand = " ").validatedBrand())
    }
}
