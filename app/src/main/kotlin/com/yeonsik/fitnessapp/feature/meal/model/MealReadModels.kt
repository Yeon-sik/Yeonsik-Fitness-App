package com.yeonsik.fitnessapp.feature.meal.model

data class MealReadNutritionTotal(
    val sum: Double,
    val known: Int,
    val missing: Int
)

data class MealReadNutritionTotals(
    val itemCount: Int,
    val totals: Map<String, MealReadNutritionTotal>
)

data class MealReadSummary(
    val id: String,
    val date: String,
    val mealLabel: String,
    val menu: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val compositionCount: Int,
    val previewTitle: String,
    val mealTime: String,
    val mealKind: String,
    val fulfillmentMode: String?,
    val storeName: String,
    val branchName: String,
    val menuName: String,
    val nutritionStatus: String,
    val macroRatio: String,
    val macroRatioAccessibility: String,
    val timeEditable: Boolean,
    val createdAt: String?,
    val subtitle: String,
    val accessibilityLabel: String
)

data class MealNutritionReadSummary(
    val proteinGrams: Double,
    val recordedDays: Int,
    val mealCount: Int,
    val estimatedMealCount: Int
)
