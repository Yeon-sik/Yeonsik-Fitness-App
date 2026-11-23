package com.yeonsik.fitnessapp.integration.wearable

import java.time.Instant

data class MeasuredPulse(val time: Instant, val bpm: Long)
data class WearableWorkout(
    val origin: String, val providerId: String, val title: String,
    val start: Instant, val end: Instant, val exerciseType: Int,
    val samples: List<MeasuredPulse>, val linkedRecordId: String? = null,
    val importedAt: Instant? = null
) {
    override fun toString() = "WearableWorkout(sampleCount=${samples.size})"
}

fun measuredPulses(start: Instant, end: Instant, samples: List<MeasuredPulse>): List<MeasuredPulse> {
    require(start < end)
    val groups = samples.filter { it.time >= start && it.time <= end && it.bpm in 1..300 }.groupBy { it.time }
    require(groups.values.none { it.map { pulse -> pulse.bpm }.distinct().size > 1 })
    return groups.values.map { it.first() }.sortedBy { it.time }
}
