package com.yeonsik.fitness.shared.feature.cardio.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CardioSessionSnapshotTest {
    @Test
    fun trackingElapsedTimeIncludesOnlyCurrentResumedSegment() {
        val snapshot = CardioSessionSnapshot(
            "record-a", "running", "달리기", CardioSessionSnapshot.STATUS_TRACKING,
            1_000L, 9_000L, 4_000L, 120.0, 4, "ready", null
        )

        assertEquals(7, snapshot.elapsedSeconds(12_000L))
    }

    @Test
    fun pausedElapsedTimeDoesNotAdvance() {
        val snapshot = CardioSessionSnapshot(
            "record-a", "walking", "걷기", CardioSessionSnapshot.STATUS_PAUSED,
            1_000L, null, 4_000L, 120.0, 4, "stopped", null
        )

        assertEquals(4, snapshot.elapsedSeconds(12_000L))
    }
}
