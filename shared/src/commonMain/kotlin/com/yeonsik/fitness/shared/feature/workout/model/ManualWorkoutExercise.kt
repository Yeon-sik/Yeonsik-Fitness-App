package com.yeonsik.fitness.shared.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType

/** A provisional workout snapshot, never a new catalog identity. */
data class ManualWorkoutExercise(
    val name: String,
    val bodyPart: BodyPart,
    val equipment: EquipmentType,
    val recordType: String
) {
    init {
        require(name.isNotBlank()) { "운동명을 입력해 주세요." }
        require(recordType in RECORD_TYPES) { "지원하지 않는 기록 방식입니다." }
    }

    fun toReplacement() = WorkoutExerciseReplacement(
        "manual", name.trim(), null, bodyPart, equipment, null, null, recordType, null
    )

    companion object {
        val RECORD_TYPES = setOf("weight_reps", "reps_only", "time", "weight_time",
            "assisted_weight_reps", "bodyweight_added_weight_reps")
    }
}
