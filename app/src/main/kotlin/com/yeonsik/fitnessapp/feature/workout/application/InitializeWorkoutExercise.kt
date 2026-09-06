package com.yeonsik.fitnessapp.feature.workout.application

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi

/** The one-time, explicit entry operation for an exercise with no set rows. */
class InitializeWorkoutExercise(private val repository: WorkoutRepositoryApi) {
    fun execute(scope: AccountScope, recordId: String, exerciseId: String): Boolean =
        repository.ensureInitialSet(scope, recordId, exerciseId)
}
