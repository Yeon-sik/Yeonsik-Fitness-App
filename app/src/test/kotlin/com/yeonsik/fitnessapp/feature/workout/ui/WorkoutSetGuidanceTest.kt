package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetType
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import org.junit.Assert.*
import org.junit.Test

class WorkoutSetGuidanceTest {
    private fun set(index: Int) = WorkoutSet("set-$index", index, 20.0, 8, 2, 90,
        true, 0, 0.0, 0.0, 0.0, LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)

    @Test fun historySeedsOnlyEmptyRowsAndRetainsExplicitZeroAndSavedInputs() {
        val empty = set(1).copy(weightKg = 0.0, actualReps = 0, rir = null,
            inputLoadValue = null, isCompleted = false)
        fun suggestion(current: WorkoutSet) = initialWorkoutSetSuggestion(current, set(1),
            FitnessRecordContract.WEIGHT_REPS, MassUnit.KG, listOf(LoadState.EXTERNAL_LOAD))
        assertEquals("20", suggestion(empty)!!.load)
        assertEquals("8", suggestion(empty)!!.reps)
        assertNull(suggestion(empty.copy(inputLoadValue = 0.0)))
        assertNull(suggestion(empty.copy(actualReps = 7)))
        assertNull(suggestion(empty.copy(isCompleted = true)))
        assertNull(suggestion(empty.copy(weightKg = 15.0)))
        assertEquals("20kg - 8회", workoutSetReferenceLabel(set(1), FitnessRecordContract.WEIGHT_REPS, MassUnit.KG, compact = true))
    }

    @Test fun firstAndOnlyVisibleSetsAreProtectedEvenWithSparseHistory() {
        assertFalse(canRemoveWorkoutSet(listOf(set(1), set(2)), set(1)))
        assertTrue(canRemoveWorkoutSet(listOf(set(1), set(2)), set(2)))
        assertFalse(canRemoveWorkoutSet(listOf(set(3)), set(3)))
        assertFalse(canRemoveWorkoutSet(listOf(set(3), set(4)), set(3)))
    }

    @Test fun historyMatchesNumberInsteadOfListPositionAndRejectsAmbiguousOrIncompleteSets() {
        val history = WorkoutExerciseHistory("2026-10-01", 0.0,
            listOf(set(3), set(1), set(2).copy(isCompleted = false)))
        assertEquals("set-3", previousWorkoutSet(history, 3)?.id)
        assertNull(previousWorkoutSet(history, 2))
        assertNull(previousWorkoutSet(history, 4))
        assertNull(previousWorkoutSet(history.copy(sets = listOf(set(1), set(1))), 1))
        assertNull(previousWorkoutSet(null, 1))
    }

    @Test fun warmupsAndDropChildrenCannotBeMatchedToDifferentSetStructures() {
        val history = WorkoutExerciseHistory("2026-10-01", 0.0, listOf(set(1)))
        assertNotNull(previousWorkoutSet(history, set(1)))
        assertNull(previousWorkoutSet(history, set(1).copy(setType = WorkoutSetType.WARMUP)))
        assertNull(previousWorkoutSet(history, set(1).copy(dropParentSetId = "root")))
    }

    @Test fun precedingValueUsesEarlierSavedCompletedSetOnly() {
        val sets = listOf(set(5), set(1), set(3).copy(isCompleted = false))
        assertEquals("set-1", precedingCompletedWorkoutSet(sets, 4)?.id)
        assertNull(precedingCompletedWorkoutSet(sets, 1))
    }

    @Test fun originalPoundsAreConvertedToDestinationKilograms() {
        val source = set(1).copy(weightKg = 45.359237, inputLoadValue = 100.0, inputLoadUnit = MassUnit.LB)
        val draft = workoutSetDraftValues(source, FitnessRecordContract.WEIGHT_REPS, MassUnit.KG,
            listOf(LoadState.EXTERNAL_LOAD), LoadState.EXTERNAL_LOAD)!!
        assertEquals("45.36", draft.load)
        assertEquals("8", draft.reps)
        assertEquals("2", draft.rir)
    }

    @Test fun canonicalFallbackConvertsWithoutGroupingAndPreservesExplicitZeroLoad() {
        val source = set(1).copy(weightKg = 1000.0, inputLoadValue = null, inputLoadUnit = null)
        assertEquals("2204.62", workoutSetDraftValues(source, FitnessRecordContract.WEIGHT_REPS,
            MassUnit.LB, emptyList(), LoadState.EXTERNAL_LOAD)!!.load)
        val zero = set(1).copy(weightKg = 0.0, inputLoadValue = 0.0, actualReps = 0)
        val draft = workoutSetDraftValues(zero, FitnessRecordContract.WEIGHT_REPS, MassUnit.KG,
            emptyList(), LoadState.EXTERNAL_LOAD)!!
        assertEquals("0", draft.load)
        assertEquals("0", draft.reps)
        assertEquals("", workoutSetDraftValues(zero.copy(inputLoadValue = null),
            FitnessRecordContract.WEIGHT_REPS, MassUnit.KG, emptyList(), LoadState.EXTERNAL_LOAD)!!.load)
    }

    @Test fun incompatibleLoadModeCannotOverwriteDraft() {
        val source = set(1).copy(loadState = LoadState.BAND_ASSISTED, assistedWeightKg = 20.0)
        assertNull(workoutSetDraftValues(source, FitnessRecordContract.ASSISTED_WEIGHT_REPS,
            MassUnit.KG, listOf(LoadState.ASSISTED), LoadState.ASSISTED))
        assertNull(workoutSetDraftValues(set(1).copy(isCompleted = false),
            FitnessRecordContract.WEIGHT_REPS, MassUnit.KG, LoadState.entries, LoadState.EXTERNAL_LOAD))
    }

    @Test fun timedHistoryCopiesDurationAndDoesNotInventRepetitionsOrRir() {
        val source = set(1).copy(durationSeconds = 75, loadState = null)
        val draft = workoutSetDraftValues(source, FitnessRecordContract.TIME, MassUnit.KG,
            emptyList(), null)!!
        assertEquals("75", draft.duration)
        assertEquals("", draft.reps)
        assertEquals("", draft.load)
        assertEquals("", draft.rir)
        assertEquals("75초", workoutSetReferenceLabel(source, FitnessRecordContract.TIME, MassUnit.KG))
    }

    @Test fun bodyweightDoesNotReuseHistoricalMassAndAssistanceUsesItsOwnCanonicalValue() {
        val bodyweight = set(1).copy(loadState = LoadState.BODYWEIGHT)
        val draft = workoutSetDraftValues(bodyweight, FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS,
            MassUnit.KG, listOf(LoadState.BODYWEIGHT, LoadState.ADDED_WEIGHT), LoadState.ADDED_WEIGHT)!!
        assertEquals("", draft.load)
        assertEquals(LoadState.BODYWEIGHT, draft.loadState)
        val assisted = set(1).copy(loadState = LoadState.ASSISTED, assistedWeightKg = 12.5,
            inputLoadValue = null, inputLoadUnit = null)
        assertEquals("12.5", workoutSetDraftValues(assisted, FitnessRecordContract.ASSISTED_WEIGHT_REPS,
            MassUnit.KG, emptyList(), LoadState.ASSISTED)!!.load)
    }
}
