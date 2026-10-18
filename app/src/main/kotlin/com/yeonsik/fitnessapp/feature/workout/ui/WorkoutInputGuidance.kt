package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutInputBasis
import com.yeonsik.fitnessapp.data.FitnessRecordContract

/** Labels follow the same side/implement contract as ExerciseVolumeCalculator. */
internal fun workoutInputGuidance(recordType: String, basis: WorkoutInputBasis, unit: MassUnit?): String {
    val guidance = workoutInputGuidanceText(recordType, basis, unit)
    val fields = listOfNotNull(guidance.load, guidance.performance).joinToString(" · ")
    return if (guidance.accounting.isBlank()) fields else "$fields\n${guidance.accounting}"
}

internal data class WorkoutInputGuidanceText(val load: String?, val performance: String, val accounting: String)

internal fun workoutInputGuidanceText(recordType: String, basis: WorkoutInputBasis, unit: MassUnit?): WorkoutInputGuidanceText {
    val type = FitnessRecordContract.normalizeRecordType(recordType)
    if (type == FitnessRecordContract.TIME) return WorkoutInputGuidanceText(null, "시간은 초 단위로 입력하세요.", "")
    val massLabel = unit?.symbol() ?: "각 칸에 표시된 단위"
    val known = basis.implementMultiplier != null
    val singleSide = basis.laterality.equals("unilateral", ignoreCase = true)
    val load = when {
        !known -> "외부 중량"
        (basis.implementMultiplier ?: 1) > 1 -> "기구 1개당 중량"
        singleSide -> "한쪽 중량"
        else -> "외부 중량"
    }
    val repetitions = (if (known && singleSide) "한쪽당 반복 횟수" else "반복 횟수") +
        " x ${if (known && singleSide) 2 else 1}"
    val loadLabel = when (type) {
        FitnessRecordContract.WEIGHT_REPS, FitnessRecordContract.WEIGHT_TIME -> "$load ($massLabel)"
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> "보조받는 중량 ($massLabel)"
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> "몸무게를 뺀 추가 중량 ($massLabel)"
        else -> null
    }
    val accounting = when {
        !known -> "입력 기준을 확인하지 못한 종목입니다. 기존 기록과 같은 기준을 유지하세요."
        type == FitnessRecordContract.ASSISTED_WEIGHT_REPS -> "보조 중량은 몸무게나 외부 중량에 합산하지 않습니다."
        type == FitnessRecordContract.WEIGHT_TIME -> "시간은 세트별 수행 시간을 초 단위로 입력하세요."
        singleSide && (basis.implementMultiplier ?: 1) > 1 -> "한쪽당 수행값을 입력하며, 볼륨 계산에 양쪽과 기구 개수를 반영합니다."
        singleSide -> "한쪽당 수행값을 입력하며, 볼륨 계산에 양쪽을 반영합니다."
        (basis.implementMultiplier ?: 1) > 1 -> "기구 개수는 볼륨 계산에 반영합니다."
        type == FitnessRecordContract.WEIGHT_REPS -> "입력한 중량 × 횟수로 볼륨을 계산합니다."
        else -> "몸무게를 중량 칸에 더하지 않고 실제 수행값을 기록하세요."
    }
    return WorkoutInputGuidanceText(loadLabel,
        if (type == FitnessRecordContract.WEIGHT_TIME) "시간 (초)" else repetitions, accounting)
}
