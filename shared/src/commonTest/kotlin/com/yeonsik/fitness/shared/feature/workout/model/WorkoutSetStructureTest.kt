package com.yeonsik.fitness.shared.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutSetStructureTest {
    private fun set(id: String, index: Int, completed: Boolean = true) = WorkoutSet(id, index,
        20.0, 10, null, null, completed, 0, 0.0, 0.0, 0.0, LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)
    private fun exercise(id: String, order: Int = 0) = WorkoutExercise(id, id, order, id,
        recordType = "weight_reps", supersetGroupId = "group")
    private fun sessionExercise(sets: List<WorkoutSet>, recordType: String = "weight_reps") = WorkoutSessionExercise(
        "exercise", "bench", 0, "벤치프레스", "chest", "barbell", recordType, recordType, null,
        sets.count { it.isCompleted }, sets.size, completedSets = sets)

    @Test fun aDropRootIsAnIndependentGroupAndAllItsInputsStayTogether() {
        val first = set("first", 1)
        val root = set("root", 2).copy(setType = WorkoutSetType.DROP)
        val child = set("child", 3).copy(setType = WorkoutSetType.DROP, dropParentSetId = root.id)
        val last = set("last", 4)
        val groups = workoutSetGroups(listOf(first, root, child, last))
        assertEquals(listOf("first", "root", "last"), groups.map { it.root.id })
        assertEquals(listOf("root", "child"), groups[1].sets.map { it.id })
        assertEquals(true, groups[1].isDropGroup)
        assertEquals(true, workoutSetGroups(listOf(root)).single().isDropGroup)
        val totals = workoutRoutineTotals(listOf(sessionExercise(listOf(first, root, child, last))))
        assertEquals(3, totals.workingSetCount)
        assertEquals(1, totals.dropStepCount)
        assertEquals(800.0, totals.volumeKg)
    }

    @Test fun separateDropGroupsHaveARestBetweenThem() {
        val first = set("first", 1, false).copy(setType = WorkoutSetType.DROP)
        val second = set("second", 2, false).copy(setType = WorkoutSetType.DROP)
        val exercise = exercise("a").copy(supersetGroupId = null)
        val detail = WorkoutExerciseDetail("record", exercise, listOf(exercise), listOf(first, second))
        assertEquals(WorkoutSetAdvance(), workoutSetAdvance(detail, first.id))
    }

    @Test fun onlyCompletedWorkingSetsCountAndEveryDropStepContributesOnce() {
        val totals = workoutRoutineTotals(listOf(sessionExercise(listOf(
            set("warmup", 1).copy(setType = WorkoutSetType.WARMUP),
            set("normal", 2),
            set("drop", 3).copy(weightKg = 10.0, setType = WorkoutSetType.DROP, dropParentSetId = "normal"),
            set("pending", 4, completed = false)
        ))))
        assertEquals(1, totals.workingSetCount)
        assertEquals(1, totals.dropStepCount)
        assertEquals(300.0, totals.volumeKg)
        assertEquals(20, totals.repetitions)
    }

    @Test fun assistedBodyweightAndTimedWorkAreNotExternalLoadVolume() {
        val totals = workoutRoutineTotals(listOf(
            sessionExercise(listOf(set("assisted", 1).copy(loadState = LoadState.ASSISTED, assistedWeightKg = 30.0))),
            sessionExercise(listOf(set("bodyweight", 2).copy(loadState = LoadState.BODYWEIGHT)), "reps_only"),
            sessionExercise(listOf(set("timed", 3).copy(durationSeconds = 60)), "weight_time")
        ))
        assertEquals(0.0, totals.volumeKg)
        assertEquals(60, totals.durationSeconds)
    }

    @Test fun supersetAndCompoundAdvanceWithoutRestAndRestAtTheEndOfARound() {
        WorkoutExerciseGroupType.entries.forEach { type ->
            val a = exercise("a").copy(groupType = type)
            val b = exercise("b", 1).copy(groupType = type)
            val a1 = set("a1", 1, false)
            val a2 = set("a2", 2, false)
            val b1 = set("b1", 1, false)
            val b2 = set("b2", 2, false)
            val detail = WorkoutExerciseDetail("record", a, listOf(a, b), listOf(a1, a2),
                sessionSets = mapOf("a" to listOf(a1, a2), "b" to listOf(b1, b2)))
            assertEquals(WorkoutSetAdvance("b", false), workoutSetAdvance(detail, "a1"))
            val onB = detail.copy(activeExercise = b, sets = listOf(b1, b2),
                sessionSets = mapOf("a" to listOf(a1.copy(isCompleted = true), a2), "b" to listOf(b1, b2)))
            assertEquals(WorkoutSetAdvance("a", true), workoutSetAdvance(onB, "b1"))
        }
    }

    @Test fun aCompoundRoundCountsEachExercisesWorkingSet() {
        val first = sessionExercise(listOf(set("a1", 1))).copy(
            id = "a", supersetGroupId = "compound", groupType = WorkoutExerciseGroupType.COMPOUND)
        val second = sessionExercise(listOf(set("b1", 1))).copy(
            id = "b", supersetGroupId = "compound", groupType = WorkoutExerciseGroupType.COMPOUND)
        val totals = workoutRoutineTotals(listOf(first, second))
        assertEquals(2, totals.workingSetCount)
        assertEquals(400.0, totals.volumeKg)
    }

    @Test fun aDropStepTakesPriorityOverSupersetNavigationAndWarmupStaysOnItsExercise() {
        val a = exercise("a")
        val b = exercise("b", 1)
        val root = set("root", 1, false)
        val drop = set("drop", 2, false).copy(setType = WorkoutSetType.DROP, dropParentSetId = "root")
        val detail = WorkoutExerciseDetail("record", a, listOf(a, b), listOf(root, drop),
            sessionSets = mapOf("b" to listOf(set("b1", 1, false))))
        assertEquals(WorkoutSetAdvance(startRest = false), workoutSetAdvance(detail, "root"))
        assertEquals(WorkoutSetAdvance("b", false), workoutSetAdvance(detail.copy(
            sets = listOf(root.copy(isCompleted = true), drop)), "drop"))
        assertEquals(WorkoutSetAdvance(), workoutSetAdvance(detail.copy(sets = listOf(root.copy(setType = WorkoutSetType.WARMUP))), "root"))
    }
}
