package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentNutritionGoal
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInStats
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInSummary

/** Read-only development port used by cross-feature read models. */
interface DevelopmentReadApi {
    fun developmentGoal(scope: AccountScope): DevelopmentGoal
    fun nutritionGoal(scope: AccountScope): DevelopmentNutritionGoal?
    fun checkInStats(scope: AccountScope, startDate: String, endDate: String): DevelopmentCheckInStats
    fun checkInDates(scope: AccountScope, startDate: String, endDate: String): List<String>
    fun checkInSummary(scope: AccountScope, startDate: String, endDate: String): DevelopmentCheckInSummary
}
