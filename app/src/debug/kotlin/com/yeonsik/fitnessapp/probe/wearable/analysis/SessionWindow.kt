package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant

/** A wall-clock interval. Both ends are absolute instants; [end] is never before [start]. */
data class TimeInterval(val start: Instant, val end: Instant) {
    init {
        require(!end.isBefore(start)) { "Interval end must not be before its start." }
    }

    val duration: Duration get() = Duration.between(start, end)

    fun overlaps(other: TimeInterval): Boolean = start < other.end && other.start < end
}

/**
 * The PR0 session window written down by the person running the probe: wall-clock start and end
 * plus every pause they recorded. The probe never reads Fitness records to build this window.
 */
data class SessionWindow(
    val start: Instant,
    val end: Instant,
    val pauses: List<TimeInterval> = emptyList(),
) {
    init {
        require(end.isAfter(start)) { "Session end must be after its start." }
        val ordered = pauses.sortedBy { it.start }
        for (pause in ordered) {
            require(pause.end.isAfter(pause.start)) { "A pause must have a positive length." }
            require(!pause.start.isBefore(start) && !pause.end.isAfter(end)) {
                "A pause must stay inside the session."
            }
        }
        ordered.zipWithNext().forEach { (first, second) ->
            require(!first.overlaps(second)) { "Pauses must not overlap." }
        }
    }

    val duration: Duration get() = Duration.between(start, end)

    /** The session minus its pauses, in time order. */
    fun activeIntervals(): List<TimeInterval> {
        val active = mutableListOf<TimeInterval>()
        var cursor = start
        for (pause in pauses.sortedBy { it.start }) {
            if (pause.start.isAfter(cursor)) {
                active += TimeInterval(cursor, pause.start)
            }
            if (pause.end.isAfter(cursor)) {
                cursor = pause.end
            }
        }
        if (end.isAfter(cursor)) {
            active += TimeInterval(cursor, end)
        }
        return active
    }

    fun activeDuration(): Duration =
        activeIntervals().fold(Duration.ZERO) { total, interval -> total.plus(interval.duration) }
}
