package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.DiningOutConsumption
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.feature.meal.model.FoodPortionInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMealInput
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

    /** Atomically copies all foods into one intake record and its immutable snapshot rows. */
    fun saveFoodComposition(
        scope: AccountScope,
        date: String,
        mealTime: String,
        portions: List<FoodPortionInput>
    ): String {
        require(portions.size == 1) { "여러 식품의 끼니 저장을 지원하지 않습니다." }
        return saveFoodMeal(scope, date, mealTime, portions.single().foodId, portions.single().quantity)
    }

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

    fun saveDiningOutPortion(scope: AccountScope, date: String, mealTime: String, input: DiningOutMealInput): String {
        require(input.quantity == 1.0) { "외식 섭취량 저장을 지원하지 않습니다." }
        return saveManualDiningOut(scope, date, mealTime, input.storeName, input.branchName, input.menuName,
            input.calories, input.proteinGrams, input.carbsGrams, input.fatGrams, input.sodiumMg, input.sugarsGrams,
            input.saturatedFatGrams, input.restaurantId, input.restaurantLocationId, input.restaurantMenuId, input.catalogProductId)
    }

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
