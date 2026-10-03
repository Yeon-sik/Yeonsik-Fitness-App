package com.yeonsik.fitnessapp.feature.meal.model

/** Nutrition values describe one serving; quantity describes the serving fraction eaten. */
data class DiningOutMealInput(
    val storeName: String, val branchName: String, val menuName: String, val quantity: Double,
    val calories: Int, val proteinGrams: Double, val carbsGrams: Double, val fatGrams: Double,
    val sodiumMg: Double?, val sugarsGrams: Double?, val saturatedFatGrams: Double?,
    val restaurantId: String?, val restaurantLocationId: String?, val restaurantMenuId: String?, val catalogProductId: String?
)
