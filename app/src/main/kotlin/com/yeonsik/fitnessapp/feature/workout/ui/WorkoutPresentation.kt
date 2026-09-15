package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.core.ui.FitnessProgressPresentation
import com.yeonsik.fitnessapp.core.ui.fitnessProgressPresentation
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.max

/** Stable presentation order; the persisted exercise id is only a deterministic tie-breaker. */
internal fun stableWorkoutExercises(
    exercises: List<WorkoutExercise>
): List<WorkoutExercise> = exercises
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<WorkoutExercise>> { it.value.orderIndex }
            .thenBy { it.value.id }
            .thenBy { it.index }
    )
    .map { it.value }

internal fun stableWorkoutSessionExercises(
    exercises: List<WorkoutSessionExercise>
): List<WorkoutSessionExercise> = exercises
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<WorkoutSessionExercise>> { it.value.orderIndex }
            .thenBy { it.value.id }
            .thenBy { it.index }
    )
    .map { it.value }

internal fun currentWorkoutExercise(
    exercises: List<WorkoutSessionExercise>
): WorkoutSessionExercise? = stableWorkoutSessionExercises(exercises)
    .firstOrNull { it.totalSetCount <= 0 || it.completedSetCount < it.totalSetCount }
    ?: stableWorkoutSessionExercises(exercises).firstOrNull()

internal fun workoutSessionProgress(
    exercises: List<WorkoutSessionExercise>
): FitnessProgressPresentation {
    val totalSets = exercises.sumOf { it.totalSetCount.coerceAtLeast(0) }
    val completedSets = exercises.sumOf {
        it.completedSetCount.coerceIn(0, it.totalSetCount.coerceAtLeast(0))
    }
    return fitnessProgressPresentation(completedSets, totalSets, emptyLabel = "세트 없음")
}

internal fun workoutExerciseProgress(
    exercise: WorkoutSessionExercise
): FitnessProgressPresentation = fitnessProgressPresentation(
    exercise.completedSetCount,
    exercise.totalSetCount,
    emptyLabel = "세트 없음"
)

internal fun formatWorkoutElapsedSeconds(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainder = safeSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%02d:%02d".format(minutes, remainder)
    }
}

internal fun workoutElapsedSeconds(
    startedAt: String,
    persistedSeconds: Int,
    status: String,
    nowMillis: Long
): Int {
    val persisted = persistedSeconds.coerceAtLeast(0)
    if (status != "in_progress") return persisted
    val parsed = runCatching { OffsetDateTime.parse(startedAt).toInstant() }
        .getOrNull()
        ?: runCatching { Instant.parse(startedAt) }.getOrNull()
        ?: return persisted
    val live = Duration.between(parsed, Instant.ofEpochMilli(nowMillis))
        .seconds
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return max(persisted, live)
}

internal fun editableMassUnit(
    inputLoadUnit: MassUnit?,
    preferredUnit: MassUnit
): MassUnit = inputLoadUnit ?: preferredUnit

/** Keeps the user's original input unit when a stored set is edited. */
internal fun initialMassInputValue(
    exerciseRecordType: String,
    weightKg: Double,
    assistedWeightKg: Double,
    addedWeightKg: Double,
    inputLoadValue: Double?,
    inputLoadUnit: MassUnit?,
    preferredUnit: MassUnit
): String {
    val normalized = FitnessRecordContract.normalizeRecordType(exerciseRecordType)
    if (inputLoadValue?.isFinite() == true && inputLoadUnit != null && inputLoadValue >= 0.0) {
        return MassFormatter.formatValue(inputLoadValue, inputLoadUnit)
    }
    val canonicalKg = when (normalized) {
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> assistedWeightKg
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> addedWeightKg
        else -> weightKg
    }
    return canonicalKg
        .takeIf { it.isFinite() && it > 0.0 }
        ?.let { MassFormatter.formatInput(it, preferredUnit) }
        .orEmpty()
}
