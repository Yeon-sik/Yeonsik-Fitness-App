package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.app.navigation.destinationScrollStateKey
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealScrollPolicyTest {
    @Test
    fun destinationScrollKeysAreStablePerScreenAndDistinctAcrossScreens() {
        assertEquals(
            destinationScrollStateKey(FitnessScreen.MEALS),
            destinationScrollStateKey(FitnessScreen.MEALS)
        )
        assertNotEquals(
            destinationScrollStateKey(FitnessScreen.MEALS),
            destinationScrollStateKey(FitnessScreen.WORKOUT)
        )
    }

    @Test
    fun mealEditorAnchorIgnoresInputSearchAndSelectionChanges() {
        val food = NutritionFood(
            "food-1",
            "owner-1",
            "사과",
            NutritionFood.KIND_INGREDIENT,
            100.0,
            "g",
            52.0,
            0.3,
            14.0,
            0.2,
            "manual",
            null
        )
        val foodEditor = readyEditor()
        val target = mealEditorScrollTarget(foodEditor)

        assertEquals(
            target,
            mealEditorScrollTarget(
                foodEditor.copy(
                    query = "사과",
                    searchResults = listOf(food),
                    selectedFood = food,
                    quantity = "100"
                )
            )
        )
        assertNotEquals(
            target,
            mealEditorScrollTarget(foodEditor.copy(diningOut = true))
        )
        assertNotEquals(
            target,
            mealEditorScrollTarget(foodEditor.copy(editing = false))
        )
        assertNull(mealEditorScrollTarget(null))
    }

    private fun readyEditor() = MealUiState.Ready(
        ownerId = "owner-1",
        date = "2026-09-11",
        editing = true,
        diningOut = false,
        query = "",
        searchResults = emptyList(),
        draft = DiningOutDraft()
    )
}
