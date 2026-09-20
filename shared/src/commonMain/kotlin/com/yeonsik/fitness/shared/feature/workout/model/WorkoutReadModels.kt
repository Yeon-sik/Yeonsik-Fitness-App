package com.yeonsik.fitness.shared.feature.workout.model

data class WorkoutReadDayMetrics(
    val sessionCount: Int,
    val totalSetCount: Int,
    val totalVolumeKg: Double,
    val totalDurationSeconds: Int
)
