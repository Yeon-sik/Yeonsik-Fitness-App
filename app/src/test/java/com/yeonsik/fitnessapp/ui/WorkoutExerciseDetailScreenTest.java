package com.yeonsik.fitnessapp.ui;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class WorkoutExerciseDetailScreenTest {
    @Test
    public void sumsAllEnteredSetVolumesBeforeComparing() {
        assertEquals(
                700d,
                WorkoutExerciseDetailScreen.sumVolumeKg(Arrays.asList(200d, 500d)),
                0.001d
        );
    }

    @Test
    public void comparisonMessageUsesTheWholeSetDelta() {
        assertEquals(
                "전체 세트 기준, 지난 운동보다 120 KG 덜 들었어요",
                WorkoutExerciseDetailScreen.totalVolumeComparisonMessage(700d, 820d)
        );
    }

    @Test
    public void comparisonMessageHandlesEqualVolumeWithoutAFalseDirection() {
        assertEquals(
                "전체 세트 기준, 지난 운동과 같은 볼륨이에요",
                WorkoutExerciseDetailScreen.totalVolumeComparisonMessage(820d, 820d)
        );
    }
}
