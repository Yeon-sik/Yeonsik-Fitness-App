package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSet
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutVolumePoint

/**
 * Stage-2 compatibility adapter. SQL and legacy data types stay on this side
 * of the feature API until the later Room stage.
 */
class LegacyWorkoutRepositoryAdapter(
    private val legacy: FitnessRepository
) : WorkoutRepositoryApi {
    override fun loadSession(scope: AccountScope, recordId: String): WorkoutSessionSnapshot? {
        requireScope(scope)
        val info = legacy.sessionInfo(recordId) ?: return null
        val exercises = legacy.sessionExerciseEntries(recordId)
        val mappedExercises = exercises.map { exercise ->
            val sets = legacy.setsForExercise(exercise.id)
            WorkoutSessionExercise(
                exercise.id,
                exercise.exerciseId,
                exercise.orderIndex,
                exercise.name,
                exercise.uiPart,
                exercise.equipment,
                exercise.recordType,
                FitnessRecordContract.displayRecordTypeKo(exercise.recordType),
                exercise.familyIdentity,
                sets.count { it.isCompleted },
                sets.size
            )
        }
        val metrics = legacy.sessionMetrics(recordId)
        return WorkoutSessionSnapshot(
            recordId,
            info.title,
            info.status,
            info.startedAt,
            info.durationSeconds,
            metrics.totalVolumeKg,
            metrics.setCount,
            mappedExercises,
            legacy.recentCompletedSessionVolumes(recordId, 4).map {
                WorkoutVolumePoint(it.date, it.label, it.volumeKg)
            }
        )
    }

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

    override fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion {
        requireScope(scope)
        if (!legacy.hasCompletedWorkout(recordId)) return WorkoutCompletion.NO_COMPLETED_SETS
        legacy.finishSession(recordId)
        return WorkoutCompletion.COMPLETED
    }

    override fun discard(scope: AccountScope, recordId: String) {
        requireScope(scope)
        legacy.deleteSession(recordId)
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
