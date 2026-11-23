package com.yeonsik.fitnessapp.integration.wearable
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
class MeasuredPulsesTest {
    private val start = Instant.parse("2000-01-02T00:00:00Z")
    private val end = start.plusSeconds(60)
    @Test fun retainsTimesAndGapsAndDeduplicatesOnlyIdenticalSamples() {
        val a = MeasuredPulse(start, 80); val b = MeasuredPulse(start.plusSeconds(40), 100)
        assertEquals(listOf(a,b), measuredPulses(start,end,listOf(b,a,a)))
    }
    @Test fun excludesOutsideIntervalWithoutInventingValues() {
        assertTrue(measuredPulses(start,end,listOf(MeasuredPulse(start.minusNanos(1), 80), MeasuredPulse(end.plusNanos(1), 80))).isEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsConflictingSameTimestamp() {
        measuredPulses(start,end,listOf(MeasuredPulse(start,80),MeasuredPulse(start,81)))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidInterval() { measuredPulses(end,start,emptyList()) }
    @Test fun allowsMissingHeartRateWithoutGeneratingSummarySamples() { assertTrue(measuredPulses(start,end,emptyList()).isEmpty()) }
}
