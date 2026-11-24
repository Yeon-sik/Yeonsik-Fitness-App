package com.yeonsik.fitnessapp.feature.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutReadSemanticsTest {
    @Test
    fun recordedStartResolvesLegacyTimesAndUsesOnlyTheDateForUnreportedTimes() {
        val date = "2001-05-15"
        val seoulMidnight = "2001-05-14T15:00Z"
        val reported = "2001-05-15T10:04+09:00"
        assertEquals("2001-05-15T07:03:18", WorkoutReadSemantics.recordedStartAt(date, "7:03:18", false, null))
        assertEquals(date, WorkoutReadSemantics.recordedStartAt(date, "", false, null))
        assertEquals(date, WorkoutReadSemantics.recordedStartAt(date, seoulMidnight, true, null))
        assertEquals(date, WorkoutReadSemantics.recordedStartAt(date, seoulMidnight, true, false))
        assertEquals(seoulMidnight, WorkoutReadSemantics.recordedStartAt(date, seoulMidnight, true, true))
        assertEquals(reported, WorkoutReadSemantics.recordedStartAt(date, reported, true, null))
    }

    @Test
    fun activeWorkoutIsExcludedFromCompletedFacts() {
        assertFalse(WorkoutReadSemantics.isCompleted("fitness", "{\"status\":\"in_progress\"}"))
        assertTrue(WorkoutReadSemantics.isCompleted("fitness", "{\"status\":\"completed\"}"))
        assertTrue(WorkoutReadSemantics.isCompleted("os", "{}"))
    }
}
