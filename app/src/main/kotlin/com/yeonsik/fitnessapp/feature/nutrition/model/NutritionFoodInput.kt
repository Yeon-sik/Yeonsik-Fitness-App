package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionProfile

data class NutritionFoodInput(
    val name: String,
    val brand: String?,
    val basisAmount: Double,
    val basisUnit: String,
    val profile: NutritionProfile,
    val sourceType: String
)
