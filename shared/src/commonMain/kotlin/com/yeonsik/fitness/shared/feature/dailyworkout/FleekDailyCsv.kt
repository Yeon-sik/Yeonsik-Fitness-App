package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class FleekDailyWorkout(val date: String, val workout: DailyWorkoutSource)

internal fun parseFleekDailyWorkouts(csv: String): List<FleekDailyWorkout> {
    val rows = readFleekCsvRows(csv)
    val header = rows.firstOrNull().orEmpty().map { it.trim().removePrefix("\uFEFF").lowercase() }
    require(header.containsAll(listOf("date", "exercise", "reps"))) { "FLEEK CSV의 date, exercise, reps 열을 확인해 주세요." }
    val zone = TimeZone.of("Asia/Seoul")
    val sessions = linkedMapOf<Instant, FleekSession>()
    for (row in rows.drop(1)) {
        fun cell(name: String): String = row.getOrNull(header.indexOf(name)).orEmpty().trim()
        val dateText = cell("date")
        val timestamp = runCatching { Instant.parse(dateText) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(dateText).toInstant(zone) }.getOrNull()
        val day = timestamp?.toLocalDateTime(zone)?.date
            ?: runCatching { LocalDate.parse(dateText) }.getOrNull() ?: continue
        val sourceId = timestamp ?: day.atStartOfDayIn(zone)
        val name = cell("exercise")
        if (name.isEmpty()) continue
        val set = DailyWorkoutSet(
            weightKg = cell("weight(kg)").measurement(),
            reps = cell("reps").toIntOrNull()?.takeIf { it > 0 },
            rpe = cell("rpe").measurement()?.takeIf { it <= 10 },
            durationSeconds = cell("duration(s)").toIntOrNull()?.takeIf { it > 0 },
            distanceMeters = cell("distance(m)").measurement(),
            setType = cell("set type").takeIf { it.isNotEmpty() }
        )
        if (set.reps == null && set.durationSeconds == null && set.distanceMeters == null) continue
        val session = sessions.getOrPut(sourceId) { FleekSession(day, timestamp) }
        val last = session.exercises.lastOrNull()
        if (last?.name == name) {
            session.exercises[session.exercises.lastIndex] = last.copy(sets = last.sets + set)
        } else {
            session.exercises.add(DailyWorkoutExercise(name, listOf(set)))
        }
    }
    require(sessions.isNotEmpty()) { "가져올 FLEEK 운동 기록이 없습니다." }
    return sessions.map { (id, session) ->
        val exercises = session.exercises
        val cardio = exercises.map { cardioKind(it.name) }.distinct().singleOrNull()
            ?.takeIf { exercises.all { exercise -> exercise.sets.all { it.reps == null } } }
        val sets = exercises.flatMap { it.sets }
        FleekDailyWorkout(session.day.toString(), DailyWorkoutSource(
            origin = WorkoutOrigin.FLEEK,
            writer = "FLEEK",
            id = id.toString(),
            title = exercises.first().name,
            kind = cardio ?: DailyWorkoutKind.STRENGTH,
            startMillis = session.start?.toEpochMilliseconds(),
            durationSeconds = sets.mapNotNull { it.durationSeconds }.sum().takeIf { cardio != null && it > 0 },
            distanceMeters = sets.mapNotNull { it.distanceMeters }.sum().takeIf { cardio != null && it > 0 },
            exercises = exercises
        ))
    }
}

private data class FleekSession(
    val day: LocalDate,
    val start: Instant?,
    val exercises: MutableList<DailyWorkoutExercise> = mutableListOf()
)

private fun String.measurement(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

private fun cardioKind(raw: String): DailyWorkoutKind? = when (raw.trim().lowercase()) {
    "러닝", "런닝", "달리기", "트레드밀", "running", "treadmill" -> DailyWorkoutKind.RUNNING
    "걷기", "walking" -> DailyWorkoutKind.WALKING
    "자전거", "사이클", "실내 자전거", "cycling" -> DailyWorkoutKind.CYCLING
    "수영", "swimming" -> DailyWorkoutKind.SWIMMING
    else -> null
}

/** CSV tokenization is shared with the existing Android workout importer. */
fun readFleekCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var fieldStarted = false
    var index = 0
    while (index < text.length) {
        val char = text[index++]
        if (quoted) {
            if (char == '"') {
                if (text.getOrNull(index) == '"') {
                    field.append('"')
                    index++
                } else quoted = false
            } else field.append(char)
        } else if (char == '"' && !fieldStarted) {
            quoted = true
            fieldStarted = true
        } else if (char == ',' || char == '\r' || char == '\n') {
            row.add(field.toString())
            field.clear()
            fieldStarted = false
            if (char != ',') {
                if (char == '\r' && text.getOrNull(index) == '\n') index++
                rows.add(row)
                row = mutableListOf()
            }
        } else {
            field.append(char)
            fieldStarted = true
        }
    }
    require(!quoted) { "닫히지 않은 CSV 따옴표가 있습니다." }
    if (field.isNotEmpty() || fieldStarted || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows
}
