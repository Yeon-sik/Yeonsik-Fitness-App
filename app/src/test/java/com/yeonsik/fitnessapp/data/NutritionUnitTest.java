package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NutritionUnitTest {
    @Test
    public void normalizesSupportedUnitsAndAliases() {
        assertEquals(NutritionUnit.GRAM, NutritionUnit.normalize("GRAM"));
        assertEquals(NutritionUnit.MILLIGRAM, NutritionUnit.normalize("milligrams"));
        assertEquals(NutritionUnit.KILOGRAM, NutritionUnit.normalize("kg"));
        assertEquals(NutritionUnit.MILLILITER, NutritionUnit.normalize("ml"));
        assertEquals(NutritionUnit.LITER, NutritionUnit.normalize("liter"));
        assertTrue(NutritionUnit.isSupported("g"));
        assertTrue(NutritionUnit.isSupported("mg"));
        assertTrue(NutritionUnit.isSupported("kg"));
        assertTrue(NutritionUnit.isSupported("ml"));
        assertTrue(NutritionUnit.isSupported("L"));
        assertTrue(NutritionUnit.isSupported("serving"));
    }

    @Test
    public void exposesEverySupportedUnitAsADefensiveChoiceList() {
        String[] expected = {"g", "mg", "kg", "ml", "L", "serving", "개", "portion", "pack"};

        String[] options = NutritionUnit.options();

        assertArrayEquals(expected, options);
        for (String option : options) {
            assertTrue(NutritionUnit.isSupported(option));
        }
        options[0] = "changed";
        assertEquals("g", NutritionUnit.options()[0]);
    }

    @Test
    public void convertsMassAndVolumeWithoutMixingDimensions() {
        assertEquals(210, NutritionUnit.convert(0.21, NutritionUnit.KILOGRAM, NutritionUnit.GRAM), 0.0001);
        assertEquals(210000, NutritionUnit.convert(210, NutritionUnit.GRAM, NutritionUnit.MILLIGRAM), 0.0001);
        assertEquals(1000, NutritionUnit.convert(1, NutritionUnit.LITER, NutritionUnit.MILLILITER), 0.0001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMassAndVolumeConversion() {
        NutritionUnit.convert(1, NutritionUnit.GRAM, NutritionUnit.MILLILITER);
    }
}
