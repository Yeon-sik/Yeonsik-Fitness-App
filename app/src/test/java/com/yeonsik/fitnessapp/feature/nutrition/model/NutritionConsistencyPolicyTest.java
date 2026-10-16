package com.yeonsik.fitnessapp.feature.nutrition.model;

import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class NutritionConsistencyPolicyTest {
    private NutritionProfile.Builder label() {
        return NutritionProfile.builder().value(NutritionProfile.CARBS_GRAMS, 43d)
                .value(NutritionProfile.SUGARS_GRAMS, 8d).value(NutritionProfile.FAT_GRAMS, 28d)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 9d).value(NutritionProfile.TRANS_FAT_GRAMS, 0.4d);
    }

    @Test public void acceptsLabelComponentsIncludedInTotals() {
        NutritionConsistencyPolicy.validate(label().build());
    }

    @Test public void rejectsSugarAboveTotalCarbohydrateAtDiningWriteBoundary() {
        assertEquals("당류는 탄수화물 총량을 초과할 수 없습니다.",
                assertThrows(IllegalArgumentException.class, () -> MealEntryPolicy.requireDiningOutMenuNutrition(
                        600, 20d, 43d, 28d, null, 50d, 9d, 0.4d)).getMessage());
    }

    @Test public void rejectsAddedSugarAboveKnownSugar() {
        assertThrows(IllegalArgumentException.class, () -> NutritionConsistencyPolicy.validate(
                label().value(NutritionProfile.ADDED_SUGARS_GRAMS, 9d).build()));
    }

    @Test public void rejectsSaturatedFatAboveTotalFatAtDiningWriteBoundary() {
        assertThrows(IllegalArgumentException.class, () -> MealEntryPolicy.requireDiningOutMenuNutrition(
                600, 20d, 43d, 28d, null, 8d, 30d, 0.4d));
    }

    @Test public void rejectsTransFatAboveTotalFatAtDiningWriteBoundary() {
        assertThrows(IllegalArgumentException.class, () -> MealEntryPolicy.requireDiningOutMenuNutrition(
                600, 20d, 43d, 28d, null, 8d, 9d, 30d));
    }

    @Test public void unknownChildrenAndMeasuredZeroRemainDistinct() {
        NutritionProfile unknown = NutritionProfile.ofMacros(600d, 20d, 43d, 28d);
        NutritionConsistencyPolicy.validate(unknown);
        assertNull(unknown.sugarsGrams()); assertNull(unknown.addedSugarsGrams());
        assertNull(unknown.saturatedFatGrams()); assertNull(unknown.transFatGrams());
        NutritionProfile measuredZero = NutritionProfile.builder().from(unknown)
                .value(NutritionProfile.SUGARS_GRAMS, 0d).value(NutritionProfile.TRANS_FAT_GRAMS, 0d).build();
        NutritionConsistencyPolicy.validate(measuredZero);
        assertEquals(0d, measuredZero.sugarsGrams(), 0d);
        assertEquals(0d, measuredZero.transFatGrams(), 0d);
        Map<String, Double> nullable = new LinkedHashMap<>(measuredZero.asMap());
        nullable.put(NutritionProfile.SUGARS_GRAMS, null);
        NutritionConsistencyPolicy.validateKnownNutrients(nullable);
        assertNull(nullable.get(NutritionProfile.SUGARS_GRAMS));
    }

    @Test public void unknownParentsAllowPartialProfilesWithoutRecomputingThem() {
        NutritionProfile partial = NutritionProfile.builder().value(NutritionProfile.SUGARS_GRAMS, 50d)
                .value(NutritionProfile.ADDED_SUGARS_GRAMS, 9d)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 30d)
                .value(NutritionProfile.TRANS_FAT_GRAMS, 30d).build();
        NutritionConsistencyPolicy.validate(partial);
        assertNull(partial.value(NutritionProfile.CARBS_GRAMS));
        assertNull(partial.value(NutritionProfile.FAT_GRAMS));
        NutritionConsistencyPolicy.validate(NutritionProfile.builder()
                .value(NutritionProfile.ADDED_SUGARS_GRAMS, 9d).build());
    }

    @Test public void totalsSumIdenticalKeysWithoutAddingTheirChildren() {
        NutritionTotals totals = NutritionTotals.builder().add(label().build()).build();
        assertEquals(43d, totals.carbsGrams(), 0d);
        assertEquals(28d, totals.fatGrams(), 0d);
        assertEquals(8d, totals.total(NutritionProfile.SUGARS_GRAMS).completeValue(), 0d);
        assertEquals(9d, totals.total(NutritionProfile.SATURATED_FAT_GRAMS).completeValue(), 0d);
        assertEquals(0.4d, totals.total(NutritionProfile.TRANS_FAT_GRAMS).completeValue(), 0d);
    }

    @Test public void acceptsEqualComponentsAndDoesNotEnforceCombinedFatOrFiberRules() {
        NutritionConsistencyPolicy.validate(label().value(NutritionProfile.SUGARS_GRAMS, 43d)
                .value(NutritionProfile.ADDED_SUGARS_GRAMS, 43d)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 28d)
                .value(NutritionProfile.TRANS_FAT_GRAMS, 28d)
                .value(NutritionProfile.FIBER_GRAMS, 50d).build());
    }

    @Test public void legacyProfilesCanStillBeConstructedAndReadBeforeWriteValidation() {
        NutritionProfile legacy = label().value(NutritionProfile.SUGARS_GRAMS, 50d).build();
        assertEquals(50d, legacy.sugarsGrams(), 0d);
        assertThrows(IllegalArgumentException.class, () -> NutritionConsistencyPolicy.validate(legacy));
    }
}
