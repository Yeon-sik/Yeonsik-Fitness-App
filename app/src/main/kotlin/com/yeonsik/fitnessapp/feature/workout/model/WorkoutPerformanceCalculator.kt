package com.yeonsik.fitnessapp.feature.workout.model

/** Workout-owned performance facts.  The Epley estimate is only defined for positive load/reps. */
object WorkoutPerformanceCalculator {
    fun epleyE1rm(loadKg: Double, reps: Int): Double {
        if (!loadKg.isFinite() || loadKg <= 0.0 || reps <= 0) return 0.0
        return loadKg * (1.0 + reps / 30.0)
    }
}
