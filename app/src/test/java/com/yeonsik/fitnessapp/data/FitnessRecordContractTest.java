package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FitnessRecordContractTest {
    @Test
    public void normalizesLegacyRecordType() {
        assertEquals(
                FitnessRecordContract.WEIGHT_REPS,
                FitnessRecordContract.normalizeRecordType("sets_reps_weight")
        );
    }

    @Test
    public void mapsSharedStrengthCategoryToStableCode() {
        assertEquals("chest", FitnessRecordContract.categoryCode("가슴운동"));
        assertEquals("back", FitnessRecordContract.categoryCode("back"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void completedWeightSetRequiresPositiveWeight() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.WEIGHT_REPS,
                new FitnessRepository.SetInput(
                        null,
                        8,
                        null,
                        null,
                        null,
                        8,
                        90,
                        true
                )
        );
    }

    @Test
    public void completedTimeSetAcceptsDurationAndOptionalRpe() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.TIME,
                new FitnessRepository.SetInput(
                        null,
                        null,
                        45,
                        null,
                        null,
                        9,
                        60,
                        true
                )
        );
    }

    @Test
    public void normalizesMealMenuPresetName() {
        assertEquals("닭가슴살 샐러드",
                FitnessRepository.normalizeMealMenuPresetName("  닭가슴살 샐러드  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyMealMenuPresetName() {
        FitnessRepository.normalizeMealMenuPresetName("   ");
    }
}
