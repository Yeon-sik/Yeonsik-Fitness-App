package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import org.junit.Assert.*
import org.junit.Test

class WorkoutTransitionIdentityTest {
    private val first = RoutineExerciseInstance("routine-a", "bench", "벤치 프레스", "chest", "", "barbell", "weight_reps", 1, null)
    private val second = first.copy(id = "routine-b", order = 2)
    private val exercises = listOf(
        WorkoutSessionExercise("new-b", "bench", 2, "벤치 프레스", "chest", "barbell", "weight_reps", "중량·횟수", null, 0, 3),
        WorkoutSessionExercise("new-a", "bench", 1, "벤치 프레스", "chest", "barbell", "weight_reps", "중량·횟수", null, 0, 3)
    )
    private fun session() = WorkoutSessionSnapshot("record", "가슴", "in_progress", "", 0, 0.0, 0,
        exercises, emptyList(), routineId = "routine")

    @Test fun repeatedExercisesMatchTheirOccurrenceAfterNewSessionIdsAreAssigned() {
        val bridge = workoutRoutineBridge("routine", listOf(second, first), "record", session())!!
        assertEquals(WorkoutRoutineExerciseAnchor("routine", 0), bridge.sessionAnchors["new-a"])
        assertEquals(WorkoutRoutineExerciseAnchor("routine", 1), bridge.sessionAnchors["new-b"])
        assertEquals(2, bridge.sessionAnchors.values.toSet().size)
    }

    @Test fun resumingAnotherSessionOrRoutineNeverAnimatesFromTheSelectedRoutine() {
        assertNull(workoutRoutineBridge("routine", listOf(first, second), null, session()))
        assertNull(workoutRoutineBridge("routine", listOf(first, second), "older-record", session()))
        assertNull(workoutRoutineBridge("another-routine", listOf(first, second), "record", session()))
        assertNull(workoutRoutineBridge("routine", listOf(first, second), "record", session().copy(status = "completed")))
    }

    @Test fun changedOrPartiallyLoadedExerciseListsFallBackToTheNormalTransition() {
        assertNull(workoutRoutineBridge("routine", listOf(first), "record", session()))
        assertNull(workoutRoutineBridge("routine", listOf(first, second.copy(exerciseId = "fly")), "record", session()))
    }
}
