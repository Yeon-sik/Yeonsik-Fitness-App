package com.yeonsik.fitnessapp.cardio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void validatesAndFormatsManuallyEnteredAverageHeartRate() {
        assertTrue(CardioMetrics.isValidAverageHeartRate(null));
        assertTrue(CardioMetrics.isValidAverageHeartRate(140));
        assertFalse(CardioMetrics.isValidAverageHeartRate(0));
        assertTrue(CardioMetrics.hasAverageHeartRate(140d));
        assertFalse(CardioMetrics.hasAverageHeartRate(0d));
        assertEquals("140", CardioMetrics.formatAverageHeartRate(140d));
        assertEquals("140.5", CardioMetrics.formatAverageHeartRate(140.5d));
        assertEquals("—", CardioMetrics.formatAverageHeartRate(null));
    }
}
