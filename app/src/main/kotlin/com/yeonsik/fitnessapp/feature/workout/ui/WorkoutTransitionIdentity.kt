package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.routine.ui.stableRoutineExercises

internal data class WorkoutRoutineExerciseAnchor(val routineId: String, val index: Int)
internal data class WorkoutSessionExerciseAnchor(val recordId: String, val exerciseId: String)
internal data class WorkoutRoutineTitleAnchor(val routineId: String)
internal data class WorkoutSessionTitleAnchor(val recordId: String)

internal data class WorkoutRoutineBridge(
    val routineId: String,
    val recordId: String,
    val sessionAnchors: Map<String, WorkoutRoutineExerciseAnchor>
)

/** Match occurrences only for the session just created by this routine start action. */
internal fun workoutRoutineBridge(
    routineId: String?,
    exercises: List<RoutineExerciseInstance>,
    createdRecordId: String?,
    session: WorkoutSessionSnapshot?
): WorkoutRoutineBridge? {
    if (routineId == null || session == null || session.recordId != createdRecordId ||
        session.routineId != routineId || session.status != "in_progress") return null
    val source = stableRoutineExercises(exercises)
    val target = stableWorkoutSessionExercises(session.exercises)
    if (source.isEmpty() || source.size != target.size) return null
    if (source.zip(target).any { (from, to) ->
            val sourceIdentity = from.familyIdentity
            val targetIdentity = to.familyIdentity
            from.exerciseId != to.exerciseId || from.order != to.orderIndex ||
                (sourceIdentity != null && targetIdentity != null &&
                    sourceIdentity.visualVariantKey != targetIdentity.visualVariantKey)
        }) return null
    return WorkoutRoutineBridge(routineId, session.recordId,
        target.mapIndexed { index, exercise -> exercise.id to WorkoutRoutineExerciseAnchor(routineId, index) }.toMap())
}
