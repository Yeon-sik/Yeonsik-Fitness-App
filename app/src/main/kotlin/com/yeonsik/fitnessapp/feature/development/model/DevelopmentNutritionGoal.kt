package com.yeonsik.fitnessapp.feature.development.model

data class DevelopmentNutritionGoal(
    val phase: String,
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
    val sodiumMg: Double,
    val waterMl: Int
)
