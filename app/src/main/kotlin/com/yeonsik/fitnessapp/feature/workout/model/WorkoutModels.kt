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

data class WorkoutSessionSnapshot(
    val recordId: String,
    val title: String,
    val status: String,
    val startedAt: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val completedSetCount: Int,
    val exercises: List<WorkoutSessionExercise>,
    val recentVolumes: List<WorkoutVolumePoint>
)

data class WorkoutSessionExercise(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val name: String,
    val uiPart: String,
    val equipment: String,
    val recordType: String,
    val completedSetCount: Int,
    val totalSetCount: Int
)

data class WorkoutVolumePoint(
    val date: String,
    val label: String,
    val volumeKg: Double
)
