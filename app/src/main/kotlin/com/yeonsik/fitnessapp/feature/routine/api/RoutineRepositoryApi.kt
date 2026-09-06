package com.yeonsik.fitnessapp.feature.routine.api

import com.yeonsik.fitnessapp.core.account.AccountScope

/** Feature boundary for routine initialization while SQLite remains legacy-owned. */
interface RoutineRepositoryApi {
    fun ensureActiveRoutine(scope: AccountScope): String
}
