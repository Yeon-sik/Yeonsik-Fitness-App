package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.DiningOutConsumption
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.feature.meal.model.FoodPortionInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMealInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMenuIntake
import com.yeonsik.fitnessapp.data.MealMenuSelection
import com.yeonsik.fitnessapp.data.MealCompositionItem
import com.yeonsik.fitnessapp.feature.meal.model.MealRecordEditSnapshot
import com.yeonsik.fitnessapp.feature.meal.model.MealRecordEditRequest

/** Meal write port used by the meal application layer. */
interface MealRecordRepositoryApi {
    fun setUserId(userId: String)

    fun savePhotoEstimate(scope: AccountScope, date: String, mealTime: String,
        input: com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMealInput,
        recordId: String? = null, expectedUpdatedAt: String? = null): String =
        throw UnsupportedOperationException("사진 추정 기록을 지원하지 않습니다.")

    fun saveFoodMeal(
        scope: AccountScope,
        date: String,
        mealTime: String,
        foodId: String,
        quantity: Double
    ): String

    /** Copies all consumed foods into one meal in a single local transaction. */
    fun saveFoodMealItems(
        scope: AccountScope,
        date: String,
        mealTime: String,
        items: List<MealCompositionItem>
    ): String

    /** Resolves catalog identities before saving their immutable intake snapshots. */
    fun saveFoodComposition(
        scope: AccountScope,
        date: String,
        mealTime: String,
        portions: List<FoodPortionInput>
    ): String {
        require(portions.size == 1) { "여러 식품의 끼니 저장을 지원하지 않습니다." }
        val portion = portions.single()
        return saveFoodMeal(scope, date, mealTime, portion.foodId, portion.quantity)
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

    fun saveManualDiningOut(
        scope: AccountScope, date: String, mealTime: String, storeName: String, branchName: String,
        menuName: String, calories: Int, proteinGrams: Double, carbsGrams: Double, fatGrams: Double,
        sodiumMg: Double?, sugarsGrams: Double?, saturatedFatGrams: Double?, restaurantId: String?,
        restaurantLocationId: String?, restaurantMenuId: String?, catalogProductId: String?, transFatGrams: Double?
    ): String {
        require(transFatGrams == null) { "트랜스지방을 포함한 외식 기록을 지원하지 않습니다." }
        return saveManualDiningOut(scope, date, mealTime, storeName, branchName, menuName, calories,
            proteinGrams, carbsGrams, fatGrams, sodiumMg, sugarsGrams, saturatedFatGrams,
            restaurantId, restaurantLocationId, restaurantMenuId, catalogProductId)
    }

    fun saveDiningOutPortion(scope: AccountScope, date: String, mealTime: String, input: DiningOutMealInput): String {
        require(input.quantity == 1.0) { "외식 섭취량 저장을 지원하지 않습니다." }
        return saveManualDiningOut(scope, date, mealTime, input.storeName, input.branchName, input.menuName,
            input.calories, input.proteinGrams, input.carbsGrams, input.fatGrams, input.sodiumMg, input.sugarsGrams,
            input.saturatedFatGrams, input.restaurantId, input.restaurantLocationId, input.restaurantMenuId, input.catalogProductId,
            input.transFatGrams)
    }

    /** Atomically snapshots each menu at 100% and records its individual consumption. */
    fun saveDiningOutMenuItems(scope: AccountScope, date: String, mealTime: String, menus: List<DiningOutMenuIntake>): String =
        throw UnsupportedOperationException("메뉴별 외식 기록을 지원하지 않습니다.")

    /** One intake may contain any catalog categories; implementations must commit it atomically. */
    fun saveMealItems(scope: AccountScope, date: String, mealTime: String,
        foods: List<MealCompositionItem>, menus: List<DiningOutMenuIntake>): String = when {
        menus.isEmpty() -> saveFoodMealItems(scope, date, mealTime, foods)
        foods.isEmpty() -> saveDiningOutMenuItems(scope, date, mealTime, menus)
        else -> throw UnsupportedOperationException("함께 담은 식단 저장을 지원하지 않습니다.")
    }

    fun loadMealForEditing(scope: AccountScope, recordId: String): MealRecordEditSnapshot? = null

    /** Updates the original meal atomically and rejects an out-of-date edit. */
    fun updateMealItems(scope: AccountScope, request: MealRecordEditRequest, mealTime: String,
        foods: List<MealCompositionItem>, menus: List<DiningOutMenuIntake>): Boolean =
        throw UnsupportedOperationException("식단 내용 수정을 지원하지 않습니다.")

    /** Updates only the local meal time and preserves the consumed snapshot. */
    fun updateMealTime(scope: AccountScope, recordId: String, mealTime: String): Boolean

    /** Moves the existing record and its eaten_at metadata without rewriting intake snapshots. */
    fun updateMealDateTime(scope: AccountScope, recordId: String, date: String, mealTime: String): Boolean

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
