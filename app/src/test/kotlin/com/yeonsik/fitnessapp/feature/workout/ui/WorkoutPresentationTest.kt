package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkoutPresentationTest {
    @Test
    fun sessionExercisesUseStableOrderAndCurrentExerciseIsFirstIncomplete() {
        val first = sessionExercise(id = "z", order = 2, completed = 1, total = 1)
        val second = sessionExercise(id = "b", order = 1, completed = 0, total = 1)
        val tie = sessionExercise(id = "a", order = 1, completed = 0, total = 1)

        val ordered = stableWorkoutSessionExercises(listOf(first, second, tie))

        assertEquals(listOf("a", "b", "z"), ordered.map { it.id })
        assertEquals("a", currentWorkoutExercise(ordered)?.id)
    }

    @Test
    fun sessionProgressClampsInvalidCountsAndHandlesEmptySets() {
        val presentation = workoutSessionProgress(
            listOf(
                sessionExercise(id = "one", order = 1, completed = 4, total = 2),
                sessionExercise(id = "two", order = 2, completed = -1, total = 0)
            )
        )

        assertEquals(1f, presentation.fraction)
        assertEquals("2/2", presentation.label)
        assertEquals(false, presentation.isEmpty)
        assertEquals("세트 없음", workoutSessionProgress(emptyList()).label)
    }

    @Test
    fun elapsedTimeUsesLiveStartForInProgressAndStoredValueForCompleted() {
        val now = 1_700_000_120_000L
        val startedAt = "2023-11-14T22:13:20Z"

        assertEquals(120, workoutElapsedSeconds(startedAt, 0, "in_progress", now))
        assertEquals(45, workoutElapsedSeconds(startedAt, 45, "completed", now))
        assertEquals("01:05", formatWorkoutElapsedSeconds(65))
        assertEquals("1:01:05", formatWorkoutElapsedSeconds(3665))
    }

    @Test
    fun originalInputUnitAndValueRemainTheEditableSource() {
        assertEquals(
            "225",
            initialMassInputValue(
                FitnessRecordContract.WEIGHT_REPS,
                weightKg = 102.0586,
                assistedWeightKg = 0.0,
                addedWeightKg = 0.0,
                inputLoadValue = 225.0,
                inputLoadUnit = MassUnit.LB,
                preferredUnit = MassUnit.KG
            )
        )
        assertSame(MassUnit.LB, editableMassUnit(MassUnit.LB, MassUnit.KG))
    }

    private fun sessionExercise(
        id: String,
        order: Int,
        completed: Int,
        total: Int
    ) = WorkoutSessionExercise(
        id = id,
        exerciseId = "exercise-$id",
        orderIndex = order,
        name = id,
        uiPart = "하체",
        equipment = "바벨",
        recordType = FitnessRecordContract.WEIGHT_REPS,
        recordTypeLabel = "중량 · 반복",
        familyIdentity = null,
        completedSetCount = completed,
        totalSetCount = total,
        completedSets = emptyList()
    )
}
