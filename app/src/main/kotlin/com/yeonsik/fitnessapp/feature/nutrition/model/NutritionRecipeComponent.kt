package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionFood

/**
 * Public catalog projection for a recipe component.
 *
 * This is intentionally a presentation-neutral model. Consumed meal snapshots belong to the
 * Meal owner and must copy the values they need at intake time.
 */
data class NutritionRecipeComponent(
    val food: NutritionFood,
    val quantity: Double,
    val unit: String
)
