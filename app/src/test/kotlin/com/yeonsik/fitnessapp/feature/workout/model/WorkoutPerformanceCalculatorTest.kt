package com.yeonsik.fitnessapp.feature.workout.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPerformanceCalculatorTest {
    @Test
    fun epleyUsesTheHighestEstimateInsteadOfTheHeaviestSet() {
        val heavySingle = WorkoutPerformanceCalculator.epleyE1rm(100.0, 1)
        val lighterHighRep = WorkoutPerformanceCalculator.epleyE1rm(80.0, 10)

        assertEquals(106.6666667, lighterHighRep, 0.0001)
        assertEquals(heavySingle, 103.3333333, 0.0001)
        assertTrue(lighterHighRep > heavySingle)
    }

    @Test
    fun invalidLoadOrRepsHaveNoEstimate() {
        assertEquals(0.0, WorkoutPerformanceCalculator.epleyE1rm(0.0, 10), 0.0)
        assertEquals(0.0, WorkoutPerformanceCalculator.epleyE1rm(50.0, 0), 0.0)
    }
}
