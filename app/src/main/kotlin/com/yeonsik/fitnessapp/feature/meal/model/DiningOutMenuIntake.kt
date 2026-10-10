package com.yeonsik.fitnessapp.feature.meal.model

/** One menu's full ordered quantity and baseline nutrition, with its own eaten fraction. */
data class DiningOutMenuIntake @JvmOverloads constructor(
    val input: DiningOutMealInput,
    val consumedFraction: Double,
    val nutritionFoodId: String? = null,
    val caloriesKcal: Double = input.calories.toDouble()
)
