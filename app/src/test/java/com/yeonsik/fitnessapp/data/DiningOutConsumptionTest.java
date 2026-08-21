package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DiningOutConsumptionTest {
    @Test
    public void blankShareDefaultsToEqualDiners() {
        DiningOutConsumption consumption = DiningOutConsumption.resolve(2, null);

        assertEquals(2, consumption.dinerCount);
        assertEquals(0.5d, consumption.consumedFraction, 0.0001d);
        assertEquals(
                DiningOutConsumption.SHARE_METHOD_EQUAL_BY_DINERS,
                consumption.shareMethod
        );
    }

    @Test
    public void manualShareIsStoredAsFractionOfWholeMenu() {
        DiningOutConsumption consumption = DiningOutConsumption.manual(3, 0.35d);

        assertEquals(3, consumption.dinerCount);
        assertEquals(0.35d, consumption.consumedFraction, 0.0001d);
        assertEquals(DiningOutConsumption.SHARE_METHOD_MANUAL, consumption.shareMethod);
    }

    @Test
    public void invalidShareDoesNotEnterTheContract() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiningOutConsumption.manual(2, 1.01d)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiningOutConsumption.equalByDiners(0)
        );
    }
}
