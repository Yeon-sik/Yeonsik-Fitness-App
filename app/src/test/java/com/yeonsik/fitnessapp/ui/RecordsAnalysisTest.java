package com.yeonsik.fitnessapp.ui;

import org.junit.Test;

import java.util.Arrays;

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
    public void inProgressSessionDoesNotCountAsACompletedTrendPoint() {
        assertFalse(RecordsAnalysis.hasEnoughTrendPoints(
                2,
                RecordsAnalysis.TrendCurrentState.IN_PROGRESS
        ));
        assertTrue(RecordsAnalysis.hasEnoughTrendPoints(
                3,
                RecordsAnalysis.TrendCurrentState.IN_PROGRESS
        ));
        assertTrue(RecordsAnalysis.hasEnoughTrendPoints(
                2,
                RecordsAnalysis.TrendCurrentState.COMPLETED
        ));
    }

    @Test
    public void paddedWeightRangeRemainsReadableForSmallChanges() {
        RecordsAnalysis.TrendRange range = RecordsAnalysis.trendRange(
                Arrays.asList(88.5d, 89.0d, 89.7d),
                RecordsAnalysis.TrendScalePolicy.RANGE_PADDED
        );

        assertTrue(range.min < 88.5d);
        assertTrue(range.max > 89.7d);
        assertTrue(range.span() >= RecordsAnalysis.MIN_RANGE_PADDED_SPAN);
    }

    @Test
    public void paddedWeightRangeHandlesConstantValuesWithoutZeroDivision() {
        RecordsAnalysis.TrendRange range = RecordsAnalysis.trendRange(
                Arrays.asList(89d, 89d, 89d),
                RecordsAnalysis.TrendScalePolicy.RANGE_PADDED
        );

        assertTrue(Double.isFinite(range.min));
        assertTrue(Double.isFinite(range.max));
        assertTrue(range.span() > 0d);
    }

    @Test
    public void periodLabelUsesOnlyProvidedDates() {
        assertEquals(
                "2026-08-21 ~ 2026-09-04",
                RecordsAnalysis.trendPeriodLabel(
                        Arrays.asList("2026-08-21", "2026-08-28"),
                        "2026-09-04"
                )
        );
        assertEquals(
                "",
                RecordsAnalysis.trendPeriodLabel(Arrays.asList(), null)
        );
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
