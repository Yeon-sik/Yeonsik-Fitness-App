package com.yeonsik.fitness.shared.feature.meal.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MealReadModelsTest {
    @Test
    fun snapshotKeepsUnknownNutritionAsNullRatherThanZero() {
        val nutrition = MealSnapshotNutritionRead(
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null,
            sodiumMg = null,
            saturatedFatGrams = null,
            sugarsGrams = null,
            fiberGrams = null,
            addedSugarsGrams = null,
            transFatGrams = null,
            cholesterolMg = null,
            micronutrients = emptyMap()
        )
        val snapshot = MealSnapshotRead(
            id = "meal-1",
            date = "2026-09-20",
            mealKind = "lunch",
            metadata = "{}",
            items = emptyList(),
            nutrition = nutrition
        )

        assertEquals("meal-1", snapshot.id)
        assertNull(snapshot.nutrition.calories)
        assertNull(snapshot.nutrition.proteinGrams)
        assertEquals(emptyMap(), snapshot.nutrition.micronutrients)
    }
}
