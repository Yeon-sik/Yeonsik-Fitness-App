package com.yeonsik.fitness.shared.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState

enum class WorkoutSetType(val id: String, val marker: String, val label: String) {
    NORMAL("normal", "", "일반 세트"),
    WARMUP("warmup", "W", "워밍업"),
    DROP("drop", "D", "드롭세트");

    companion object {
        fun fromId(id: String?): WorkoutSetType = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Exercise linking is independent of the type of each recorded set. */
enum class WorkoutExerciseGroupType(val id: String, val marker: String, val label: String) {
    SUPERSET("superset", "S", "슈퍼세트"),
    COMPOUND("compound", "C", "컴파운드세트");

    companion object {
        // Existing groups without a type retain their original superset behavior.
        fun fromId(id: String?): WorkoutExerciseGroupType = entries.firstOrNull { it.id == id } ?: SUPERSET
    }
}

/** The first input row owns the group; its existing values and stable id stay intact. */
data class WorkoutSetGroup(val root: WorkoutSet, val sets: List<WorkoutSet>) {
    val isDropGroup: Boolean get() = root.setType == WorkoutSetType.DROP || sets.size > 1
}

fun workoutSetGroups(sets: List<WorkoutSet>): List<WorkoutSetGroup> {
    val ordered = sets.sortedBy { it.setIndex }
    val roots = ordered.filter { it.dropParentSetId == null }
    val rootIds = roots.map { it.id }.toSet()
    val children = ordered.filter { it.dropParentSetId in rootIds }.groupBy { it.dropParentSetId }
    return ordered.filter { it.dropParentSetId == null || it.dropParentSetId !in rootIds }.map { root ->
        WorkoutSetGroup(root, listOf(root) + children[root.id].orEmpty())
    }
}

data class WorkoutRoutineTotals(
    val workingSetCount: Int,
    val dropStepCount: Int,
    val volumeKg: Double,
    val repetitions: Int,
    val durationSeconds: Int,
    val exerciseKeys: List<String>
)

/** A drop chain counts once; every completed step contributes its actual work once. */
fun workoutRoutineTotals(exercises: List<WorkoutSessionExercise>): WorkoutRoutineTotals {
    var workingSets = 0
    var drops = 0
    var volume = 0.0
    var reps = 0
    var seconds = 0
    exercises.forEach { exercise ->
        val completed = exercise.completedSets.filter { it.isCompleted && it.setType != WorkoutSetType.WARMUP }
        workingSets += completed.map { it.dropParentSetId ?: it.id }.distinct().size
        drops += completed.count { it.setType == WorkoutSetType.DROP && it.dropParentSetId != null }
        completed.forEach { set ->
            reps += set.actualReps.coerceAtLeast(0)
            seconds += set.durationSeconds.coerceAtLeast(0)
            // Bodyweight, assistance, bands and timed loads are not external kg × reps.
            if (exercise.recordType == "weight_reps" &&
                (set.loadState == LoadState.EXTERNAL_LOAD || set.loadState == null)) {
                val amount = set.comparisonVolumeKg ?: (set.weightKg * set.actualReps)
                if (amount.isFinite() && amount > 0) volume += amount
            }
        }
    }
    return WorkoutRoutineTotals(workingSets, drops, volume, reps, seconds,
        exercises.map { exercise ->
            val identity = exercise.familyIdentity
            "${identity?.familyId ?: exercise.exerciseId}:${identity?.canonicalVariantKey.orEmpty()}:" +
                "${if (exercise.exerciseId == "manual") exercise.name else ""}:${exercise.recordType}"
        }.sorted())
}

data class WorkoutSetAdvance(val exerciseId: String? = null, val startRest: Boolean = true)

/** Drop steps stay together; linked exercises advance to the next unfinished movement in the round. */
fun workoutSetAdvance(detail: WorkoutExerciseDetail, completedSetId: String): WorkoutSetAdvance {
    val ordered = detail.sets.sortedBy { it.setIndex }
    val index = ordered.indexOfFirst { it.id == completedSetId }
    val completed = ordered.getOrNull(index) ?: return WorkoutSetAdvance()
    val dropGroup = workoutSetGroups(ordered).firstOrNull { group -> group.sets.any { it.id == completedSetId } }
    if (dropGroup?.isDropGroup == true && dropGroup.sets.any { it.id != completedSetId && !it.isCompleted })
        return WorkoutSetAdvance(startRest = false)
    if (completed.setType == WorkoutSetType.WARMUP) return WorkoutSetAdvance()
    val groupId = detail.activeExercise.supersetGroupId ?: return WorkoutSetAdvance()
    val group = detail.exercises.filter { it.supersetGroupId == groupId }.sortedBy { it.orderIndex }
    val current = group.indexOfFirst { it.id == detail.activeExercise.id }
    if (current < 0 || group.size < 2) return WorkoutSetAdvance()
    val following = group.drop(current + 1).firstOrNull { exercise ->
        detail.sessionSets[exercise.id].orEmpty().any { !it.isCompleted && it.setType != WorkoutSetType.WARMUP }
    }
    if (following != null) return WorkoutSetAdvance(following.id, startRest = false)
    val nextRound = group.take(current + 1).firstOrNull { exercise ->
        detail.sessionSets[exercise.id].orEmpty().any { it.id != completedSetId && !it.isCompleted && it.setType != WorkoutSetType.WARMUP }
    }
    return WorkoutSetAdvance(nextRound?.id, startRest = true)
}
