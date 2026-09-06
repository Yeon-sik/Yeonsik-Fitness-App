package com.yeonsik.fitnessapp.feature.workout.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot

/**
 * Public workout boundary for new Kotlin application code.  It deliberately
 * exposes neither SQLite types nor legacy repository model objects.
 */
interface WorkoutRepositoryApi {
    fun loadSession(scope: AccountScope, recordId: String): WorkoutSessionSnapshot?

    fun loadExerciseDetail(
        scope: AccountScope,
        recordId: String,
        activeExerciseId: String?
    ): WorkoutExerciseDetail?

    /** Explicit entry initialization; never call this while rendering a view. */
    fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean

    /** Completes only a session that has a persisted completed set. */
    fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion

    /** Deletes an empty in-progress session using the existing soft-delete contract. */
    fun discard(scope: AccountScope, recordId: String)

    fun updateTypedSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput): Boolean
    fun addTypedSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int,
                    input: WorkoutSetInput): Boolean
    fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean
    fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean
    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String,
                        replacement: WorkoutExerciseReplacement): Boolean
}

enum class WorkoutCompletion {
    COMPLETED,
    NO_COMPLETED_SETS
}
