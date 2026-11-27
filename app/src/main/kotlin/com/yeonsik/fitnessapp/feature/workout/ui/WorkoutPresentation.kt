package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.core.ui.FitnessProgressPresentation
import com.yeonsik.fitnessapp.core.ui.fitnessProgressPresentation
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitnessapp.ui.WorkoutSummaryAnalytics
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs
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

internal fun workoutSummaryChangeLabel(
    previousVolumeKg: Double?,
    currentVolumeKg: Double
): String = previousVolumeKg?.let {
    WorkoutSummaryAnalytics.formatChangePercent(it, currentVolumeKg)
} ?: "비교할 이전 기록 없음"

internal fun workoutExerciseVolumeChangeLabel(
    previousVolumeKg: Double?,
    currentVolumeKg: Double,
    unit: MassUnit
): String {
    val previous = previousVolumeKg ?: return "이전 기록 없음"
    if (!previous.isFinite() || !currentVolumeKg.isFinite()) return "변화 계산 불가"

    val differenceKg = currentVolumeKg - previous
    if (differenceKg == 0.0) return "직전 대비 변화 없음"

    val difference = MassFormatter.withUnit(abs(differenceKg), unit)
    val signedDifference = if (differenceKg > 0.0) "+$difference" else "−$difference"
    val percentage = workoutSummaryChangeLabel(previous, currentVolumeKg)
    return "직전 대비 $signedDifference ($percentage)"
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
