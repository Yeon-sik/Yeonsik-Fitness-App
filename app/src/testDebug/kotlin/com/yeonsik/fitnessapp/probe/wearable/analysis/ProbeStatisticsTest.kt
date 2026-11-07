package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeStatisticsTest {
    private val start = Instant.parse("2026-11-05T00:00:00Z")

    private fun at(seconds: Long): Instant = start.plusSeconds(seconds)

    private fun window(lengthSeconds: Long, vararg pauses: Pair<Long, Long>) = SessionWindow(
        start = start,
        end = at(lengthSeconds),
        pauses = pauses.map { (from, to) -> TimeInterval(at(from), at(to)) },
    )

    @Test
    fun steadySamplingCoversEveryBin() {
        val stats = ProbeStatistics.compute(window(600), (0L..600L step 5).map(::at))
        assertEquals(10, stats.activeBinCount)
        assertEquals(10, stats.coveredBinCount)
        assertEquals(1.0, stats.coverageRatio!!, 0.0)
        assertEquals(Duration.ofSeconds(5), stats.intervalMedian)
        assertEquals(Duration.ofSeconds(5), stats.intervalP95)
        assertEquals(Duration.ofSeconds(5), stats.maxActiveGap)
        assertEquals(GateStatus.MET, ProvisionalCoverageGate.evaluate(stats).status)
    }

    @Test
    fun aLongGapFailsTheProvisionalGate() {
        val samples = (0L..300L step 10).map(::at) + (960L..1200L step 10).map(::at)
        val stats = ProbeStatistics.compute(window(1200), samples)
        assertEquals(Duration.ofSeconds(660), stats.maxActiveGap)
        assertEquals(Duration.ofSeconds(660), stats.intervalMax)
        assertEquals(20, stats.activeBinCount)
        assertEquals(10, stats.coveredBinCount)
        val gate = ProvisionalCoverageGate.evaluate(stats)
        assertEquals(GateStatus.NOT_MET, gate.status)
        assertEquals(false, gate.coverageMet)
        assertEquals(false, gate.maxGapMet)
    }

    @Test
    fun pausesAreNeitherGapsNorBins() {
        // 0-300 active, 300-600 paused with no samples, 600-900 active.
        val samples = (0L..300L step 10).map(::at) + (600L..900L step 10).map(::at)
        val stats = ProbeStatistics.compute(window(900, 300L to 600L), samples)
        assertEquals(Duration.ofMinutes(10), stats.activeDuration)
        assertEquals(10, stats.activeBinCount)
        assertEquals(10, stats.coveredBinCount)
        assertEquals(Duration.ofSeconds(10), stats.maxActiveGap)
        assertEquals(Duration.ofSeconds(10), stats.intervalMax)
    }

    @Test
    fun samplesDuringAPauseCountInTheWindowButNotAsActive() {
        val stats = ProbeStatistics.compute(window(120, 60L to 90L), listOf(at(10), at(70), at(100)))
        assertEquals(3, stats.samplesInWindow)
        assertEquals(2, stats.samplesInActive)
    }

    @Test
    fun aFinalPartialBinCountsOnlyFromThirtySeconds() {
        assertEquals(2, ProbeStatistics.compute(window(90), listOf(at(1))).activeBinCount)
        assertEquals(1, ProbeStatistics.compute(window(80), listOf(at(1))).activeBinCount)
    }

    @Test
    fun aSampleExactlyAtTheEndCoversTheLastBin() {
        val stats = ProbeStatistics.compute(window(120), listOf(at(10), at(120)))
        assertEquals(2, stats.coveredBinCount)
    }

    @Test
    fun duplicateTimestampsAreCountedAndIgnoredForIntervals() {
        val stats = ProbeStatistics.compute(window(60), listOf(at(0), at(0), at(30), at(60)))
        assertEquals(1, stats.duplicateActiveTimestamps)
        assertEquals(Duration.ofSeconds(30), stats.intervalMedian)
    }

    @Test
    fun samplesOutsideTheWindowAreIgnoredAndOffsetsAreRelative() {
        val stats = ProbeStatistics.compute(window(60), listOf(at(-10), at(15), at(45), at(70)))
        assertEquals(2, stats.samplesInWindow)
        assertEquals(Duration.ofSeconds(15), stats.firstSampleOffset)
        assertEquals(Duration.ofSeconds(45), stats.lastSampleOffset)
    }

    @Test
    fun noSamplesMeansNoDataNotFailure() {
        val stats = ProbeStatistics.compute(window(600), emptyList())
        assertEquals(0, stats.coveredBinCount)
        assertNull(stats.intervalMedian)
        assertEquals(Duration.ofSeconds(600), stats.maxActiveGap)
        assertEquals(GateStatus.NO_DATA, ProvisionalCoverageGate.evaluate(stats).status)
    }

    @Test
    fun percentileUsesNearestRank() {
        val values = (1L..20L).toList()
        assertEquals(10L, ProbeStatistics.percentile(values, 0.5))
        assertEquals(19L, ProbeStatistics.percentile(values, 0.95))
        assertEquals(20L, ProbeStatistics.percentile(values, 1.0))
        assertNull(ProbeStatistics.percentile(emptyList(), 0.5))
    }

    @Test
    fun boundaryValuesOfTheProvisionalGateAreInclusive() {
        // A gap of exactly five minutes passes the gap rule; 2 of 5 bins still fails coverage.
        val stats = ProbeStatistics.compute(window(300), listOf(at(0), at(300)))
        assertEquals(5, stats.activeBinCount)
        assertEquals(2, stats.coveredBinCount)
        assertEquals(Duration.ofMinutes(5), stats.maxActiveGap)
        val fiveMinuteGap = ProvisionalCoverageGate.evaluate(stats)
        assertEquals(true, fiveMinuteGap.maxGapMet)
        assertEquals(false, fiveMinuteGap.coverageMet)
        assertEquals(GateStatus.NOT_MET, fiveMinuteGap.status)

        // Exactly 4 of 5 bins (80%) passes the coverage rule.
        val eighty = ProbeStatistics.compute(window(300), listOf(at(0), at(60), at(120), at(180)))
        assertEquals(0.8, eighty.coverageRatio!!, 1e-9)
        assertEquals(Duration.ofSeconds(120), eighty.maxActiveGap)
        assertEquals(GateStatus.MET, ProvisionalCoverageGate.evaluate(eighty).status)
    }
}
