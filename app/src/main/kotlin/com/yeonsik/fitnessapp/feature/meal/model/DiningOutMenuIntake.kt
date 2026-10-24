package com.yeonsik.fitnessapp.feature.meal.model

import com.yeonsik.fitnessapp.data.DiningOutComponent
import com.yeonsik.fitnessapp.data.DiningOutConsumption

/** One menu's full ordered quantity and baseline nutrition, with its own eaten fraction. */
data class DiningOutMenuIntake @JvmOverloads constructor(
    val input: DiningOutMealInput,
    val consumedFraction: Double,
    val nutritionFoodId: String? = null,
    val caloriesKcal: Double = input.calories.toDouble(),
    /** null preserves compatibility with callers that do not edit nested selections. */
    val components: List<DiningOutComponent>? = null,
    val consumption: DiningOutConsumption? = null,
    val sourceReference: String? = null
)
