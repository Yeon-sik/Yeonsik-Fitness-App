package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionTotals

/** Raw quantity stays editable; only a valid amount produces a nutrition preview. */
data class FoodPortionDraft(val food: NutritionFood, val quantity: String) {
    val amount: Double?
        get() = quantity.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    val profile: NutritionProfile?
        get() = amount?.let { runCatching { NutritionCalculator.forQuantity(food, it) }.getOrNull() }
}

fun foodPortionTotals(portions: List<FoodPortionDraft>): NutritionTotals =
    NutritionTotals.builder().apply {
        portions.forEach { add(it.profile ?: NutritionProfile.empty()) }
    }.build()

fun addFoodPortion(portions: List<FoodPortionDraft>, food: NutritionFood): List<FoodPortionDraft> {
    val existing = portions.indexOfFirst { it.food.id == food.id }
    if (existing < 0) return portions + FoodPortionDraft(food, NutritionCalculator.trim(food.basisAmount))
    return portions.mapIndexed { index, item ->
        if (index == existing) item.copy(quantity = NutritionCalculator.trim((item.amount ?: 0.0) + food.basisAmount))
        else item
    }
}
