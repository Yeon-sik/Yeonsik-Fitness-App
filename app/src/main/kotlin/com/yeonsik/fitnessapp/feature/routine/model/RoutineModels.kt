package com.yeonsik.fitnessapp.feature.routine.model

import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity

/** Values owned by the routine feature, independent of the legacy repository. */
data class RoutineSummary(
    @JvmField val id: String,
    @JvmField val name: String,
    @JvmField val exerciseCount: Int
)

data class RoutineExerciseInstance(
    @JvmField val id: String,
    @JvmField val exerciseId: String,
    @JvmField val nameKo: String,
    @JvmField val uiPart: String,
    @JvmField val primarySubPart: String,
    @JvmField val equipment: String,
    @JvmField val recordType: String,
    @JvmField val order: Int,
    @JvmField val familyIdentity: ExerciseFamilyIdentity?
)

/**
 * Feature-owned input for adding an exercise to a routine.
 *
 * The repository adapter translates these stable values to the legacy exercise
 * catalog only at the Java implementation boundary.
 */
data class RoutineExerciseDraft(
    @JvmField val exerciseId: String,
    @JvmField val nameKo: String,
    @JvmField val nameEn: String,
    @JvmField val bodyPartId: String?,
    @JvmField val equipmentVariantId: String?,
    @JvmField val primarySubPart: String?,
    @JvmField val recordType: String?,
    @JvmField val familyIdentity: ExerciseFamilyIdentity?
)
