package com.yeonsik.fitnessapp.feature.exercise.ui

import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerModelsTest {
    @Test
    fun selectionModeKeepsAddAndReplaceContractsExplicit() {
        assertTrue(
            ExercisePickerSelectionMode.forTarget(
                FitnessScreen.ROUTINE_ADD,
                null
            ) == ExercisePickerSelectionMode.ROUTINE_ADD
        )
        assertTrue(
            ExercisePickerSelectionMode.forTarget(
                FitnessScreen.WORKOUT_EXERCISE_ADD,
                null
            ) == ExercisePickerSelectionMode.WORKOUT_ADD
        )
        assertTrue(
            ExercisePickerSelectionMode.forTarget(
                FitnessScreen.WORKOUT_EXERCISE_ADD,
                "exercise-to-replace"
            ) == ExercisePickerSelectionMode.WORKOUT_REPLACE
        )
    }

    @Test
    fun requestGateRejectsStaleOwnerModeAndTargetResults() {
        val gate = ExercisePickerRequestGate()
        val first = ExercisePickerRequestKey(
            "owner-a",
            FitnessScreen.WORKOUT_EXERCISE_ADD,
            ExercisePickerSelectionMode.WORKOUT_ADD,
            "record-a",
            null,
            null
        )
        val second = first.copy(
            ownerId = "owner-b",
            selectionMode = ExercisePickerSelectionMode.WORKOUT_REPLACE,
            replacementId = "exercise-b"
        )
        val firstToken = gate.begin(first)
        val secondToken = gate.begin(second)

        assertFalse(gate.accepts(firstToken, first))
        assertTrue(gate.accepts(secondToken, second))
        assertFalse(gate.accepts(secondToken, first))
    }
}
