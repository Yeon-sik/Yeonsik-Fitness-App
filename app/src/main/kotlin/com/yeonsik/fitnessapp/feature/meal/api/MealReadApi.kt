package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.model.MealReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotRead

/** Read-only meal port used by cross-feature read models. */
interface MealReadApi {
    fun mealCount(scope: AccountScope, date: String): Int
    fun mealTotals(scope: AccountScope, date: String): MealReadNutritionTotals
    fun meals(scope: AccountScope, date: String): List<MealReadSummary>
    fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int
    fun dates(scope: AccountScope, startDate: String, endDate: String): List<String>
    fun nutritionSummary(scope: AccountScope, startDate: String, endDate: String): MealNutritionReadSummary

    /** Reads historical meal structure from Meal-owned snapshots, never from mutable catalog rows. */
    fun mealSnapshot(scope: AccountScope, recordId: String): MealSnapshotRead? = null
}
