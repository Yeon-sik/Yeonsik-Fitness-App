package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ExerciseVolumeCalculatorTest {
    @Test
    public void usesOneMultiplierForBilateralSingleImplementExercises() {
        assertEquals(
                160d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.EXTERNAL_LOAD,
                        20d,
                        0d,
                        8,
                        "bilateral",
                        1
                ),
                0.001d
        );
    }

    @Test
    public void appliesSideMultiplierToUnilateralExercises() {
        assertEquals(
                160d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.EXTERNAL_LOAD,
                        10d,
                        0d,
                        8,
                        "unilateral",
                        1
                ),
                0.001d
        );
    }

    @Test
    public void appliesTwoIndependentImplementMultiplierToDumbbellExercises() {
        assertEquals(
                320d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.EXTERNAL_LOAD,
                        20d,
                        0d,
                        8,
                        "bilateral",
                        2
                ),
                0.001d
        );
    }

    @Test
    public void combinesSideAndImplementMultipliersForUnilateralTwoImplementExercises() {
        assertEquals(
                320d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.EXTERNAL_LOAD,
                        10d,
                        0d,
                        8,
                        "unilateral",
                        2
                ),
                0.001d
        );
    }

    @Test
    public void doesNotTurnBodyweightOrAssistedLoadsIntoKilogramVolume() {
        assertEquals(
                0d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.ASSISTED,
                        0d,
                        0d,
                        8,
                        "bilateral",
                        2
                ),
                0.001d
        );
        assertEquals(
                0d,
                ExerciseVolumeCalculator.calculate(
                        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS,
                        LoadState.BODYWEIGHT,
                        0d,
                        0d,
                        8,
                        "bilateral",
                        1
                ),
                0.001d
        );
    }

    @Test
    public void describesOnlyTheEffectiveMultiplier() {
        assertEquals("중량 × 횟수", ExerciseVolumeCalculator.formulaLabel("bilateral", 1));
        assertEquals("중량 × 횟수 × 2", ExerciseVolumeCalculator.formulaLabel("bilateral", 2));
        assertEquals("중량 × 횟수 × 4", ExerciseVolumeCalculator.formulaLabel("unilateral", 2));
    }
}
