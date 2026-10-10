package com.yeonsik.fitnessapp.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.data.FitnessRecordContract

/** Same catalog variant across legacy IDs and aliases; load state belongs to each set. */
@ConsistentCopyVisibility
data class WorkoutExerciseSelectionIdentity private constructor(
    val familyId: String?,
    val canonicalVariantKey: String?,
    val legacyExerciseId: String?,
    val manualName: String?,
    val recordType: String
) {
    companion object {
        fun from(
            exerciseId: String,
            name: String,
            recordType: String,
            identity: ExerciseFamilyIdentity?
        ): WorkoutExerciseSelectionIdentity {
            val hasVariant = identity?.hasVariantIdentity() == true
            return WorkoutExerciseSelectionIdentity(
                familyId = identity?.familyId.takeIf { hasVariant },
                canonicalVariantKey = identity?.canonicalVariantKey.takeIf { hasVariant },
                legacyExerciseId = exerciseId.takeUnless { hasVariant },
                manualName = name.trim().takeIf { !hasVariant && exerciseId == "manual" },
                recordType = FitnessRecordContract.normalizeRecordType(recordType)
            )
        }

        fun from(exercise: WorkoutExerciseReplacement): WorkoutExerciseSelectionIdentity =
            from(exercise.masterExerciseId.ifBlank { "manual" }, exercise.nameKo,
                exercise.recordType, exercise.familyIdentity)
    }
}
