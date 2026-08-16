package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiningOutOptionTest {
    @Test
    public void descriptiveOptionDoesNotBecomeZeroNutrition() {
        DiningOutOption option = DiningOutOption.descriptive("면 추가");

        assertFalse(option.hasNutrition());
        assertFalse(option.hasCompleteMacros());
        assertFalse(option.profile.isKnown(NutritionProfile.CALORIES_KCAL));
    }

    @Test
    public void nutrientBearingOptionHasIndependentProfile() {
        DiningOutOption option = DiningOutOption.withProfile(
                "고기 추가",
                NutritionProfile.ofMacros(180, 16, 2, 11)
        );

        assertTrue(option.hasNutrition());
        assertTrue(option.hasCompleteMacros());
        assertTrue(option.profile.isKnown(NutritionProfile.CALORIES_KCAL));
        assertTrue(option.profile.isKnown(NutritionProfile.PROTEIN_GRAMS));
    }
}
