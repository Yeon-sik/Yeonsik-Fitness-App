package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlin.math.abs

enum class WorkoutMatchBasis { OVERLAP, START_IN_INTERVAL, NEAR_START, DATE_ONLY }

data class DailyWorkoutCandidate(
    val first: DailyWorkoutSource,
    val second: DailyWorkoutSource,
    val basis: WorkoutMatchBasis,
    val overlapMillis: Long = 0,
    val startDifferenceMillis: Long? = null,
    internal val overlapRatio: Double = 0.0
)

private val matchOrder = compareBy<DailyWorkoutCandidate> { it.basis }
    .thenByDescending { it.overlapRatio }
    .thenBy { it.startDifferenceMillis ?: Long.MAX_VALUE }
    .thenByDescending { it.overlapMillis }
    .thenBy { it.first.key }.thenBy { it.second.key }

private fun DailyWorkoutSource.sameWriter(other: DailyWorkoutSource) = origin == other.origin && writer == other.writer

private fun DailyWorkoutSource.matchEnd(start: Long): Long? =
    endMillis?.takeIf { it > start } ?: durationSeconds?.takeIf { it > 0 }?.let { start + it * 1000L }

private fun timeMatch(first: DailyWorkoutSource, second: DailyWorkoutSource): DailyWorkoutCandidate? {
    if (!first.kind.matches(second.kind)) return null
    val a = first.startMillis
    val b = second.startMillis
    if (a == null || b == null) return DailyWorkoutCandidate(first, second, WorkoutMatchBasis.DATE_ONLY)
    val endA = first.matchEnd(a)
    val endB = second.matchEnd(b)
    val gap = abs(a - b)
    if (endA != null && endB != null) {
        val overlap = minOf(endA, endB) - maxOf(a, b)
        if (overlap <= 0) return null
        val union = maxOf(endA, endB) - minOf(a, b)
        return DailyWorkoutCandidate(first, second, WorkoutMatchBasis.OVERLAP, overlap, gap, overlap.toDouble() / union)
    }
    val basis = when {
        endA != null && b in a until endA || endB != null && a in b until endB -> WorkoutMatchBasis.START_IN_INTERVAL
        gap <= 10 * 60_000L -> WorkoutMatchBasis.NEAR_START
        else -> return null
    }
    return DailyWorkoutCandidate(first, second, basis, startDifferenceMillis = gap)
}

internal fun findDailyWorkoutCandidates(
    groups: List<List<DailyWorkoutSource>>,
    separate: List<DailyWorkoutDecision>
): List<DailyWorkoutCandidate> {
    val matches = mutableListOf<DailyWorkoutCandidate>()
    for ((index, left) in groups.withIndex()) {
        val leftKeys = left.map { it.key }
        for (right in groups.drop(index + 1)) {
            if (separate.any { it.matches(leftKeys, right.map { source -> source.key }) }) continue
            // A linked watch cannot make two distinct workouts from the same log into one session.
            if (left.any { a -> right.any { b -> a.sameWriter(b) &&
                    !(a.origin.isHealth && a.interval != null && a.interval == b.interval) } }) continue
            val best = left.flatMap { a -> right.mapNotNull { b -> timeMatch(a, b) } }.minWithOrNull(matchOrder)
            if (best != null) matches.add(best)
        }
    }
    // A date alone cannot choose between multiple sessions from the same workout log.
    return matches.filter { candidate ->
        candidate.basis != WorkoutMatchBasis.DATE_ONLY || matches.none { other ->
            other !== candidate && (other.alternativeFor(candidate.first)?.sameWriter(candidate.second) == true ||
                other.alternativeFor(candidate.second)?.sameWriter(candidate.first) == true)
        }
    }.sortedWith(matchOrder)
}

private fun DailyWorkoutCandidate.alternativeFor(source: DailyWorkoutSource): DailyWorkoutSource? = when (source.key) {
    first.key -> second
    second.key -> first
    else -> null
}

/** A screen card can show alternatives together without changing the confirmed sessions used by the export. */
data class DailyWorkoutEntry(val sessions: List<DailyWorkoutSession>, val candidates: List<DailyWorkoutCandidate>) {
    internal fun forSource(key: String): DailyWorkoutEntry? {
        val current = sessions.firstOrNull { session -> session.sources.any { it.key == key } } ?: return null
        val ownKeys = current.sources.map { it.key }.toSet()
        val watches = sessions.filter { session -> session !== current && session.sources.any { it.origin.isHealth } }
        val watchKeys = watches.flatMap { it.sources }.map { it.key }.toSet()
        val options = candidates.filter { (it.first.key in ownKeys && it.second.key in watchKeys) ||
            (it.second.key in ownKeys && it.first.key in watchKeys) }
        if (options.isEmpty() && current.sources.none { it.origin.isHealth }) return null
        val optionKeys = options.flatMap { listOf(it.first.key, it.second.key) }.toSet()
        return DailyWorkoutEntry(listOf(current) + watches.filter { it.sources.any { source -> source.key in optionKeys } }, options)
    }
}

internal fun dailyWorkoutEntries(
    sessions: List<DailyWorkoutSession>,
    candidates: List<DailyWorkoutCandidate>
): List<DailyWorkoutEntry> {
    val buckets = sessions.map { mutableListOf(it) }.toMutableList()
    val bySource = buckets.flatMap { bucket -> bucket.single().sources.map { it.key to bucket } }.toMap().toMutableMap()
    for (candidate in candidates) {
        val left = bySource.getValue(candidate.first.key)
        val right = bySource.getValue(candidate.second.key)
        if (left === right) continue
        left.addAll(right)
        right.forEach { session -> session.sources.forEach { bySource[it.key] = left } }
        buckets.remove(right)
    }
    return buckets.map { bucket ->
        val ordered = bucket.sortedWith(compareBy({ it.details == null }, { it.primary.origin.isHealth },
            { it.timing.startMillis ?: Long.MAX_VALUE }, { it.primary.key }))
        DailyWorkoutEntry(ordered, candidates.filter { bySource[it.first.key] === bucket })
    }.sortedBy { entry -> entry.sessions.mapNotNull { it.timing.startMillis }.minOrNull() ?: Long.MAX_VALUE }
}
