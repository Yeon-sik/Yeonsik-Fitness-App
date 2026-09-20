package com.yeonsik.fitness.shared.feature.workout.application

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi

/** Keeps the existing completed-set check before the legacy completion write. */
class CompleteWorkout(private val repository: WorkoutRepositoryApi) {
    fun execute(scope: AccountScope, recordId: String): WorkoutCompletion =
        repository.completeIfEligible(scope, recordId)

    fun discardEmptySession(scope: AccountScope, recordId: String) {
        repository.discard(scope, recordId)
    }
}
