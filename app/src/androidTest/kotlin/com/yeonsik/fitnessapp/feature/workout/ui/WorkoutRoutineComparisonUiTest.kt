package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Rule
import org.junit.Test

class WorkoutRoutineComparisonUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneRoutineCardUpdatesTheAggregateWithoutRepeatingPerSet() {
        val previous = WorkoutRoutineTotals(12, 0, 4680.0, 120, 0, listOf("bench"))
        var state by mutableStateOf(WorkoutSessionSnapshot("record", "상체", "in_progress", "", 0,
            3240.0, 8, emptyList(), emptyList(), routineId = "routine",
            previousRoutine = WorkoutRoutineComparison("2026-10-13", 4680.0, 12, totals = previous),
            routineTotals = previous.copy(workingSetCount = 8, volumeKg = 3240.0, repetitions = 80)))
        compose.setContent { FitnessComposeTheme(false) { WorkoutRoutineComparisonCard(state, MassUnit.KG) } }
        compose.onAllNodesWithTag("workout-routine-comparison").assertCountEquals(1)
        compose.onNodeWithText("본세트 8 / 지난번 12세트").assertExists()
        compose.onNodeWithTag("workout-routine-volume-difference").assertTextContains("-30.8%", substring = true)
        compose.runOnIdle { state = state.copy(routineTotals = previous.copy(workingSetCount = 10, volumeKg = 4000.0)) }
        compose.onAllNodesWithTag("workout-routine-comparison").assertCountEquals(1)
        compose.onNodeWithText("본세트 10 / 지난번 12세트").assertExists()
    }

    @Test fun firstRoutineHasNoInventedPercentageOrBenchmark() {
        val state = WorkoutSessionSnapshot("record", "상체", "in_progress", "", 0, 0.0, 0,
            emptyList(), emptyList(), routineId = "routine")
        compose.setContent { FitnessComposeTheme(false) { WorkoutRoutineComparisonCard(state, MassUnit.KG) } }
        compose.onNodeWithText("첫 기록입니다. 다음 운동부터 이전 완료 기록과 비교합니다.").assertExists()
        compose.onNodeWithTag("workout-routine-volume-difference").assertDoesNotExist()
    }
}
