package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter

/** Match the actual set number, never the position in a possibly sparse history. */
internal fun previousWorkoutSet(history: WorkoutExerciseHistory?, setIndex: Int): WorkoutSet? =
    history?.sets?.filter { it.isCompleted && it.setIndex == setIndex }?.singleOrNull()

internal fun previousWorkoutSet(history: WorkoutExerciseHistory?, current: WorkoutSet): WorkoutSet? =
    previousWorkoutSet(history, current.setIndex)?.takeIf {
        it.setType == current.setType && (it.dropParentSetId == null) == (current.dropParentSetId == null)
    }

internal fun precedingCompletedWorkoutSet(sets: List<WorkoutSet>, setIndex: Int): WorkoutSet? =
    sets.filter { it.isCompleted && it.setIndex < setIndex }.maxByOrNull { it.setIndex }

/** A suggestion contains editable values only. Completion, rest and identity are never copied. */
internal data class WorkoutSetDraftValues(
    val load: String,
    val reps: String,
    val duration: String,
    val rir: String,
    val loadState: LoadState?
)

internal fun workoutSetDraftValues(
    source: WorkoutSet,
    rawRecordType: String,
    inputUnit: MassUnit,
    allowedLoadStates: List<LoadState>,
    currentLoadState: LoadState?
): WorkoutSetDraftValues? {
    if (!source.isCompleted) return null
    val type = FitnessRecordContract.normalizeRecordType(rawRecordType)
    val state = source.loadState
    // Do not reinterpret assistance as added weight, or silently change an unavailable load mode.
    if (state != null && state != currentLoadState && state !in allowedLoadStates) return null
    val effectiveState = state ?: when (type) {
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> LoadState.ASSISTED
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS ->
            if (source.addedWeightKg > 0.0 || source.inputLoadValue != null) LoadState.ADDED_WEIGHT
            else LoadState.BODYWEIGHT
        FitnessRecordContract.WEIGHT_REPS, FitnessRecordContract.WEIGHT_TIME -> LoadState.EXTERNAL_LOAD
        else -> null
    }
    if (state == null && currentLoadState != null && currentLoadState != effectiveState &&
        effectiveState !in allowedLoadStates) return null
    val supportsLoad = type != FitnessRecordContract.REPS_ONLY && type != FitnessRecordContract.TIME &&
        effectiveState != LoadState.BODYWEIGHT
    val canonicalLoad = when (effectiveState) {
        LoadState.ASSISTED, LoadState.BAND_ASSISTED -> source.assistedWeightKg
        LoadState.ADDED_WEIGHT -> source.addedWeightKg
        else -> source.weightKg
    }
    val sourceValue = source.inputLoadValue?.takeIf { it.isFinite() && it >= 0.0 }
    val sourceUnit = source.inputLoadUnit
    val load = when {
        !supportsLoad -> ""
        sourceValue != null && sourceUnit != null ->
            MassFormatter.formatInput(MassUnit.toKg(sourceValue, sourceUnit), inputUnit)
        canonicalLoad.isFinite() && canonicalLoad > 0.0 -> MassFormatter.formatInput(canonicalLoad, inputUnit)
        else -> ""
    }
    val timed = type == FitnessRecordContract.TIME || type == FitnessRecordContract.WEIGHT_TIME
    return WorkoutSetDraftValues(
        load = load,
        reps = if (!timed) source.actualReps.takeIf { it >= 0 }?.toString().orEmpty() else "",
        duration = if (timed) source.durationSeconds.takeIf { it >= 0 }?.toString().orEmpty() else "",
        rir = if (FitnessRecordContract.supportsRir(type)) source.rir?.toString().orEmpty() else "",
        loadState = state ?: effectiveState?.takeIf { it == currentLoadState || it in allowedLoadStates }
    )
}

/** Seed only untouched empty rows; saved entries and restored editor drafts always win. */
internal fun initialWorkoutSetSuggestion(
    current: WorkoutSet,
    previous: WorkoutSet?,
    recordType: String,
    unit: MassUnit,
    allowedLoadStates: List<LoadState>
): WorkoutSetDraftValues? {
    if (current.isCompleted || current.inputLoadValue != null || current.weightKg != 0.0 ||
        current.assistedWeightKg != 0.0 || current.addedWeightKg != 0.0 || current.actualReps != 0 ||
        current.durationSeconds != 0 || current.distanceMeters != 0.0 || current.rir != null) return null
    return previous?.let { workoutSetDraftValues(it, recordType, unit, allowedLoadStates, current.loadState) }
}

internal fun canRemoveWorkoutSet(sets: List<WorkoutSet>, set: WorkoutSet): Boolean =
    sets.size > 1 && set.setIndex > 1 && sets.minByOrNull { it.setIndex }?.id != set.id

internal fun workoutSetReferenceLabel(source: WorkoutSet, rawRecordType: String, unit: MassUnit,
                                     compact: Boolean = false): String {
    val values = workoutSetDraftValues(source, rawRecordType, unit, LoadState.entries, source.loadState)
        ?: return "비교할 완료 값 없음"
    val type = FitnessRecordContract.normalizeRecordType(rawRecordType)
    val loadLabel = when (values.loadState) {
        LoadState.BODYWEIGHT -> "체중"
        LoadState.ASSISTED -> "보조 "
        LoadState.BAND_ASSISTED -> "밴드 보조 "
        LoadState.ADDED_WEIGHT -> "추가 "
        LoadState.BAND_RESISTED -> "밴드 저항 "
        else -> ""
    }
    val load = if (type == FitnessRecordContract.REPS_ONLY || type == FitnessRecordContract.TIME) null
        else if (values.loadState == LoadState.BODYWEIGHT) loadLabel
        else loadLabel + if (values.load.isBlank()) "중량 미기록"
            else values.load + (if (compact) "" else " ") + unit.symbol()
    val performance = if (type == FitnessRecordContract.TIME || type == FitnessRecordContract.WEIGHT_TIME)
        "${values.duration.ifBlank { "—" }}초" else "${values.reps.ifBlank { "—" }}회"
    return listOfNotNull(load, performance,
        values.rir.takeIf { !compact && it.isNotBlank() }?.let { "RIR $it" })
        .joinToString(if (compact) " - " else " · ")
}
