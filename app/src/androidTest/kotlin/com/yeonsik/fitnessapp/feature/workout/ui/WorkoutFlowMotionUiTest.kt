package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutFlowMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = WorkoutSessionSnapshot("record", "루틴", "in_progress", "", 0, 0.0, 0, emptyList(), emptyList())

    @Test fun imageGrowsBetweenListAndEditorAndShrinksOnReturn() {
        val screen = mutableStateOf(FitnessScreen.WORKOUT_SESSION)
        var density = 1f
        compose.setContent {
            FitnessComposeTheme(false) {
                density = LocalDensity.current.density
                WorkoutFlowHost("owner", screen.value, "record", WorkoutSessionUiState.Ready("owner", snapshot),
                    motionEnabled = true) { destination, _ ->
                    val detail = destination == FitnessScreen.WORKOUT_EXERCISE_DETAIL
                    Column {
                        Box(Modifier.workoutSharedElement(workoutSessionImageAnchor("record", "occurrence"))
                            .size(if (detail) 200.dp else 64.dp)
                            .background(Color.Blue).testTag("shared-image"))
                        Button(onClick = { screen.value = if (detail) FitnessScreen.WORKOUT_SESSION else FitnessScreen.WORKOUT_EXERCISE_DETAIL }) {
                            Text(if (detail) "목록으로" else "기록하기")
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("기록하기").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val growing = compose.onNodeWithTag("shared-image").fetchSemanticsNode().boundsInRoot.width / density
        assertTrue("The shared image must interpolate its bounds, width=$growing", growing > 64f && growing < 200f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("목록으로").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val shrinking = compose.onNodeWithTag("shared-image").fetchSemanticsNode().boundsInRoot.width / density
        assertTrue("The return transition must shrink toward the source, width=$shrinking", shrinking > 64f && shrinking < 200f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("기록하기").assertIsDisplayed()
    }

    @Test fun navigationAndSessionRefreshKeepTheEditorButAccountChangesReleaseIt() {
        val screen = mutableStateOf(FitnessScreen.WORKOUT_SESSION)
        val owner = mutableStateOf("owner")
        val session = mutableStateOf(snapshot)
        compose.setContent {
            FitnessComposeTheme(false) {
                WorkoutFlowHost(owner.value, screen.value, "record", WorkoutSessionUiState.Ready(owner.value, session.value)) { destination, _ ->
                    Column {
                        if (destination == FitnessScreen.WORKOUT_EXERCISE_DETAIL) {
                            var draft by remember { mutableStateOf("") }
                            TextField(draft, { draft = it }, Modifier.testTag("draft"))
                            Button(onClick = { screen.value = FitnessScreen.WORKOUT_SESSION }) { Text("목록으로") }
                        } else {
                            Button(onClick = { screen.value = FitnessScreen.WORKOUT_EXERCISE_DETAIL }) { Text("기록하기") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("기록하기").performClick()
        compose.onNodeWithTag("draft").performTextInput("62.5")
        val editorId = compose.onNodeWithTag("draft").fetchSemanticsNode().id
        compose.onNodeWithText("목록으로").performClick()
        compose.onNodeWithTag("draft").assertDoesNotExist()
        compose.runOnIdle { session.value = snapshot.copy(completedSetCount = 1) }
        compose.onNodeWithText("기록하기").performClick()
        compose.onNodeWithTag("draft").assertTextEquals("62.5")
        assertEquals(editorId, compose.onNodeWithTag("draft").fetchSemanticsNode().id)
        compose.runOnIdle { owner.value = "different-owner" }
        compose.onNodeWithTag("draft").assertTextEquals("")
    }

    @Test fun reducedMotionUsesTheDestinationImmediatelyAndKeepsArtworkPlaybackOff() {
        val screen = mutableStateOf(FitnessScreen.WORKOUT_SESSION)
        compose.setContent {
            FitnessComposeTheme(false) {
                WorkoutFlowHost("owner", screen.value, "record", WorkoutSessionUiState.Ready("owner", snapshot),
                    motionEnabled = false) { destination, _ ->
                    Column {
                        Text(if (LocalWorkoutImagePlayback.current) "재생 중" else "이미지 정지")
                        if (destination == FitnessScreen.WORKOUT_SESSION) {
                            Button(onClick = { screen.value = FitnessScreen.WORKOUT_EXERCISE_DETAIL }) { Text("기록하기") }
                        } else Text("세트 입력")
                    }
                }
            }
        }
        compose.onNodeWithText("기록하기").performClick()
        compose.onNodeWithText("세트 입력").assertIsDisplayed()
        compose.onNodeWithText("이미지 정지").assertIsDisplayed()
        compose.onNodeWithText("재생 중").assertDoesNotExist()
    }
}
