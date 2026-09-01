package com.yeonsik.fitnessapp.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorkoutSummaryAnalyticsTest {
    @Test
    public void intensityUsesAbsoluteSetSaturation() {
        assertEquals(0.2212d,
                WorkoutSummaryAnalytics.intensityForEffectiveSets(1d), 0.0001d);
        assertEquals(0.6321d,
                WorkoutSummaryAnalytics.intensityForEffectiveSets(4d), 0.0001d);
        assertTrue(WorkoutSummaryAnalytics.intensityForEffectiveSets(8d)
                > WorkoutSummaryAnalytics.intensityForEffectiveSets(4d));
    }

    @Test
    public void percentageChangeHandlesIncreaseDecreaseAndZeroBaseline() {
        assertEquals("+25%", WorkoutSummaryAnalytics.formatChangePercent(80d, 100d));
        assertEquals("-25%", WorkoutSummaryAnalytics.formatChangePercent(80d, 60d));
        assertEquals("신규", WorkoutSummaryAnalytics.formatChangePercent(0d, 60d));
        assertEquals("—", WorkoutSummaryAnalytics.formatChangePercent(0d, 0d));
    }
}
