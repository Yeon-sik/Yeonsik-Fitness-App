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
