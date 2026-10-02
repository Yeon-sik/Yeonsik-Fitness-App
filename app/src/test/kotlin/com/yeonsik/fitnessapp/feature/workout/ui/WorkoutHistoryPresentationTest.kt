package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutHistoryPresentationTest {
    @Test
    fun setTablesKeepSevenColumnsAndStartAnotherTableAfterSeven() {
        val batches = workoutHistorySetBatches((1..15).map(::set))

        assertEquals(listOf(7, 7, 1), batches.map { it.size })
        assertEquals(listOf(1, 8, 15), batches.map { it.first().setIndex })
    }

    @Test
    fun setTableUsesOneDecimalWeightAndSeparateRepRow() {
        val rows = workoutSetTableRows(
            recordType = "weight_reps",
            sets = listOf(set(1, weightKg = 80.06, reps = 8)),
            unit = MassUnit.KG
        )

        assertEquals("중량(${MassUnit.KG.symbol()})", rows.upperLabel)
        assertEquals("횟수", rows.lowerLabel)
        assertEquals("80.1", rows.upperValues.single().visibleValue)
        assertEquals("8", rows.lowerValues.single().visibleValue)
        assertEquals("1세트, 횟수 8회", rows.lowerValues.single().spokenValue)
    }

    @Test
    fun trendPointsRetainUnknownOneRepMaxAsUnknown() {
        val points = workoutExerciseTrendPoints(
            listOf(
                WorkoutExerciseHistory("2026-10-01", 120.0, emptyList(), "one", 60.0),
                WorkoutExerciseHistory("2026-10-02", 0.0, emptyList(), "two", null)
            )
        )

        assertEquals(2, points.size)
        assertEquals(60.0, points.first().estimatedOneRepMaxKg!!, 0.0)
        assertNull(points.last().estimatedOneRepMaxKg)
        assertEquals(0.0, points.last().totalVolumeKg, 0.0)
    }

    private fun set(
        index: Int,
        weightKg: Double = 50.0,
        reps: Int = 8
    ) = WorkoutSet(
        id = "set-$index",
        setIndex = index,
        weightKg = weightKg,
        actualReps = reps,
        rir = null,
        restSeconds = null,
        isCompleted = true,
        durationSeconds = 0,
        distanceMeters = 0.0,
        assistedWeightKg = 0.0,
        addedWeightKg = 0.0,
        loadState = LoadState.EXTERNAL_LOAD,
        inputLoadValue = weightKg,
        inputLoadUnit = MassUnit.KG
    )
}
