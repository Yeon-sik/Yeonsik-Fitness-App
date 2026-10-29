package com.yeonsik.fitnessapp.feature.records.ui

import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsWorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsDaySummaryTest {
    @Test fun selectedDateIncludesTheWeekdayAndHandlesYearBoundaries() {
        assertEquals("10월 9일 금요일", recordsSelectedDateLabel("2026-10-09"))
        assertEquals("1월 1일 금요일", recordsSelectedDateLabel("2027-01-01"))
    }

    @Test fun malformedDateIsNotSilentlyReplacedWithToday() {
        assertEquals("unknown", recordsSelectedDateLabel("unknown"))
    }

    @Test fun emptyDateIsNotPresentedAsZeroExercisePerformance() {
        assertEquals("저장된 기록이 없습니다",
            recordsDayCounts(RecordsDayDetail(DATE, emptyList(), emptyList(), emptyList())))
    }

    @Test fun countsUseRecordEntriesRatherThanCompletedSetsOrAverageWeights() {
        val workouts = listOf(
            RecordsWorkoutSummary("a", DATE, "상체", "strength", 600, 300.0, 8, listOf("등")),
            RecordsWorkoutSummary("b", DATE, "하체", "strength", 900, 400.0, 12, listOf("하체"))
        )
        val weights = listOf(BodyReadEntry("c", DATE, 70.0, ""), BodyReadEntry("d", DATE, 71.0, ""))
        assertEquals("운동 2건 · 식단 0건 · 체중 2건",
            recordsDayCounts(RecordsDayDetail(DATE, workouts, weights, emptyList())))
    }

    private companion object { const val DATE = "2026-10-09" }
}
