package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class DiningOutOptionTest {
    @Test
    public void descriptiveOptionDoesNotBecomeZeroNutrition() {
        DiningOutOption option = DiningOutOption.descriptive("면 추가");

        assertFalse(option.hasNutrition());
        assertFalse(option.hasCompleteMacros());
        assertFalse(option.profile.isKnown(NutritionProfile.CALORIES_KCAL));
        assertEquals(
                NutritionFood.DATA_VERSION_MACROS_ONLY,
                option.asMealCompositionItem("user", "식당").food.dataVersion
        );
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

    @Test
    public void optionNutritionUsesItsIndependentConsumedFraction() {
        DiningOutOption option = DiningOutOption.withProfile(
                "왕새우튀김",
                NutritionProfile.ofMacros(500, 20, 40, 25)
        ).withConsumedFraction(0.4d);

        assertEquals(0.4d, option.consumedFraction, 0.0001d);
        assertEquals(200d, option.consumedProfile().calories(), 0.0001d);
        assertEquals(8d, option.consumedProfile().proteinGrams(), 0.0001d);
    }

    @Test
    public void optionConsumedFractionMustBeWithinOneHundredPercent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiningOutOption.withProfile(
                        "왕새우튀김",
                        NutritionProfile.ofMacros(500, 20, 40, 25)
                ).withConsumedFraction(1.01d)
        );
    }
}
