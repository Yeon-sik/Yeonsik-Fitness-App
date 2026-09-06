package com.yeonsik.fitnessapp.feature.cardio.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CardioSessionSnapshotTest {
    @Test
    public void trackingElapsedTimeIncludesOnlyCurrentResumedSegment() {
        CardioSessionSnapshot snapshot = new CardioSessionSnapshot(
                "record-a", "running", "달리기", CardioSessionSnapshot.STATUS_TRACKING,
                1_000L, 9_000L, 4_000L, 120d, "ready"
        );

        assertEquals(7, snapshot.elapsedSeconds(12_000L));
    }

    @Test
    public void pausedElapsedTimeDoesNotAdvance() {
        CardioSessionSnapshot snapshot = new CardioSessionSnapshot(
                "record-a", "walking", "걷기", CardioSessionSnapshot.STATUS_PAUSED,
                1_000L, null, 4_000L, 120d, "stopped"
        );

        assertEquals(4, snapshot.elapsedSeconds(12_000L));
    }
}
