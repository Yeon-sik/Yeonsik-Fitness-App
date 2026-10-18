package com.yeonsik.fitness.shared.feature.workout.model

/** An accepted UI suggestion for one newly created session; never a routine-definition edit. */
data class WorkoutSetCountProposal(
    val sourceRecordId: String,
    val sourceExerciseId: String,
    val sourceDate: String,
    val referenceDate: String,
    val routineId: String,
    val performanceKey: String,
    val sourceSignature: String,
    val normalSetCount: Int,
    val ruleVersion: String
)

/** A stale suggestion is rejected if its completed evidence changes before session creation. */
fun WorkoutProgressSession.proposalSignature(): String = listOf(
    recordId, exerciseOccurrenceId, date, routineId.orEmpty(), performanceKey.orEmpty(),
    sets.sortedBy { it.setIndex }.joinToString(";") {
        listOf(it.id, it.setIndex, it.type.id, it.dropParentSetId.orEmpty(), it.loadKg,
            it.repetitions, it.durationSeconds, it.rir, it.restSeconds).joinToString(":")
    }
).joinToString("|")
