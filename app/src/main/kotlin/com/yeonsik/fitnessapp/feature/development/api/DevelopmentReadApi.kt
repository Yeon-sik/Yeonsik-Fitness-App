package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentNutritionGoal

/** Read-only development port used by cross-feature read models. */
interface DevelopmentReadApi {
    fun nutritionGoal(scope: AccountScope): DevelopmentNutritionGoal?
}
