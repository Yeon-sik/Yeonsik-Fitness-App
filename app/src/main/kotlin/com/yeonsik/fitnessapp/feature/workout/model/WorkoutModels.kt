package com.yeonsik.fitnessapp.feature.workout.model

/** Android- and storage-independent values consumed by the workout feature. */
data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val name: String,
    val recordType: String
)

data class WorkoutSet(
    val id: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val durationSeconds: Int,
    val completed: Boolean
)

data class WorkoutExerciseDetail(
    val recordId: String,
    val activeExercise: WorkoutExercise,
    val exercises: List<WorkoutExercise>,
    val sets: List<WorkoutSet>
)
