package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.*
import org.junit.Assert.*
import org.junit.Test

class MealDiningComponentsTest {
    @Test fun quantityAndFractionStayIndependentThroughSelectionAndDraftRestore() {
        val draft = MealDiningComponentDraft(name = "뼈 추가", quantity = "2", consumedPercent = "33.33333333333333",
            nutrients = mapOf(NutritionProfile.CALORIES_KCAL to "300", NutritionProfile.PROTEIN_GRAMS to "30"),
            catalogFoodId = "bone", memberId = "member", sourceReference = "{\"composition_template_id\":\"template\"}")
        val restored = decodeDiningComponents(encodeDiningComponents(listOf(draft))).single()
        assertEquals(draft, restored)
        val selected = restored.selection()
        assertEquals(2.0, selected.quantity, 0.0)
        assertEquals(1.0 / 3, selected.consumedFraction, 0.000001)
        assertEquals(200.0, selected.consumedProfile().calories(), 0.000001)
        assertEquals(600.0, selected.asMealCompositionItem("owner", "식당").profile.calories(), 0.0)
        assertEquals(2.0, selected.withProvisionType("review_event").withConsumedFraction(0.5).quantity, 0.0)
        assertNull(selected.consumedProfile().sodiumMg())
    }

    @Test fun groupAndProvisionAreIndependentForEveryCombination() {
        CompositionGroupType.values().forEach { group -> DiningOutProvisionType.values().forEach { provision ->
            val selected = MealDiningComponentDraft(name = "구성", groupType = group.value(), provisionType = provision.value()).selection()
            assertEquals(group.value(), selected.groupType)
            assertEquals(provision.value(), selected.provisionType)
            assertNull(selected.profile.value(NutritionProfile.CALORIES_KCAL))
        } }
    }

    @Test fun multiMenuDraftKeepsNestedSelectionsAndNeverUsesMenuFractionForComponents() {
        val burger = menu("burger", "통새우와퍼", "600").copy(consumedPercent = "50", components = listOf(
            component("통새우 추가", "add_on", "paid", "3", "100", "20"),
            component("너겟킹", "side", "included", "1", "100", "100"),
            component("제로콜라", "beverage", "included", "1", "100", "0")))
        val soup = menu("soup", "감자탕", "900").copy(consumedPercent = "33.33333333333333", components = listOf(
            component("뼈 추가", "add_on", "paid", "2", "33.33333333333333", "300"),
            component("김치", "banchan", "included", "1", "100", "10"),
            component("단무지", "banchan", "included", "1", "100", "10"),
            component("미니만두", "side", "paid", "1", "100", "100"),
            component("콜라", "beverage", "review_event", "1", "100", "100")))
        val menus = listOf(burger, soup).map { decodeDiningMenu(encodeDiningMenu(it))!! }
        assertEquals(listOf(3, 5), menus.map { it.components.size })
        val state = MealUiState.Ready("owner", "2026-10-24", true, true, "", emptyList(), DiningOutDraft(time = "12:30"), diningMenus = menus)
        assertEquals(1180.0, diningNutritionTotals(state).calories(), 0.000001)
        assertEquals(1180.0, mealEntryNutritionTotals(state).calories(), 0.000001)
        assertNull(mealEntryNutritionTotals(state).total(NutritionProfile.SODIUM_MG).completeValue())
        assertEquals("review_event", menus[1].components.last().provisionType)
        assertEquals("beverage", menus[1].components.last().groupType)
    }

    @Test fun invalidDraftCannotWriteAndDoesNotBecomeKnownZero() {
        listOf("0", "NaN", "Infinity", "-1", "").forEach {
            assertNotNull(MealDiningComponentDraft(name = "뼈", quantity = it).error())
        }
        assertNotNull(MealDiningComponentDraft(name = "뼈", nutrients = mapOf("calories_kcal" to "oops")).error())
        val menu = menu("menu", "식사", "600").copy(components = listOf(MealDiningComponentDraft(name = "뼈", quantity = "")))
        assertNull(diningMenuNutritionTotals(menu).total(NutritionProfile.CALORIES_KCAL).completeValue())
    }

    @Test fun oldEncodedFlatDraftStillReadsWithoutComponents() {
        val encoded = org.json.JSONObject(encodeDiningMenu(menu("flat", "단품", "600")))
        encoded.remove("components"); encoded.remove("consumption"); encoded.remove("source_reference")
        assertTrue(decodeDiningMenu(encoded.toString())!!.components.isEmpty())
    }

    @Test fun sourceLocationAndTemplateReferencesStayAttachedToTheirOwnMenu() {
        val original = menu("linked", "메뉴", "600")
        val linked = original.copy(draft = original.draft.copy(sourceNamespace = "pricetrace", sourceLocationCode = "seoul-1"),
            sourceReference = "{\"composition_template_id\":\"template\",\"restaurant_id\":\"restaurant\"}")
        val source = org.json.JSONObject(diningMenuSourceReference(decodeDiningMenu(encodeDiningMenu(linked))!!))
        assertEquals("template", source.getString("composition_template_id"))
        assertEquals("restaurant", source.getString("restaurant_id"))
        assertEquals("pricetrace", source.getString("source_namespace"))
        assertEquals("seoul-1", source.getString("source_location_code"))
    }

    private fun menu(id: String, name: String, calories: String) = MealDiningDraftItem(id,
        DiningOutDraft(store = "식당", menu = name, calories = calories, protein = "20", carbs = "50", fat = "10"))
    private fun component(name: String, group: String, provision: String, quantity: String, percent: String, calories: String) =
        MealDiningComponentDraft(name = name, groupType = group, provisionType = provision, quantity = quantity,
            consumedPercent = percent, nutrients = mapOf(NutritionProfile.CALORIES_KCAL to calories))
}
