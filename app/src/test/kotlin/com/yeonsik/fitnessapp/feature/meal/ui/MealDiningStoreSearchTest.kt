package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionUnit
import org.junit.Assert.*
import org.junit.Test

class MealDiningStoreSearchTest {
    @Test fun storeInputFindsSavedBrandDespiteADifferentDishName() {
        assertTrue(matchesDiningMenuQuery(menu(), "  버거킹  ", storeOnly = true))
    }

    @Test fun storeInputDoesNotConfuseADishNameWithItsBrand() {
        assertFalse(matchesDiningMenuQuery(menu(brand = "다른 가게", name = "버거킹 와퍼", source = "{}"), "버거킹", storeOnly = true))
    }

    @Test fun storeInputSupportsTheExistingRestaurantSourceWhenBrandIsMissing() {
        assertTrue(matchesDiningMenuQuery(menu(brand = null), "버거킹", storeOnly = true))
    }

    @Test fun ordinaryMenuSearchStillFindsDishAndBranchNames() {
        assertTrue(matchesDiningMenuQuery(menu(), "통새우"))
        assertTrue(matchesDiningMenuQuery(menu(), "영등포KT"))
        assertFalse(matchesDiningMenuQuery(menu(), "영등포KT", storeOnly = true))
    }

    @Test fun malformedLegacySourceStillAllowsBrandLookup() {
        assertTrue(matchesDiningMenuQuery(menu(brand = "Burger King", source = "legacy"), "burger", storeOnly = true))
        assertFalse(matchesDiningMenuQuery(menu(brand = null, source = "legacy"), "없는 가게", storeOnly = true))
    }

    @Test fun lookupPreservesTheSavedIdentityAndUnknownNutrition() {
        val original = menu()
        val found = listOf(original).filter { matchesDiningMenuQuery(it, "버거킹", storeOnly = true) }.single()
        assertSame(original, found)
        assertEquals("menu-food", found.id)
        assertEquals(original.sourceReference, found.sourceReference)
        assertNull(found.profile.value(NutritionProfile.SODIUM_MG))
    }

    private fun menu(brand: String? = "버거킹", name: String = "통새우와퍼", source: String =
        """{"restaurant_name":"버거킹","branch_name":"영등포KT점","restaurant_id":"restaurant","restaurant_location_id":"branch","restaurant_menu_id":"menu","catalog_product_id":"product"}""") =
        NutritionFood.builder().id("menu-food").name(name).brand(brand).ownerId("nutrition-owner")
            .kind(NutritionFood.KIND_EXTERNAL_MENU).source("manual_estimate", source)
            .basis(1.0, NutritionUnit.SERVING).profile(NutritionProfile.ofMacros(600.0, 30.0, 80.0, 15.0)).build()
}
