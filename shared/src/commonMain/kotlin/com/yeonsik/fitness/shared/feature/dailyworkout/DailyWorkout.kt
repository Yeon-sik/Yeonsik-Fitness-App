package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Instant

data class WorkoutDay(val date: LocalDate, val zone: TimeZone) {
    val start: Instant get() = date.atStartOfDayIn(zone)
    val end: Instant get() = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
}

@Serializable
enum class DailyWorkoutKind(val label: String) {
    STRENGTH("근력"), RUNNING("러닝"), WALKING("걷기"), CYCLING("자전거"),
    SWIMMING("수영"), CARDIO("유산소"), OTHER("기타 운동");

    internal fun matches(other: DailyWorkoutKind): Boolean = this == other || this == OTHER || other == OTHER ||
        (this == CARDIO && other.isSpecificCardio()) || (other == CARDIO && isSpecificCardio())

    private fun isSpecificCardio() = this in setOf(RUNNING, WALKING, CYCLING, SWIMMING)
}

@Serializable
enum class WorkoutOrigin(val label: String) {
    FITNESS("Fitness App"), FLEEK("FLEEK"), HEALTH_CONNECT("Health Connect"), HEALTH_KIT("Apple 건강");

    internal val isHealth get() = this == HEALTH_CONNECT || this == HEALTH_KIT
}

@Serializable
data class DailyWorkoutSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rir: Int? = null,
    val rpe: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val loadState: String? = null,
    val setType: String? = null
)

@Serializable
data class DailyWorkoutExercise(val name: String, val sets: List<DailyWorkoutSet>)

@Serializable
data class DailyWorkoutSource(
    val origin: WorkoutOrigin,
    val writer: String,
    val id: String,
    val title: String,
    val kind: DailyWorkoutKind,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val energyKcal: Double? = null,
    val manualHeartRateBpm: Double? = null,
    val exercises: List<DailyWorkoutExercise> = emptyList()
) {
    val key: String get() = "${origin.name}:${writer.length}:$writer$id"

    internal val interval: LongRange?
        get() = if (startMillis != null && endMillis != null && endMillis > startMillis) {
            startMillis until endMillis
        } else null
}

@Serializable
data class DailyHeartRateSample(val writer: String, val timeMillis: Long, val bpm: Double)

@Serializable
data class DailyWearableRecords(
    val workouts: List<DailyWorkoutSource> = emptyList(),
    val heartRateSamples: List<DailyHeartRateSample> = emptyList()
)

@Serializable
data class DailyWorkoutDecision(val first: String, val second: String, val sameWorkout: Boolean) {
    internal fun matches(firstKeys: Collection<String>, secondKeys: Collection<String>) =
        (first in firstKeys && second in secondKeys) || (second in firstKeys && first in secondKeys)
}

data class DailyWorkoutCandidate(val first: DailyWorkoutSource, val second: DailyWorkoutSource)

data class DailyHeartRateSummary(val writer: String, val averageBpm: Double, val maxBpm: Double, val sampleCount: Int)

data class DailyWorkoutSession(
    val sources: List<DailyWorkoutSource>,
    val timing: DailyWorkoutSource,
    val heartRate: DailyHeartRateSummary?
) {
    val primary: DailyWorkoutSource get() = sources.first()
    val details: DailyWorkoutSource? get() = sources.firstOrNull { it.exercises.isNotEmpty() }
}

data class DailyWorkoutReport(
    val day: WorkoutDay,
    val sessions: List<DailyWorkoutSession>,
    val candidates: List<DailyWorkoutCandidate>,
    val pendingCount: Int,
    val unlinkedHeartRate: List<DailyHeartRateSummary>,
    val decisions: List<DailyWorkoutDecision>,
    val sources: Map<String, DailyWorkoutSource>
)

fun projectDailyWorkouts(
    day: WorkoutDay,
    workouts: List<DailyWorkoutSource>,
    samples: List<DailyHeartRateSample>,
    decisions: List<DailyWorkoutDecision>
): DailyWorkoutReport {
    val sources = workouts.associateBy { it.key }
    val groups = sources.values.map { mutableListOf(it) }.toMutableList()
    val relevantDecisions = decisions.filter { it.first in sources && it.second in sources }
    val separate = relevantDecisions.filterNot { it.sameWorkout }
    for (link in relevantDecisions.filter { it.sameWorkout }) {
        val first = groups.first { it.any { source -> source.key == link.first } }
        val second = groups.first { it.any { source -> source.key == link.second } }
        if (first === second) continue
        val firstKeys = first.map { it.key }
        val secondKeys = second.map { it.key }
        require(separate.none { it.matches(firstKeys, secondKeys) }) { "먼저 상충하는 연결 결정을 취소해 주세요." }
        first.addAll(second)
        groups.remove(second)
    }

    val order = compareBy<DailyWorkoutSource>({ it.exercises.isEmpty() }, { it.origin }, { it.key })
    val orderedGroups = groups.map { it.sortedWith(order) }.sortedWith { a, b -> order.compare(a.first(), b.first()) }
    val pending = mutableSetOf<String>()
    val candidates = mutableListOf<DailyWorkoutCandidate>()
    for ((index, left) in orderedGroups.withIndex()) {
        val leftKeys = left.map { it.key }
        for (right in orderedGroups.drop(index + 1)) {
            if (separate.any { it.matches(leftKeys, right.map { source -> source.key }) }) continue
            val pair = candidateBetween(left, right) ?: continue
            candidates.add(pair)
            pending.add(right.first().key)
        }
    }

    val timing = orderedGroups.associate { group ->
        group.first().key to (group.firstOrNull { it.origin.isHealth && it.interval != null &&
            (it.writer.startsWith("com.apple.") || it.writer == "com.sec.android.app.shealth") }
            ?: group.firstOrNull { it.origin.isHealth && it.interval != null }
            ?: group.firstOrNull { it.interval != null }
            ?: group.firstOrNull { it.startMillis != null }
            ?: group.first())
    }
    val samplesBySession = mutableMapOf<String, MutableList<DailyHeartRateSample>>()
    val unlinked = mutableListOf<DailyHeartRateSample>()
    val dayEnd = day.end.toEpochMilliseconds()
    for (sample in samples.distinct().filter { it.bpm.isFinite() && it.bpm > 0 }) {
        val matching = orderedGroups.filter { group ->
            group.any { source -> source.interval?.contains(sample.timeMillis) == true }
        }
        val matchingWriter = matching.filter { group -> group.any { source ->
            source.origin.isHealth && source.writer == sample.writer && source.interval?.contains(sample.timeMillis) == true
        } }
        val group = matchingWriter.singleOrNull() ?: matching.singleOrNull()
        if (group != null && group.first().key !in pending) {
            samplesBySession.getOrPut(group.first().key) { mutableListOf() }.add(sample)
        } else if (sample.timeMillis < dayEnd) {
            unlinked.add(sample)
        }
    }
    val sessions = orderedGroups.filterNot { it.first().key in pending }.map { group ->
        val key = group.first().key
        DailyWorkoutSession(group, timing.getValue(key), summarize(samplesBySession[key].orEmpty()).firstOrNull())
    }.sortedWith(compareBy({ it.timing.startMillis ?: Long.MAX_VALUE }, { it.primary.key }))
    return DailyWorkoutReport(day, sessions, candidates, pending.size, summarize(unlinked), relevantDecisions, sources)
}

private fun candidateBetween(left: List<DailyWorkoutSource>, right: List<DailyWorkoutSource>): DailyWorkoutCandidate? {
    for (first in left) {
        for (second in right) {
            if (first.kind.matches(second.kind) && canBeSameWorkout(first, second)) {
                return DailyWorkoutCandidate(first, second)
            }
        }
    }
    return null
}

private fun canBeSameWorkout(first: DailyWorkoutSource, second: DailyWorkoutSource): Boolean {
    val firstInterval = first.interval
    val secondInterval = second.interval
    if (first.origin == second.origin && first.writer == second.writer) {
        return first.origin.isHealth && firstInterval != null && firstInterval == secondInterval
    }
    if (firstInterval != null && secondInterval != null) {
        return firstInterval.first <= secondInterval.last && secondInterval.first <= firstInterval.last
    }
    val firstStart = first.startMillis ?: return true
    val secondStart = second.startMillis ?: return true
    return firstInterval?.contains(secondStart) == true || secondInterval?.contains(firstStart) == true ||
        abs(firstStart - secondStart) <= 10 * 60 * 1000
}

private fun summarize(samples: List<DailyHeartRateSample>): List<DailyHeartRateSummary> = samples.groupBy { it.writer }
    .map { (writer, readings) ->
        DailyHeartRateSummary(writer, readings.map { it.bpm }.average(), readings.maxOf { it.bpm }, readings.size)
    }.sortedWith(compareByDescending<DailyHeartRateSummary> { it.sampleCount }.thenBy { it.writer })
