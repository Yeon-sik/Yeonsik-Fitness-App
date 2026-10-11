package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionTotals

/** Raw quantity stays editable; only a valid amount produces a nutrition preview. */
data class FoodPortionDraft(val food: NutritionFood, val quantity: String, val mode: PortionInputMode = PortionInputMode.BASIS) {
    val enteredAmount: Double?
        get() = quantity.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    val amount: Double?
        get() = enteredAmount?.let { entered ->
            when (mode) {
                PortionInputMode.BASIS -> entered
                PortionInputMode.COUNT -> packageAmountInBasis(food)?.let { entered * it }
                PortionInputMode.GRAMS -> recipeGramsPerServing(food)?.let { entered / it }
            }?.takeIf { it.isFinite() && it > 0.0 }
        }

    val profile: NutritionProfile?
        get() = amount?.let { runCatching { NutritionCalculator.forQuantity(food, it) }.getOrNull() }
}

fun foodPortionTotals(portions: List<FoodPortionDraft>): NutritionTotals =
    NutritionTotals.builder().apply {
        portions.forEach { add(it.profile ?: NutritionProfile.empty()) }
    }.build()

fun addFoodPortion(portions: List<FoodPortionDraft>, food: NutritionFood, preferCount: Boolean = false): List<FoodPortionDraft> {
    val existing = portions.indexOfFirst { it.food.id == food.id }
    if (existing < 0) return portions + initialFoodPortion(food, preferCount)
    return portions.mapIndexed { index, item ->
        if (index != existing || item.enteredAmount == null) item
        else {
            val increment = when (item.mode) {
                PortionInputMode.COUNT -> 1.0
                PortionInputMode.BASIS -> packageAmountInBasis(food) ?: food.basisAmount
                PortionInputMode.GRAMS -> recipeGramsPerServing(food) ?: 0.0
            }
            item.copy(quantity = NutritionCalculator.trim(item.enteredAmount!! + increment))
        }
    }
}
