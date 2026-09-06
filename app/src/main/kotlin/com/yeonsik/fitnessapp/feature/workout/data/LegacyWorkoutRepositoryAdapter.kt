package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseBests
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSet
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutVolumePoint
import com.yeonsik.fitnessapp.exercise.RoutineExercise

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
        val activeSets = legacy.setsForExercise(active.id)
        return WorkoutExerciseDetail(
            recordId = recordId,
            activeExercise = active.asFeatureModel(),
            exercises = exercises.map { it.asFeatureModel() },
            sets = activeSets.map { set ->
                set.asFeatureModel()
            },
            lastHistory = legacy.lastExerciseHistory(active.exerciseId, active.name, recordId)?.asFeatureModel(),
            bests = legacy.exerciseBests(active.exerciseId, active.name, recordId)?.asFeatureModel(),
            recentVolumes = legacy.recentExerciseVolumes(active.exerciseId, active.name, recordId, 8).map {
                WorkoutVolumePoint(it.date, it.label, it.volumeKg)
            },
            allowedLoadStates = exercises.associate { exercise ->
                exercise.id to legacy.allowedLoadStatesForExercise(exercise.id)
            },
            volumeFormula = legacy.volumeCalculationFormula(active),
            volumeBySetId = activeSets.associate { set -> set.id to legacy.volumeForSet(active, set) }
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

    override fun updateTypedSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput): Boolean {
        requireScope(scope)
        legacy.updateTypedSet(recordId, setId, input.toLegacy())
        return true
    }

    override fun addTypedSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int,
                              input: WorkoutSetInput): Boolean {
        requireScope(scope)
        legacy.addTypedSet(recordId, exerciseId, setIndex, input.toLegacy())
        return true
    }

    override fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean {
        requireScope(scope)
        legacy.deleteSet(recordId, setId)
        return true
    }

    override fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
        requireScope(scope)
        legacy.deleteExercise(recordId, exerciseId)
        return true
    }

    override fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String,
                                 replacement: WorkoutExerciseReplacement): Boolean {
        requireScope(scope)
        return legacy.replaceExerciseFromMaster(recordId, exerciseId, replacement.toLegacy())
    }

    private fun FitnessRepository.SessionExerciseEntry.asFeatureModel() = WorkoutExercise(
        id, exerciseId, orderIndex, name, uiPart, equipment, recordType, familyIdentity
    )

    private fun FitnessRepository.SessionSetEntry.asFeatureModel() = WorkoutSet(
        id, setIndex, weightKg, actualReps, rir, restSeconds, isCompleted,
        durationSeconds, distanceMeters, assistedWeightKg, addedWeightKg,
        loadState, inputLoadValue, inputLoadUnit
    )

    private fun FitnessRepository.ExerciseHistory.asFeatureModel() = WorkoutExerciseHistory(
        date, totalVolumeKg, sets.map { it.asFeatureModel() }
    )

    private fun FitnessRepository.ExerciseBests.asFeatureModel() = WorkoutExerciseBests(
        performanceKey, loadState, maxWeightKg, repsAtMaxWeight, maxWeightDate,
        bestE1rmKg, bestE1rmDate, bestSessionVolumeKg, bestVolumeDate, sessionCount
    )

    private fun WorkoutSetInput.toLegacy() = FitnessRepository.SetInput(
        weightKg, reps, durationSeconds, distanceMeters, assistedWeightKg, addedWeightKg,
        rir, restSeconds, completed, loadState, inputLoadValue, inputLoadUnit
    )

    private fun WorkoutExerciseReplacement.toLegacy() = RoutineExercise(
        masterExerciseId, nameKo, nameEn, bodyPart, equipmentType, equipmentVariantId,
        primarySubPart, recordType, familyIdentity
    )

    private fun requireScope(scope: AccountScope) {
        check(scope.ownerId == legacy.currentUserId()) {
            "The account changed while the workout operation was pending."
        }
    }
}
