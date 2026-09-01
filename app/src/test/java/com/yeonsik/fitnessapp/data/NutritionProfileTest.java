package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NutritionProfileTest {
    @Test
    public void distinguishesMeasuredZeroFromUnknown() {
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.SUGARS_GRAMS, 0.0)
                .build();

        assertTrue(profile.isKnown(NutritionProfile.SUGARS_GRAMS));
        assertEquals(0, profile.sugarsGrams(), 0.001);

        assertFalse(profile.isKnown(NutritionProfile.SODIUM_MG));
        assertNull(profile.sodiumMg());
        assertEquals(0, profile.valueOrZero(NutritionProfile.SODIUM_MG), 0.001);
    }

    @Test
    public void nullValueLeavesNutrientUnknownRatherThanZero() {
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.FIBER_GRAMS, null)
                .build();

        assertFalse(profile.isKnown(NutritionProfile.FIBER_GRAMS));
        assertNull(profile.fiberGrams());
    }

    @Test
    public void reportsMissingRequiredNutrients() {
        NutritionProfile macrosOnly = NutritionProfile.ofMacros(100, 10, 5, 3);

        assertFalse(macrosOnly.hasAllRequired());
        List<String> missing = macrosOnly.missingRequiredKeys();
        assertEquals(3, missing.size());
        assertTrue(missing.contains(NutritionProfile.SODIUM_MG));
        assertTrue(missing.contains(NutritionProfile.SATURATED_FAT_GRAMS));
        assertTrue(missing.contains(NutritionProfile.SUGARS_GRAMS));
    }

    @Test
    public void completeProfileSatisfiesRequiredNutrients() {
        assertTrue(NutritionCalculatorTest.fullyMeasuredChicken().profile.hasAllRequired());
    }

    @Test
    public void scalingPreservesUnknownNutrients() {
        NutritionProfile scaled = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 100.0)
                .micronutrient(NutrientCode.POTASSIUM, 250.0)
                .build()
                .scaled(0.5);

        assertEquals(50, scaled.calories(), 0.001);
        assertEquals(125, scaled.micronutrient(NutrientCode.POTASSIUM), 0.001);
        assertNull(scaled.sodiumMg());
        assertFalse(scaled.isKnown(NutritionProfile.SODIUM_MG));
    }

    @Test
    public void listsKnownMicronutrientsInCatalogOrder() {
        NutritionProfile profile = NutritionProfile.builder()
                .micronutrient(NutrientCode.VITAMIN_C, 10.0)
                .micronutrient(NutrientCode.CALCIUM, 20.0)
                .build();

        // 입력 순서가 아니라 사전 정의 순서(미네랄 → 비타민)를 따른다.
        assertEquals(
                java.util.Arrays.asList(NutrientCode.CALCIUM, NutrientCode.VITAMIN_C),
                profile.knownMicronutrientCodes()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownNutrientKey() {
        NutritionProfile.builder().value("unobtainium_mg", 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeNutrient() {
        NutritionProfile.builder().value(NutritionProfile.SODIUM_MG, -1.0);
    }

    @Test
    public void exposesUnitsAndLabelsForEveryStoredNutrient() {
        assertEquals("mg", NutritionProfile.unitOf(NutritionProfile.SODIUM_MG));
        assertEquals("g", NutritionProfile.unitOf(NutritionProfile.FIBER_GRAMS));
        assertEquals("ug", NutritionProfile.unitOf(NutrientCode.VITAMIN_D));
        assertEquals("µg", NutrientCode.displayUnit(NutrientCode.unitOf(NutrientCode.VITAMIN_D)));
        assertEquals("나트륨", NutritionProfile.labelOf(NutritionProfile.SODIUM_MG));
        assertEquals("칼슘", NutritionProfile.labelOf(NutrientCode.CALCIUM));
    }

    @Test
    public void exposesTheSharedPrimaryNutritionDisplayOrder() {
        assertEquals(
                java.util.Arrays.asList(
                        NutritionProfile.CALORIES_KCAL,
                        NutritionProfile.CARBS_GRAMS,
                        NutritionProfile.PROTEIN_GRAMS,
                        NutritionProfile.FAT_GRAMS,
                        NutritionProfile.SUGARS_GRAMS,
                        NutritionProfile.SATURATED_FAT_GRAMS,
                        NutritionProfile.SODIUM_MG
                ),
                NutritionProfile.PRIMARY_DISPLAY_ORDER
        );
    }

    @Test
    public void catalogCoversRequestedMineralsAndVitamins() {
        for (String code : new String[]{
                NutrientCode.CALCIUM,
                NutrientCode.IRON,
                NutrientCode.MAGNESIUM,
                NutrientCode.POTASSIUM,
                NutrientCode.ZINC,
                NutrientCode.VITAMIN_A,
                NutrientCode.VITAMIN_B1,
                NutrientCode.VITAMIN_B12,
                NutrientCode.VITAMIN_C,
                NutrientCode.VITAMIN_D,
                NutrientCode.VITAMIN_E,
                NutrientCode.VITAMIN_K
        }) {
            assertTrue(code, NutrientCode.isKnown(code));
            assertTrue(code, NutritionProfile.isSupportedKey(code));
        }
        assertEquals(10, NutrientCode.group(NutrientCode.GROUP_MINERAL).size());
        assertEquals(13, NutrientCode.group(NutrientCode.GROUP_VITAMIN).size());
    }
}
