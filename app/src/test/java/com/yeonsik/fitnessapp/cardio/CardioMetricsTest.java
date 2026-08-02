package com.yeonsik.fitnessapp.cardio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CardioMetricsTest {
    @Test
    public void formatsElapsedDistancePaceAndCyclingSpeed() {
        assertEquals("01:01:01", CardioMetrics.formatElapsed(3_661));
        assertEquals("5.00", CardioMetrics.formatDistanceKilometers(5_000d));
        assertEquals("6:00", CardioMetrics.formatAveragePace(1_800, 5_000d));
        assertEquals("20.0", CardioMetrics.formatAverageSpeed(1_800, 10_000d));
    }

    @Test
    public void hidesPerformanceMetricUntilDistanceIsMeaningful() {
        assertEquals("--:--", CardioMetrics.formatAveragePace(60, 10d));
        assertEquals("--", CardioMetrics.formatAverageSpeed(60, 10d));
    }
}
