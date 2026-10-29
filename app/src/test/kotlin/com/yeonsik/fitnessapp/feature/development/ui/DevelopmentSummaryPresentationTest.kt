package com.yeonsik.fitnessapp.feature.development.ui

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.development.model.*
import org.junit.Assert.*
import org.junit.Test

class DevelopmentSummaryPresentationTest {
    @Test fun unchangedSetsAreAProposalNotAClaimOfImprovement() {
        assertEquals("3세트 유지 제안", developmentSetSummary(suggestion(3)))
    }

    @Test fun missingProposalDoesNotInventASetCount() {
        assertEquals("최근 세트 구성 확인", developmentSetSummary(suggestion(null)))
    }

    @Test fun comparableRepetitionsShowBothObservedValues() {
        assertEquals("이전 24회 → 최근 27회", developmentComparisonValues(comparison(), MassUnit.KG))
    }

    @Test fun differentConditionsNeverDisplayAnApparentPerformanceDelta() {
        val value = developmentComparisonValues(comparison().copy(
            state = DevelopmentComparisonState.DIFFERENT_CONDITIONS,
            previousValue = null, currentValue = null
        ), MassUnit.KG)
        assertEquals("일반 세트 · 이전 2개 / 최근 2개", value)
        assertFalse(value.contains("→"))
    }

    @Test fun firstRecordDoesNotInventPreviousZero() {
        assertEquals("최근 일반 세트 2개", developmentComparisonValues(comparison().copy(
            state = DevelopmentComparisonState.FIRST_RECORD, previous = null,
            previousValue = null, currentValue = null
        ), MassUnit.KG))
    }

    @Test fun loadsRespectUnitAndDurationDoesNotBecomeRepetitions() {
        val load = developmentComparisonValues(comparison().copy(
            metric = DevelopmentComparisonMetric.LOAD, previousValue = 10.0, currentValue = 20.0
        ), MassUnit.LB)
        assertTrue(load.contains("lb"))
        assertFalse(load.contains("kg"))
        assertEquals("이전 24초 → 최근 27초", developmentComparisonValues(
            comparison().copy(metric = DevelopmentComparisonMetric.DURATION), MassUnit.KG))
    }

    private fun suggestion(count: Int?) = DevelopmentSetSuggestion("squat", "스쿼트",
        DevelopmentSetAdjustment.MAINTAIN, 3, count, "기록 기준", emptyList(), null)

    private fun comparison(): DevelopmentExerciseComparison {
        val sample = DevelopmentPerformanceSample(WorkoutProgressSession(
            "record", "exercise", "2026-10-21", "", null, "스쿼트", "legs",
            "weight_reps", "key", LoadState.EXTERNAL_LOAD, null,
            listOf(
                WorkoutProgressSet("warmup", 0, WorkoutSetType.WARMUP, null, 10.0, 10, 0, null, null),
                WorkoutProgressSet("normal1", 1, WorkoutSetType.NORMAL, null, 30.0, 12, 0, null, null),
                WorkoutProgressSet("normal2", 2, WorkoutSetType.NORMAL, null, 30.0, 12, 0, null, null)
            )
        ))
        return DevelopmentExerciseComparison(DevelopmentComparisonState.COMPARABLE,
            sample, sample, DevelopmentComparisonMetric.REPETITIONS, 24.0, 27.0)
    }
}
