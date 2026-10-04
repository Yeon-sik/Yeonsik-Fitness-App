package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.core.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutCancelAndTrendUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cancellationIsExplicitAndOnlyShownForAnInProgressWorkout() {
        val status = mutableStateOf("in_progress")
        var cancelRequests = 0
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WorkoutSessionScreen(
                        WorkoutSessionUiState.Ready("owner", session(status.value)),
                        "owner", MassUnit.KG, {}, { cancelRequests++ }
                    )
                }
            }
        }
        compose.onNodeWithText("운동 취소").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancelRequests); status.value = "completed" }
        compose.onNodeWithText("운동 취소").assertDoesNotExist()
    }

    @Test
    fun cancellationPromptClearlyOffersContinuingWithoutDeleting() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            FitnessComposeTheme(false) {
                WorkoutDeleteConfirmationDialog(
                    WorkoutDeleteConfirmationUiState.Ready("owner", "draft", cancelWorkout = true),
                    "owner",
                    object : WorkoutDeleteConfirmationActions {
                        override fun confirm() { confirmed++ }
                        override fun dismiss() { dismissed++ }
                    }
                )
            }
        }
        compose.onNodeWithText("기록 없이 취소").assertExists()
        compose.onNodeWithText("계속 운동").performClick()
        compose.runOnIdle { assertEquals(0, confirmed); assertEquals(1, dismissed) }
    }

    @Test
    fun tappingPointsShowsFullDatesSwitchesDetailsAndDismissesOnEmptySpace() {
        val unit = mutableStateOf(MassUnit.KG)
        compose.setContent {
            FitnessComposeTheme(false) {
                FitnessTrendChart(
                    fitnessTrendPresentation(listOf(
                        FitnessTrendPoint("10/1", MassUnit.fromKg(1000.0, unit.value), "2026-10-01"),
                        FitnessTrendPoint("10/3", MassUnit.fromKg(1000.0, unit.value), "2026-10-03")
                    ), minimumPoints = 2),
                    modifier = Modifier.width(280.dp),
                    unit = unit.value.symbol()
                )
            }
        }
        val plot = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        plot.performTouchInput { click(Offset(24.dp.toPx() + 4.dp.toPx(), height / 2f)) }
        compose.onNodeWithText("2026-10-01").assertExists()
        compose.onNodeWithText("1000kg").assertExists()
        plot.performTouchInput { click(Offset(width - 24.dp.toPx(), height / 2f)) }
        compose.onNodeWithText("2026-10-01").assertDoesNotExist()
        compose.onNodeWithText("2026-10-03").assertExists()
        compose.runOnIdle { unit.value = MassUnit.LB }
        compose.onNodeWithText("2026-10-03").assertDoesNotExist()
        plot.performTouchInput { click(Offset(width - 24.dp.toPx(), height / 2f)) }
        compose.onNodeWithText("2204.6lb").assertExists()
        plot.performTouchInput { click(Offset(width / 2f, 8.dp.toPx())) }
        compose.onNodeWithText("2026-10-03").assertDoesNotExist()
    }

    @Test
    fun largeTextAtBothEdgePointsStaysInsideANarrowPlot() {
        compose.setContent {
            FitnessComposeTheme(true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(240.dp)) {
                        FitnessTrendChart(
                            fitnessTrendPresentation(listOf(
                                FitnessTrendPoint("10/1", 100.0, "2026-10-01"),
                                FitnessTrendPoint("10/3", 9000.0, "2026-10-03")
                            ), minimumPoints = 2),
                            unit = "kg"
                        )
                    }
                }
            }
        }
        val plot = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        listOf(0 to "2026-10-01", 1 to "2026-10-03").forEach { (index, date) ->
            val selectPoint = plot.fetchSemanticsNode().config[SemanticsActions.CustomActions][index].action
            compose.runOnIdle { assertTrue(selectPoint()) }
            val plotBounds = plot.fetchSemanticsNode().boundsInRoot
            val dateBounds = compose.onNodeWithText(date, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(dateBounds.left >= plotBounds.left)
            assertTrue(dateBounds.right <= plotBounds.right)
            assertTrue(dateBounds.top >= plotBounds.top)
            assertTrue(dateBounds.bottom <= plotBounds.bottom)
        }
    }

    private fun session(status: String) = WorkoutSessionSnapshot(
        recordId = "draft", title = "무산소 운동", status = status,
        startedAt = "", durationSeconds = 0, totalVolumeKg = 0.0,
        completedSetCount = 0, exercises = emptyList(), recentVolumes = emptyList()
    )
}
