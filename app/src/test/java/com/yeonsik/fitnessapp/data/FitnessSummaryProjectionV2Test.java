package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FitnessSummaryProjectionV2Test {
    @Test
    public void containsOnlyTheSummaryProjectionContract() throws Exception {
        FitnessSummaryProjectionV2 projection = new FitnessSummaryProjectionV2(
                "session-1",
                "session-1",
                "2026-08-01",
                "completed",
                14,
                8,
                0,
                0,
                0,
                0,
                0,
                3600,
                null,
                "2026-08-01T00:00:00Z",
                false,
                null,
                null,
                "2026-08-01T01:00:00Z",
                null,
                "android-local"
        );

        assertEquals(2, FitnessSummaryProjectionV2.CONTRACT_VERSION);
        assertEquals("session-1", projection.sourceFitnessSessionId);
        assertEquals("completed", projection.completionStatus);
        assertEquals(14, projection.chestSets);
        assertEquals(8, projection.backSets);
        assertEquals(Integer.valueOf(3600), projection.totalDurationSeconds);
        assertEquals(null, projection.cardioDurationSeconds);
    }
}
