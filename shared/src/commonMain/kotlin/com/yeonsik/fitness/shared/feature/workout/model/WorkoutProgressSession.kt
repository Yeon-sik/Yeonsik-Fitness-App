package com.yeonsik.fitness.shared.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState

/** Read-only completed occurrence. Missing identity/load is deliberately not a comparison key. */
data class WorkoutProgressSession(
    val recordId: String,
    val exerciseOccurrenceId: String,
    val date: String,
    val performedAt: String,
    val routineId: String?,
    val exerciseName: String,
    val bodyPart: String,
    val recordType: String,
    val performanceKey: String?,
    val loadState: LoadState?,
    val groupType: WorkoutExerciseGroupType?,
    val sets: List<WorkoutProgressSet>
)

data class WorkoutProgressSet(
    val id: String,
    val setIndex: Int,
    val type: WorkoutSetType,
    val dropParentSetId: String?,
    val loadKg: Double?,
    val repetitions: Int,
    val durationSeconds: Int,
    val rir: Int?,
    val restSeconds: Int?
)
