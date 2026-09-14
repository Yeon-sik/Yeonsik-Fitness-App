package com.yeonsik.fitnessapp.feature.workout.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutReadSemanticsTest {
    @Test
    fun activeWorkoutIsExcludedFromCompletedFacts() {
        assertFalse(WorkoutReadSemantics.isCompleted("fitness", "{\"status\":\"in_progress\"}"))
        assertTrue(WorkoutReadSemantics.isCompleted("fitness", "{\"status\":\"completed\"}"))
        assertTrue(WorkoutReadSemantics.isCompleted("os", "{}"))
    }
}
