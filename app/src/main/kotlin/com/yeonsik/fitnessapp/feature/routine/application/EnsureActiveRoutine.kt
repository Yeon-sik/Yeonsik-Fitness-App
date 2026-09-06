package com.yeonsik.fitnessapp.feature.routine.application

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi

class EnsureActiveRoutine(private val repository: RoutineRepositoryApi) {
    fun execute(scope: AccountScope): String = repository.ensureActiveRoutine(scope)
}
