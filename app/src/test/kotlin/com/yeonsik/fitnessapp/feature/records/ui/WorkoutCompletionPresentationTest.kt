package com.yeonsik.fitnessapp.feature.records.ui

import com.yeonsik.fitnessapp.feature.workout.ui.formatWorkoutCompletionDateTime
import com.yeonsik.fitnessapp.feature.workout.ui.workoutCompletionStatusLabel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class WorkoutCompletionPresentationTest {
    @Test
    fun completionTimestampUses24HourLocalTimeAndOmitsSeconds() {
        val zone = ZoneId.of("+09:00")

        assertEquals(
            "2026-10-02 17:05",
            formatWorkoutCompletionDateTime("2026-10-02T08:05:59Z", zone)
        )
        assertEquals(
            "2026-10-02 00:08",
            formatWorkoutCompletionDateTime("2026-10-02T00:08:49+09:00", zone)
        )
        assertNull(formatWorkoutCompletionDateTime("", zone))
        assertNull(formatWorkoutCompletionDateTime("not-a-timestamp", zone))
    }

    @Test
    fun completedDetailLabelKeepsUnknownCompletionTimeExplicit() {
        assertEquals("완료된 운동 · 완료 시각 미기록", workoutCompletionStatusLabel(null))
        assertEquals("완료된 운동 · 완료 시각 미기록", workoutCompletionStatusLabel("invalid"))
        assertEquals(
            "완료된 운동 · 2026-10-03 00:08",
            workoutCompletionStatusLabel("2026-10-03T00:08:49")
        )
    }
}
