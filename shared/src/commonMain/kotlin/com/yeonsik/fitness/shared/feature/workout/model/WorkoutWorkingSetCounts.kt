package com.yeonsik.fitness.shared.feature.workout.model

/** Observed working sets, not an estimate of physiological stimulus or effort. */
object WorkoutWorkingSetCounts {
    @kotlin.jvm.JvmStatic
    fun byBodyPart(sessions: List<WorkoutProgressSession>): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        sessions.groupBy { it.recordId to it.exerciseOccurrenceId }.values.forEach { rows ->
            val part = rows.first().bodyPart.trim()
            if (part.isNotEmpty()) {
                val count = rows.flatMap { it.sets }.filter { it.type != WorkoutSetType.WARMUP }
                    .map { it.dropParentSetId ?: it.id }.distinct().size
                result[part] = (result[part] ?: 0) + count
            }
        }
        return result
    }
}
