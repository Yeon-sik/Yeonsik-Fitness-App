package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartRateBandsTest {
    private fun sample(time: Long, bpm: Double) = DailyHeartRateSample("test.watch", time, bpm)

    @Test fun groupsFiveInTimeOrderAndKeepsExtremesAndRemainder() {
        val samples = listOf(82, 140, 91, 75, 108, 119, 119).mapIndexed { index, bpm -> sample(1_000L * index, bpm.toDouble()) }
        assertEquals(listOf(HeartRateBand(75.0, 140.0, 5), HeartRateBand(119.0, 119.0, 2)),
            heartRateBands(samples.reversed(), null, null))
    }

    @Test fun marksMissingIntervalsIncludingTheEdgesWithoutSpanningThem() {
        val samples = listOf(sample(100_000, 80.0), sample(101_000, 82.0), sample(300_000, 140.0), sample(301_000, 143.0))
        assertEquals(listOf(null, HeartRateBand(80.0, 82.0, 2), null, HeartRateBand(140.0, 143.0, 2), null),
            heartRateBands(samples, 0, 500_000))
        val sparse = List(5) { sample(it * 90_000L, 110.0) }
        assertEquals(listOf(HeartRateBand(110.0, 110.0, 5)), heartRateBands(sparse, null, null))
    }

    @Test fun ignoresInvalidReadingsAndDisplaysASingleValidObservation() {
        val invalid = listOf(sample(0, Double.NaN), sample(1, Double.POSITIVE_INFINITY), sample(2, 0.0))
        assertTrue(heartRateBands(invalid, null, null).isEmpty())
        assertEquals(listOf(HeartRateBand(92.0, 92.0, 1)), heartRateBands(invalid + sample(10, 92.0), null, null))
    }
}
