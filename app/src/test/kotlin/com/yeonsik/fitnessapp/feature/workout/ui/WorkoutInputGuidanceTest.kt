package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutInputBasis
import org.junit.Assert.*
import org.junit.Test

class WorkoutInputGuidanceTest {
    @Test fun pairedImplementsUseOneImplementAndUnilateralRepetitionsUseOneSide() {
        val paired = workoutInputGuidance("weight_reps", WorkoutInputBasis("bilateral", 2), MassUnit.KG)
        assertTrue(paired.contains("기구 1개당 중량 (kg)"))
        assertTrue(paired.contains("기구 개수는 볼륨 계산에 반영"))
        val oneSide = workoutInputGuidance("weight_reps", WorkoutInputBasis("unilateral", 1), MassUnit.LB)
        assertTrue(oneSide.contains("한쪽 중량 (lb)"))
        assertTrue(oneSide.contains("한쪽당 반복 횟수"))
        assertTrue(oneSide.contains("볼륨 계산에 양쪽을 반영"))
    }

    @Test fun unknownOrMixedUnitRecordsDoNotInventCatalogOrBarWeightRules() {
        val unknown = workoutInputGuidance("weight_reps", WorkoutInputBasis(), null)
        assertTrue(unknown.contains("각 칸에 표시된 단위"))
        assertTrue(unknown.contains("입력 기준을 확인하지 못한 종목"))
        assertFalse(unknown.contains("한쪽당"))
        assertFalse(unknown.contains("봉 포함"))
    }

    @Test fun timedAndAssistedInputsDescribeTheActualFields() {
        assertEquals("시간은 초 단위로 입력하세요.", workoutInputGuidance("time", WorkoutInputBasis(), MassUnit.KG))
        assertTrue(workoutInputGuidance("weight_time", WorkoutInputBasis("bilateral", 1), MassUnit.LB)
            .contains("외부 중량 (lb) · 시간 (초)"))
        assertTrue(workoutInputGuidance("assisted_weight_reps", WorkoutInputBasis("bilateral", 1), MassUnit.KG)
            .contains("보조받는 중량 (kg)"))
        assertTrue(workoutInputGuidance("bodyweight_added_weight_reps", WorkoutInputBasis("bilateral", 1), MassUnit.KG)
            .contains("몸무게를 뺀 추가 중량 (kg)"))
    }
}
