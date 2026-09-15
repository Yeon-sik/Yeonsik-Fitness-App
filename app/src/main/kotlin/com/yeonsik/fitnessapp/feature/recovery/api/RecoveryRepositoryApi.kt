package com.yeonsik.fitnessapp.feature.recovery.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal

/** Recovery-owned writes for nutrition targets and daily check-in facts. */
interface RecoveryRepositoryApi {
    fun nutritionGoal(scope: AccountScope): AthleteNutritionGoal?
    fun saveNutritionGoal(scope: AccountScope, goal: AthleteNutritionGoal)
    fun checkIn(scope: AccountScope, date: String): AthleteDailyCheckIn
    fun saveCheckIn(scope: AccountScope, checkIn: AthleteDailyCheckIn)
}
