package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitness.shared.core.account.AccountScope
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

    /** Returns immutable Meal-owned snapshots for a date range. */
    fun mealSnapshots(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<MealSnapshotRead> = emptyList()

    /** Reads historical meal structure from Meal-owned snapshots, never from mutable catalog rows. */
    fun mealSnapshot(scope: AccountScope, recordId: String): MealSnapshotRead? = null
}
