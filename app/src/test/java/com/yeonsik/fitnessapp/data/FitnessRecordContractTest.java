package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.ExercisePerformanceKey;
import com.yeonsik.fitnessapp.exercise.LoadState;

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
                        2,
                        90,
                        true
                )
        );
    }

    @Test
    public void completedStrengthSetAcceptsRirZero() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.WEIGHT_REPS,
                new FitnessRepository.SetInput(
                        45d,
                        8,
                        null,
                        null,
                        null,
                        0,
                        60,
                        true
                )
        );
    }

    @Test
    public void completedBodyweightAddedWeightSetAcceptsZeroAddedWeight() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS,
                new FitnessRepository.SetInput(
                        null,
                        8,
                        null,
                        null,
                        0d,
                        null,
                        60,
                        true
                )
        );
    }

    @Test
    public void performanceKeyDoesNotCompareDifferentVariantsOrLoadStates() {
        ExerciseFamilyIdentity barbell = identity("barbell_back_squat");
        ExerciseFamilyIdentity dumbbell = identity("dumbbell_back_squat");
        ExercisePerformanceKey barbellExternal = barbell.performanceKey(LoadState.EXTERNAL_LOAD);
        assertEquals(true, barbellExternal.isComparableTo(
                barbell.performanceKey(LoadState.EXTERNAL_LOAD)));
        assertEquals(false, barbellExternal.isComparableTo(
                dumbbell.performanceKey(LoadState.EXTERNAL_LOAD)));
        assertEquals(false, barbellExternal.isComparableTo(
                barbell.performanceKey(LoadState.BODYWEIGHT)));
    }

    private static ExerciseFamilyIdentity identity(String variant) {
        return new ExerciseFamilyIdentity(
                variant,
                "squat",
                variant,
                variant,
                "스쿼트",
                "Squat",
                "스쿼트",
                "Squat",
                "legs",
                "{\"equipment\":\"" + variant + "\"}",
                "{\"equipment\":\"" + variant + "\"}",
                variant,
                "external_load",
                FitnessRecordContract.WEIGHT_REPS,
                null
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

    @Test
    public void acceptsAndNormalizesIsoBodyMetricDate() {
        assertEquals("2026-08-10", FitnessRepository.requireRecordDate(" 2026-08-10 "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleBodyMetricDate() {
        FitnessRepository.requireRecordDate("2026-02-30");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBodyWeight() {
        FitnessRepository.requireBodyWeight(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteBodyWeight() {
        FitnessRepository.requireBodyWeight(Double.NaN);
    }
}
