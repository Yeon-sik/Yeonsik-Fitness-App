package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MassUnitTest {
    @Test
    public void usesTheExactPoundToKilogramConversion() {
        assertEquals(0.45359237d, MassUnit.KILOGRAMS_PER_POUND, 0d);
        assertEquals(102.05828325d, MassUnit.toKg(225d, MassUnit.LB), 1e-12d);
        assertEquals(225d, MassUnit.fromKg(102.05828325d, MassUnit.LB), 1e-12d);
    }

    @Test
    public void convertsAndRoundTripsWithoutChangingThePhysicalLoad() {
        double pounds = 187.5d;
        double kilograms = MassUnit.toKg(pounds, MassUnit.LB);
        assertEquals(kilograms, MassUnit.convert(pounds, MassUnit.LB, MassUnit.KG), 0d);
        assertEquals(pounds, MassUnit.convert(kilograms, MassUnit.KG, MassUnit.LB), 1e-12d);
    }
}
