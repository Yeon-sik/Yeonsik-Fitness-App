package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val workoutCompletionDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val workoutStartDateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd:HH:mm", Locale.ROOT)

internal fun formatWorkoutCompletionDateTime(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? = workoutLocalDateTime(value, zoneId)?.format(workoutCompletionDateTimeFormatter)

internal fun formatWorkoutStartDateTime(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    workoutLocalDateTime(value, zoneId)?.format(workoutStartDateTimeFormatter)
        ?: runCatching { LocalDate.parse(value).toString() + "\n시각 미기록" }.getOrDefault("시각 미기록")

internal fun formatCompletedWorkoutDuration(seconds: Int): String =
    if (seconds > 0) String.format(Locale.ROOT, "(%02d:%02d)", seconds / 3600, seconds / 60 % 60)
    else "(시간 미기록)"

private fun workoutLocalDateTime(value: String?, zoneId: ZoneId): LocalDateTime? {
    val timestamp = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        OffsetDateTime.parse(timestamp).toInstant().atZone(zoneId).toLocalDateTime()
    }.getOrElse {
        runCatching { Instant.parse(timestamp).atZone(zoneId).toLocalDateTime() }.getOrElse {
            runCatching { LocalDateTime.parse(timestamp) }.getOrNull()
        }
    }
}

internal fun workoutCompletionStatusLabel(completedAt: String?): String =
    formatWorkoutCompletionDateTime(completedAt)
        ?.let { "완료된 운동 · $it" }
        ?: "완료된 운동 · 완료 시각 미기록"

internal data class WorkoutExerciseTrendPoint(
    val date: String,
    val estimatedOneRepMaxKg: Double?,
    val totalVolumeKg: Double
)

internal fun workoutExerciseTrendPoints(
    histories: List<WorkoutExerciseHistory>
): List<WorkoutExerciseTrendPoint> = histories.map { history ->
    WorkoutExerciseTrendPoint(
        date = history.date,
        estimatedOneRepMaxKg = history.estimatedOneRepMaxKg?.takeIf { it.isFinite() && it > 0.0 },
        totalVolumeKg = history.totalVolumeKg.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    )
}
