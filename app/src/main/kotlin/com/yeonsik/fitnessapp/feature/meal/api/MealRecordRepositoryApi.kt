package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitnessapp.core.account.AccountScope

/** Meal write port used by the meal application layer. */
interface MealRecordRepositoryApi {
    fun setUserId(userId: String)

    fun saveFoodMeal(
        scope: AccountScope,
        date: String,
        mealTime: String,
        foodId: String,
        quantity: Double
    ): String

    fun saveManualDiningOut(
        scope: AccountScope,
        date: String,
        mealTime: String,
        storeName: String,
        branchName: String,
        menuName: String,
        calories: Int,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double,
        sodiumMg: Double,
        sugarsGrams: Double,
        saturatedFatGrams: Double,
        restaurantId: String?,
        restaurantLocationId: String?,
        restaurantMenuId: String?,
        catalogProductId: String?
    ): String
}
