package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSet

/**
 * Stage-2 compatibility adapter. SQL and legacy data types stay on this side
 * of the feature API until the later Room stage.
 */
class LegacyWorkoutRepositoryAdapter(
    private val legacy: FitnessRepository
) : WorkoutRepositoryApi {
    override fun loadExerciseDetail(
        scope: AccountScope,
        recordId: String,
        activeExerciseId: String?
    ): WorkoutExerciseDetail? {
        requireScope(scope)
        val exercises = legacy.sessionExerciseEntries(recordId)
        val active = exercises.firstOrNull { it.id == activeExerciseId } ?: exercises.firstOrNull()
            ?: return null
        return WorkoutExerciseDetail(
            recordId = recordId,
            activeExercise = active.asFeatureModel(),
            exercises = exercises.map { it.asFeatureModel() },
            sets = legacy.setsForExercise(active.id).map { set ->
                WorkoutSet(
                    set.id,
                    set.setIndex,
                    set.weightKg,
                    set.actualReps,
                    set.durationSeconds,
                    set.isCompleted
                )
            }
        )
    }

    override fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
        requireScope(scope)
        if (legacy.setsForExercise(exerciseId).isNotEmpty()) return false
        legacy.addTypedSet(
            recordId,
            exerciseId,
            1,
            FitnessRepository.SetInput(
                null, null, null, null, null, null, null, null,
                false, null, null, null
            )
        )
        return true
    }

    private fun FitnessRepository.SessionExerciseEntry.asFeatureModel() = WorkoutExercise(
        id, exerciseId, orderIndex, name, recordType
    )

    private fun requireScope(scope: AccountScope) {
        check(scope.ownerId == legacy.currentUserId()) {
            "The account changed while the workout operation was pending."
        }
    }
}
