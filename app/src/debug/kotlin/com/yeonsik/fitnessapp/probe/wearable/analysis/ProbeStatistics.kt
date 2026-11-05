package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/**
 * Timing statistics for heart-rate sample times inside one PR0 session window.
 *
 * Definitions (also written into the PR0 result document):
 * - Active intervals are the session minus the recorded pauses.
 * - Intervals between samples are measured between consecutive distinct sample times inside the
 *   same active interval, so a pause never counts as a sampling gap.
 * - Coverage splits each active interval into 60-second bins from its start. A final partial bin
 *   counts only when it is at least 30 seconds long. A bin is covered when it holds a sample.
 * - The maximum active gap includes the stretch from an active interval's start to its first
 *   sample and from its last sample to its end.
 * - Percentiles use the nearest-rank method.
 */
data class SampleStatistics(
    val samplesInWindow: Int,
    val samplesInActive: Int,
    val duplicateActiveTimestamps: Int,
    val firstSampleOffset: Duration?,
    val lastSampleOffset: Duration?,
    val intervalMedian: Duration?,
    val intervalP95: Duration?,
    val intervalMax: Duration?,
    val activeDuration: Duration,
    val activeBinCount: Int,
    val coveredBinCount: Int,
    val maxActiveGap: Duration?,
) {
    val coverageRatio: Double?
        get() = if (activeBinCount == 0) null else coveredBinCount.toDouble() / activeBinCount
}

object ProbeStatistics {
    val BIN: Duration = Duration.ofSeconds(60)
    val MIN_PARTIAL_BIN: Duration = Duration.ofSeconds(30)

    fun compute(window: SessionWindow, sampleTimes: List<Instant>): SampleStatistics {
        val inWindow = sampleTimes.filter { !it.isBefore(window.start) && !it.isAfter(window.end) }.sorted()
        val active = window.activeIntervals()
        val perInterval = active.map { interval ->
            interval to inWindow.filter { !it.isBefore(interval.start) && !it.isAfter(interval.end) }
        }
        val activeSamples = perInterval.flatMap { it.second }
        val distinctActive = activeSamples.distinct()

        val intervalMillis = perInterval
            .flatMap { (_, samples) ->
                samples.distinct().zipWithNext { first, second -> Duration.between(first, second).toMillis() }
            }
            .sorted()

        var bins = 0
        var covered = 0
        for ((interval, samples) in perInterval) {
            var binStart = interval.start
            while (binStart.isBefore(interval.end)) {
                val proposedEnd = binStart.plus(BIN)
                val binEnd = if (proposedEnd.isAfter(interval.end)) interval.end else proposedEnd
                if (Duration.between(binStart, binEnd) >= MIN_PARTIAL_BIN) {
                    bins++
                    val closesInterval = binEnd == interval.end
                    val hasSample = samples.any { sample ->
                        !sample.isBefore(binStart) &&
                            (sample.isBefore(binEnd) || (closesInterval && sample == binEnd))
                    }
                    if (hasSample) covered++
                }
                binStart = binEnd
            }
        }

        val maxGap = perInterval.maxOfOrNull { (interval, samples) ->
            val points = listOf(interval.start) + samples.distinct() + listOf(interval.end)
            points.zipWithNext { first, second -> Duration.between(first, second) }.maxOrNull() ?: Duration.ZERO
        }

        return SampleStatistics(
            samplesInWindow = inWindow.size,
            samplesInActive = activeSamples.size,
            duplicateActiveTimestamps = activeSamples.size - distinctActive.size,
            firstSampleOffset = inWindow.firstOrNull()?.let { Duration.between(window.start, it) },
            lastSampleOffset = inWindow.lastOrNull()?.let { Duration.between(window.start, it) },
            intervalMedian = percentile(intervalMillis, 0.5)?.let(Duration::ofMillis),
            intervalP95 = percentile(intervalMillis, 0.95)?.let(Duration::ofMillis),
            intervalMax = intervalMillis.lastOrNull()?.let(Duration::ofMillis),
            activeDuration = window.activeDuration(),
            activeBinCount = bins,
            coveredBinCount = covered,
            maxActiveGap = maxGap,
        )
    }

    /** Nearest-rank percentile of an ascending list; `null` when the list is empty. */
    fun percentile(sortedValues: List<Long>, fraction: Double): Long? {
        require(fraction > 0.0 && fraction <= 1.0) { "Percentile fraction must be in (0, 1]." }
        if (sortedValues.isEmpty()) return null
        val rank = ceil(fraction * sortedValues.size).toInt().coerceIn(1, sortedValues.size)
        return sortedValues[rank - 1]
    }
}

enum class GateStatus { MET, NOT_MET, NO_DATA }

data class ProvisionalGateResult(
    val status: GateStatus,
    val coverageMet: Boolean?,
    val maxGapMet: Boolean?,
)

/**
 * The provisional K-12 coverage criteria from the wearable plan. This is an aid for the person
 * writing the PR0 result; it is not the PR0 verdict and does not replace user approval of K-12.
 */
object ProvisionalCoverageGate {
    const val MIN_COVERAGE: Double = 0.8
    val MAX_ACTIVE_GAP: Duration = Duration.ofMinutes(5)

    fun evaluate(statistics: SampleStatistics): ProvisionalGateResult {
        val coverage = statistics.coverageRatio
        if (statistics.samplesInActive == 0 || coverage == null) {
            return ProvisionalGateResult(GateStatus.NO_DATA, coverageMet = null, maxGapMet = null)
        }
        val coverageMet = coverage >= MIN_COVERAGE
        val gapMet = statistics.maxActiveGap?.let { it <= MAX_ACTIVE_GAP } ?: false
        val status = if (coverageMet && gapMet) GateStatus.MET else GateStatus.NOT_MET
        return ProvisionalGateResult(status, coverageMet, gapMet)
    }
}
