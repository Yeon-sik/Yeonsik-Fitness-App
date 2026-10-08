package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI fixture only: no personal DB or authentication/sync side effects. */
class WorkoutSetRecordingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preset get() = ExerciseFamilyCatalog.load(context).runtimeCatalog().preset("legs_barbell_back_squat")!!
    private val occurrence get() = WorkoutExercise("occurrence", preset.storageExerciseId, 0,
        preset.displayName(), preset.defaultUiPart, preset.equipmentNameKo, preset.recordType,
        ExerciseFamilyCatalog.empty().identityForPreset(preset))
    private fun set(id: String, index: Int, rest: Int?) = WorkoutSet(id, index, 20.0, 10, 2, rest,
        false, 0, 0.0, 0.0, 0.0, LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)
    private fun detail(sets: List<WorkoutSet> = listOf(set("set-1", 1, 45))) = WorkoutExerciseDetail(
        "record", occurrence, listOf(occurrence), sets,
        recentHistories = listOf(WorkoutExerciseHistory("2026-10-01", 200.0,
            listOf(set("history-set", 1, 120).copy(isCompleted = true)), "history")),
        exerciseRestSeconds = 90)

    @Test fun rowsAppendInsideOneBoxAndDraftsSurviveWhileRestRemainsExerciseScoped() {
        val fixture = show(detail())
        compose.onAllNodesWithTag("workout-set-record-box").assertCountEquals(1)
        compose.onAllNodesWithTag("exercise-rest-seconds").assertCountEquals(1)
        val imageBounds = compose.onNodeWithContentDescription("${occurrence.name} 운동 이미지").fetchSemanticsNode().boundsInRoot
        val boxBounds = compose.onNodeWithTag("workout-set-record-box").fetchSemanticsNode().boundsInRoot
        assertTrue(boxBounds.top >= imageBounds.bottom)
        val weight = hasContentDescription("중량(kg)") and hasSetTextAction()
        compose.onNode(weight).performScrollTo().performTextReplacement("22.5")
        compose.onNodeWithTag("workout-add-set").performScrollTo().performClick()
        compose.onAllNodesWithTag("workout-set-record-box").assertCountEquals(1)
        compose.onAllNodes(weight).assertCountEquals(2)
        compose.onAllNodes(weight)[0].assertTextEquals("22.5")
        compose.onAllNodesWithText("세트 기록").assertCountEquals(1)
        compose.onAllNodesWithText("세트", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("exercise-rest-seconds").performScrollTo().performTextReplacement("150")
        compose.onNodeWithText("적용").performClick()
        compose.runOnIdle {
            assertEquals(150, fixture.state.value.detail.exerciseRestSeconds)
            assertEquals(45, fixture.state.value.detail.sets[0].restSeconds)
            assertNull(fixture.state.value.detail.sets[1].restSeconds)
        }
        compose.onNodeWithContentDescription("1세트 완료").performScrollTo().performClick()
        compose.onAllNodesWithText("저장")[0].performScrollTo().performClick()
        compose.onNodeWithContentDescription("2세트 완료").performScrollTo().performClick()
        compose.onAllNodesWithText("저장")[1].performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(150, 150), fixture.timers)
            assertEquals(45, fixture.saved[0].restSeconds)
            assertNull(fixture.saved[1].restSeconds)
        }
        compose.onNodeWithContentDescription("${occurrence.name} 운동 이미지").performScrollTo()
        screenshot("workout-set-recording.png")
    }

    @Test fun imageUsesBothManifestFramesAtDoubleHeightAndTitleIsThirtyPercentSmaller() {
        compose.mainClock.autoAdvance = false
        show(detail())
        compose.mainClock.advanceTimeBy(32)
        val frameA = compose.onNodeWithTag("workout-exercise-frame-0")
        val boundsA = frameA.fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertEquals(360f * density, boundsA.height, 1f)
        val textLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(occurrence.name).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
            it(textLayout)
        }
        assertEquals(21f, textLayout.single().layoutInput.style.fontSize.value, 0.1f)
        compose.mainClock.advanceTimeBy(1050)
        compose.onNodeWithTag("workout-exercise-frame-1").assertExists()
        val boundsB = compose.onNodeWithTag("workout-exercise-frame-1").fetchSemanticsNode().boundsInRoot
        assertEquals(boundsA, boundsB)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("workout-exercise-frame-0").assertExists()
    }

    @Test fun completedWeightAndRepetitionRowsUseDifferentColorsInBothThemes() {
        val dark = mutableStateOf(false)
        val ready = WorkoutExerciseDetailUiState.Ready("owner", detail(), false, readOnly = true)
        compose.setContent {
            FitnessComposeTheme(dark.value) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    WorkoutDetailScreen(ready, "owner", MassUnit.KG, Fixture(ready))
                }
            }
        }
        fun assertRows() {
            fun colorFor(description: String): Color {
                val results = mutableListOf<TextLayoutResult>()
                compose.onNodeWithContentDescription(description).performScrollTo()
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
                return results.single().layoutInput.style.color
            }
            val weight = colorFor("1세트, 부하 20 kg")
            val reps = colorFor("1세트, 횟수 10회")
            assertNotEquals(weight, reps)
            assertTrue(weight.red > weight.blue && weight.green > weight.blue)
        }
        assertRows()
        compose.runOnIdle { dark.value = true }
        assertRows()
        screenshot("workout-history-weight.png")
    }

    private fun show(detail: WorkoutExerciseDetail): Fixture {
        val fixture = Fixture(WorkoutExerciseDetailUiState.Ready("owner", detail, false))
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutDetailScreen(fixture.state.value, "owner", MassUnit.KG, fixture)
                }
            }
        }
        return fixture
    }
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private class Fixture(ready: WorkoutExerciseDetailUiState.Ready) : WorkoutDetailActions {
        val state = mutableStateOf(ready)
        val timers = mutableListOf<Int?>()
        val saved = mutableListOf<WorkoutSetInput>()
        override fun back() {}
        override fun refresh() {}
        override fun openExercise(exerciseId: String) {}
        override fun linkManualExercise(exerciseId: String) {}
        override fun deleteExercise(recordId: String, exerciseId: String, onResult: (Boolean) -> Unit) {}
        override fun applyPreviousHistory(recordId: String, exerciseId: String, currentSets: List<WorkoutSet>, history: WorkoutExerciseHistory, onResult: (Boolean) -> Unit) {}
        override fun addSet(recordId: String, exerciseId: String, setIndex: Int, input: WorkoutSetInput, onResult: (Boolean) -> Unit) {
            val next = WorkoutSet("set-$setIndex", setIndex, 0.0, 0, null, input.restSeconds, false,
                0, 0.0, 0.0, 0.0, LoadState.EXTERNAL_LOAD, null, MassUnit.KG)
            state.value = state.value.copy(detail = state.value.detail.copy(sets = state.value.detail.sets + next))
            onResult(true)
        }
        override fun updateSet(recordId: String, setId: String, input: WorkoutSetInput, onResult: (Boolean) -> Unit) {
            saved += input
            onResult(true)
        }
        override fun deleteSet(recordId: String, setId: String, onResult: (Boolean) -> Unit) {}
        override fun startRestTimer(restSeconds: Int?) { timers += restSeconds }
        override fun updateExerciseRestSeconds(recordId: String, exerciseId: String, seconds: Int, onResult: (Boolean) -> Unit) {
            state.value = state.value.copy(detail = state.value.detail.copy(exerciseRestSeconds = seconds))
            onResult(true)
        }
        override fun toast(message: String) { error(message) }
    }
}
