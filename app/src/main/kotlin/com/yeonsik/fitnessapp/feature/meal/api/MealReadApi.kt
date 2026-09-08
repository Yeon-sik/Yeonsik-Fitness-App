package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.model.MealReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealNutritionReadSummary

/** Read-only meal port used by cross-feature read models. */
interface MealReadApi {
    fun mealCount(scope: AccountScope, date: String): Int
    fun mealTotals(scope: AccountScope, date: String): MealReadNutritionTotals
    fun meals(scope: AccountScope, date: String): List<MealReadSummary>
    fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int
    fun dates(scope: AccountScope, startDate: String, endDate: String): List<String>
    fun nutritionSummary(scope: AccountScope, startDate: String, endDate: String): MealNutritionReadSummary
}
