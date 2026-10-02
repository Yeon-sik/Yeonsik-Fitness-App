package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MassFormatterTest {
    @Test
    public void displayValuesUseAtMostOneFractionDigit() {
        assertEquals("120kg", MassFormatter.withUnit(120.0000001d, MassUnit.KG));
        assertEquals("120.3kg", MassFormatter.withUnit(120.26d, MassUnit.KG));
        assertEquals("264.6lb", MassFormatter.withUnit(120d, MassUnit.LB));
    }

    @Test
    public void editableInputKeepsItsExistingTwoFractionDigitPrecision() {
        assertEquals("120.26", MassFormatter.formatInput(120.26d, MassUnit.KG));
    }
}
