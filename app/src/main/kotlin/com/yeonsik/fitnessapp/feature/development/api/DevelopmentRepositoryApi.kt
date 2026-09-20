package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentGoal

/** Public write boundary for development-owned goals. */
interface DevelopmentRepositoryApi {
    fun developmentGoal(scope: AccountScope): DevelopmentGoal
    fun saveDevelopmentGoal(scope: AccountScope, goal: DevelopmentGoal)
}
