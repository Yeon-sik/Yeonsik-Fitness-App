package com.yeonsik.fitnessapp.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RecordsAnalysisTest {
    @Test
    public void requiresThreePointsBeforeRenderingATrendChart() {
        assertFalse(RecordsAnalysis.hasEnoughTrendPoints(0));
        assertFalse(RecordsAnalysis.hasEnoughTrendPoints(2));
        assertTrue(RecordsAnalysis.hasEnoughTrendPoints(3));
    }

    @Test
    public void formatsWeightDeltaWithoutTurningUnknownIntoZero() {
        assertEquals("+1.2kg", RecordsAnalysis.formatSignedDelta(88.3, 89.5, "kg"));
        assertEquals("-1kg", RecordsAnalysis.formatSignedDelta(90d, 89d, "kg"));
        assertEquals("0kg", RecordsAnalysis.formatSignedDelta(89d, 89d, "kg"));
        assertEquals("미계산",
                RecordsAnalysis.formatSignedDelta(Double.NaN, 89d, "kg"));
    }

    @Test
    public void calendarMarkerCountIsBounded() {
        assertEquals(0, RecordsAnalysis.markerCount(false, false, false));
        assertEquals(2, RecordsAnalysis.markerCount(true, false, true));
        assertEquals(3, RecordsAnalysis.markerCount(true, true, true));
    }
}
