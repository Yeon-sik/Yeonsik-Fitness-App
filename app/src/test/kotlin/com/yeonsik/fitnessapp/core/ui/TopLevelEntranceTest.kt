package com.yeonsik.fitnessapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelEntranceTest {
    @Test
    fun pendingArrivalWaitsForActualActiveReadyContent() {
        assertFalse(shouldConsumeTopLevelEntrance(7L, 6L, isActive = false, contentReady = true))
        assertFalse(shouldConsumeTopLevelEntrance(7L, 6L, isActive = true, contentReady = false))
        assertTrue(shouldConsumeTopLevelEntrance(7L, 6L, isActive = true, contentReady = true))
        assertFalse(shouldConsumeTopLevelEntrance(null, 6L, isActive = true, contentReady = true))
    }

    @Test
    fun consumedOrOlderArrivalCannotReplayOnRefreshOrRestore() {
        val restored = requireNotNull(TopLevelEntranceState.Saver.restore(7L))
        assertEquals(7L, restored.lastConsumedToken)
        assertEquals(1f, restored.clock.value, 0f)
        assertFalse(shouldConsumeTopLevelEntrance(7L, restored.lastConsumedToken, true, true))
        assertFalse(shouldConsumeTopLevelEntrance(6L, restored.lastConsumedToken, true, true))
        assertTrue(shouldConsumeTopLevelEntrance(8L, restored.lastConsumedToken, true, true))
        assertEquals(1f, TopLevelEntranceMotion(restored, 7L, true).progress(4), 0f)
    }

    @Test
    fun previewAndCancelledArrivalStayFullyVisibleWithoutConsumption() {
        val pending = TopLevelEntranceState()
        assertEquals(1f, TopLevelEntranceMotion(pending, 1L, false).progress(0), 0f)
        assertEquals(1f, TopLevelEntranceMotion(pending, null, true).progress(4), 0f)
        assertEquals(0L, pending.lastConsumedToken)
        assertEquals(0f, TopLevelEntranceMotion(pending, 1L, true).progress(0), 0f)
    }

    @Test
    fun staggerCapsAtFourStepsAndEachBlockFinishesAfter320Milliseconds() {
        assertEquals(listOf(0, 50, 100, 150, 200, 200, 200), (0..6).map(::topLevelEntranceDelayMs))
        assertEquals(200, topLevelEntranceDelayMs(10_000))
        assertEquals(0f, topLevelEntranceProgress(200f / 520f, 4), 0f)
        assertEquals(1f, topLevelEntranceProgress(320f / 520f, 0), 0.0001f)
        assertEquals(1f, topLevelEntranceProgress(1f, 10_000), 0f)
        assertTrue(topLevelEntranceProgress(160f / 520f, 0) > 0.5f)
    }
}
