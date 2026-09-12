package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.exercise.LoadState
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSessionScreenTest {
    @Test
    fun completedSetsUseStoredWeightAndReps() {
        val exercise = sessionExercise(
            FitnessRecordContract.WEIGHT_REPS,
            listOf(workoutSet(1, weightKg = 80.0, actualReps = 10, loadState = LoadState.EXTERNAL_LOAD))
        )

        assertEquals(
            listOf("1세트 80kg × 10회"),
            completedSetSummaryLines(exercise, MassUnit.KG)
        )
    }

    @Test
    fun completedSetSummaryPreservesAssistedAndTimeMeanings() {
        val assisted = sessionExercise(
            FitnessRecordContract.ASSISTED_WEIGHT_REPS,
            listOf(workoutSet(2, actualReps = 8, assistedWeightKg = 25.0, loadState = LoadState.ASSISTED))
        )
        val timed = sessionExercise(
            FitnessRecordContract.TIME,
            listOf(workoutSet(3, durationSeconds = 45))
        )

        assertEquals(listOf("2세트 25kg × 8회"), completedSetSummaryLines(assisted, MassUnit.KG))
        assertEquals(listOf("3세트 45초"), completedSetSummaryLines(timed, MassUnit.KG))
    }

    private fun sessionExercise(
        recordType: String,
        completedSets: List<WorkoutSet>
    ) = WorkoutSessionExercise(
        id = "exercise-row",
        exerciseId = "exercise",
        orderIndex = 0,
        name = "테스트 종목",
        uiPart = "",
        equipment = "",
        recordType = recordType,
        recordTypeLabel = "테스트",
        familyIdentity = null,
        completedSetCount = completedSets.size,
        totalSetCount = completedSets.size,
        completedSets = completedSets
    )

    private fun workoutSet(
        setIndex: Int,
        weightKg: Double = 0.0,
        actualReps: Int = 0,
        durationSeconds: Int = 0,
        assistedWeightKg: Double = 0.0,
        loadState: LoadState? = null
    ) = WorkoutSet(
        id = "set-$setIndex",
        setIndex = setIndex,
        weightKg = weightKg,
        actualReps = actualReps,
        rir = null,
        restSeconds = null,
        isCompleted = true,
        durationSeconds = durationSeconds,
        distanceMeters = 0.0,
        assistedWeightKg = assistedWeightKg,
        addedWeightKg = 0.0,
        loadState = loadState,
        inputLoadValue = null,
        inputLoadUnit = null
    )
}
