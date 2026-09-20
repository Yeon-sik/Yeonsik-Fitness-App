package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.DiningOutConsumption
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.MealMenuSelection

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
        sodiumMg: Double?,
        sugarsGrams: Double?,
        saturatedFatGrams: Double?,
        restaurantId: String?,
        restaurantLocationId: String?,
        restaurantMenuId: String?,
        catalogProductId: String?
    ): String

    /** Updates only the local meal time and preserves the consumed snapshot. */
    fun updateMealTime(scope: AccountScope, recordId: String, mealTime: String): Boolean

    /** Tombstones the meal and its owned snapshot rows without changing another date. */
    fun deleteMeal(scope: AccountScope, recordId: String): Boolean

    /**
     * Records multiple menus as one Meal-owned intake snapshot.
     *
     * Catalog/template values are resolved by the caller and copied by the repository; this
     * boundary never makes historical reads depend on mutable catalog rows.
     */
    fun saveComplexDiningOutMeal(
        scope: AccountScope,
        date: String,
        mealTime: String,
        storeName: String,
        branchName: String?,
        identity: DiningOutIdentity?,
        fulfillmentMode: String?,
        menuSelections: List<MealMenuSelection>,
        nominalServings: Double,
        consumption: DiningOutConsumption
    ): String
}
