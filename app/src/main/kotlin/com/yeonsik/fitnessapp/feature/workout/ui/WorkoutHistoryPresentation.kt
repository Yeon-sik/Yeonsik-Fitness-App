package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val WORKOUT_HISTORY_SET_COLUMNS = 7
private val workoutCompletionDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

internal fun formatWorkoutCompletionDateTime(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? {
    val timestamp = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val localDateTime = runCatching {
        OffsetDateTime.parse(timestamp).toInstant().atZone(zoneId).toLocalDateTime()
    }.getOrElse {
        runCatching { Instant.parse(timestamp).atZone(zoneId).toLocalDateTime() }.getOrElse {
            runCatching { LocalDateTime.parse(timestamp) }.getOrNull()
        }
    } ?: return null
    return workoutCompletionDateTimeFormatter.format(localDateTime)
}

internal fun workoutCompletionStatusLabel(completedAt: String?): String =
    formatWorkoutCompletionDateTime(completedAt)
        ?.let { "완료된 운동 · $it" }
        ?: "완료된 운동 · 완료 시각 미기록"

internal data class WorkoutSetTableCell(
    val visibleValue: String,
    val spokenValue: String
)

internal data class WorkoutSetTableRows(
    val upperLabel: String,
    val upperValues: List<WorkoutSetTableCell>,
    val lowerLabel: String,
    val lowerValues: List<WorkoutSetTableCell>
)

internal data class WorkoutExerciseTrendPoint(
    val date: String,
    val estimatedOneRepMaxKg: Double?,
    val totalVolumeKg: Double
)

internal fun workoutHistorySetBatches(
    sets: List<WorkoutSet>,
    batchSize: Int = WORKOUT_HISTORY_SET_COLUMNS
): List<List<WorkoutSet>> {
    require(batchSize > 0)
    return sets.chunked(batchSize)
}

internal fun workoutSetTableRows(
    recordType: String,
    sets: List<WorkoutSet>,
    unit: MassUnit
): WorkoutSetTableRows {
    val normalizedType = FitnessRecordContract.normalizeRecordType(recordType)
    val isTimed = normalizedType == FitnessRecordContract.TIME ||
        normalizedType == FitnessRecordContract.WEIGHT_TIME
    val isTimeOnly = normalizedType == FitnessRecordContract.TIME
    val unitSymbol = MassUnit.orDefault(unit).symbol()
    val upperLabel = when {
        isTimeOnly -> "시간(초)"
        normalizedType == FitnessRecordContract.REPS_ONLY -> "부하"
        else -> "중량($unitSymbol)"
    }
    val lowerLabel = when {
        isTimeOnly -> "거리(m)"
        isTimed -> "시간(초)"
        else -> "횟수"
    }
    val upperValues = sets.map { set ->
        if (isTimeOnly) {
            val seconds = set.durationSeconds.takeIf { it > 0 }?.toString() ?: "—"
            WorkoutSetTableCell(seconds, "${set.setIndex}세트, 시간 ${seconds}초")
        } else {
            val load = setLoadLabel(set, normalizedType, unit)
            WorkoutSetTableCell(load, "${set.setIndex}세트, 부하 $load $unitSymbol")
        }
    }
    val lowerValues = sets.map { set ->
        when {
            isTimeOnly -> {
                val distance = set.distanceMeters.takeIf { it > 0.0 }?.let {
                    String.format(Locale.ROOT, "%.1f", it)
                } ?: "—"
                WorkoutSetTableCell(distance, "${set.setIndex}세트, 거리 ${distance}미터")
            }
            isTimed -> {
                val seconds = set.durationSeconds.takeIf { it > 0 }?.toString() ?: "—"
                WorkoutSetTableCell(seconds, "${set.setIndex}세트, 시간 ${seconds}초")
            }
            else -> {
                val reps = set.actualReps.takeIf { it > 0 }?.toString() ?: "—"
                WorkoutSetTableCell(reps, "${set.setIndex}세트, 횟수 ${reps}회")
            }
        }
    }
    return WorkoutSetTableRows(upperLabel, upperValues, lowerLabel, lowerValues)
}

private fun setLoadLabel(set: WorkoutSet, recordType: String, unit: MassUnit): String = when {
    recordType == FitnessRecordContract.REPS_ONLY -> "체중"
    set.loadState == LoadState.BODYWEIGHT -> "체중"
    set.loadState == LoadState.ADDED_WEIGHT && set.addedWeightKg > 0.0 ->
        "+${MassFormatter.format(set.addedWeightKg, unit)}"
    set.loadState == LoadState.ASSISTED || set.loadState == LoadState.BAND_ASSISTED ->
        set.assistedWeightKg.takeIf { it > 0.0 }?.let { "보조 ${MassFormatter.format(it, unit)}" } ?: "보조"
    set.loadState == LoadState.BAND_RESISTED ->
        set.weightKg.takeIf { it > 0.0 }?.let { MassFormatter.format(it, unit) }
            ?: set.addedWeightKg.takeIf { it > 0.0 }?.let { MassFormatter.format(it, unit) }
            ?: "밴드"
    set.weightKg > 0.0 -> MassFormatter.format(set.weightKg, unit)
    else -> "—"
}

internal fun workoutExerciseTrendPoints(
    histories: List<WorkoutExerciseHistory>
): List<WorkoutExerciseTrendPoint> = histories.map { history ->
    WorkoutExerciseTrendPoint(
        date = history.date,
        estimatedOneRepMaxKg = history.estimatedOneRepMaxKg?.takeIf { it.isFinite() && it > 0.0 },
        totalVolumeKg = history.totalVolumeKg.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    )
}
