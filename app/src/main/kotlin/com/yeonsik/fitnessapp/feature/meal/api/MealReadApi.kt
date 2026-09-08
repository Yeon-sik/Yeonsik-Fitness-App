package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.model.MealReadSummary

/** Read-only meal port used by cross-feature read models. */
interface MealReadApi {
    fun mealCount(scope: AccountScope, date: String): Int
    fun mealTotals(scope: AccountScope, date: String): MealReadNutritionTotals
    fun meals(scope: AccountScope, date: String): List<MealReadSummary>
}
