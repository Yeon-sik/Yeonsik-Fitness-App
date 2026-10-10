package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.material3.MaterialTheme
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
        exerciseRestSeconds = 90,
        allowedLoadStates = mapOf(occurrence.id to listOf(LoadState.EXTERNAL_LOAD, LoadState.BAND_RESISTED)))

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
        compose.onNodeWithContentDescription("2세트 완료").performScrollTo().performClick()
        compose.onNodeWithText("저장").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(150, 150), fixture.timers)
            assertEquals(45, fixture.saved[0].restSeconds)
            assertNull(fixture.saved[1].restSeconds)
        }
        compose.onNodeWithContentDescription("${occurrence.name} 운동 이미지").performScrollTo()
        screenshot("workout-set-recording.png")
    }

    @Test fun imageUsesBothManifestFramesInOneCompactCanvasAndTitleIsThirtyPercentSmaller() {
        compose.mainClock.autoAdvance = false
        show(detail())
        compose.mainClock.advanceTimeBy(32)
        val frameA = compose.onNodeWithTag("workout-exercise-frame-0")
        val boundsA = frameA.fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("The image must not reserve the old 360 dp area", boundsA.height <= 220f * density + 1f)
        assertTrue(boundsA.height > 0f)
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

    @Test fun missingSmithInclineNeverUsesTheFlatImageInDetailWithOrWithoutStoredIdentity() {
        compose.mainClock.autoAdvance = false
        fun occurrenceFor(id: String): WorkoutExercise {
            val catalog = ExerciseFamilyCatalog.load(context)
            val variant = requireNotNull(catalog.runtimeCatalog().preset(id))
            return occurrence.copy(exerciseId = variant.storageExerciseId, name = variant.displayName(),
                familyIdentity = catalog.identityForPreset(variant))
        }
        val incline = occurrenceFor("chest_smith_incline_bench_press")
        val flat = occurrenceFor("chest_smith_flat_bench_press")
        val fixture = show(detail().copy(activeExercise = incline, exercises = listOf(incline)))
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 이미지 없음").assertExists()
        compose.onAllNodesWithTag("workout-exercise-frame-0").assertCountEquals(0)
        compose.runOnIdle {
            fixture.state.value = fixture.state.value.copy(detail = fixture.state.value.detail.copy(
                activeExercise = incline.copy(familyIdentity = null), exercises = listOf(incline)))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 이미지 없음").assertExists()
        compose.onAllNodesWithTag("workout-exercise-frame-0").assertCountEquals(0)
        compose.runOnIdle {
            fixture.state.value = fixture.state.value.copy(detail = fixture.state.value.detail.copy(
                activeExercise = flat, exercises = listOf(flat)))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 이미지 없음").assertDoesNotExist()
        compose.onNodeWithContentDescription("${flat.name} 운동 이미지").assertExists()
    }

    @Test fun sessionCardsShowMissingImageForInclineAndExactImageForFlat() {
        compose.mainClock.autoAdvance = false
        val catalog = ExerciseFamilyCatalog.load(context)
        val exercises = listOf("chest_smith_incline_bench_press", "chest_smith_flat_bench_press")
            .mapIndexed { index, id ->
                val variant = requireNotNull(catalog.runtimeCatalog().preset(id))
                WorkoutSessionExercise("occurrence-$index", variant.storageExerciseId, index,
                    variant.displayName(), variant.defaultUiPart, variant.equipmentNameKo,
                    variant.recordType, "중량 · 반복", catalog.identityForPreset(variant), 0, 0)
            }
        val session = WorkoutSessionSnapshot("record", "운동 세션", "in_progress",
            "2026-10-08T12:00:00+09:00", 0, 0.0, 0, exercises, emptyList())
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(12.dp)) {
                    WorkoutSessionScreen(WorkoutSessionUiState.Ready("owner", session), "owner", MassUnit.KG) { }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 이미지 없음").assertExists()
        compose.onNodeWithContentDescription("${exercises[0].name} 운동 이미지").assertDoesNotExist()
        compose.onNodeWithContentDescription("${exercises[1].name} 운동 이미지").assertExists()
    }

    @Test fun everySetControlFitsOneRowAndCompletionPersistsWithoutSeparateSaveButtons() {
        val fixture = show(detail())
        val fields = listOf(
            compose.onNodeWithContentDescription("1세트 설정"),
            compose.onNode(hasContentDescription("중량(kg)") and hasSetTextAction()),
            compose.onNode(hasContentDescription("횟수") and hasSetTextAction()),
            compose.onNode(hasContentDescription("RIR") and hasSetTextAction()),
            compose.onNodeWithContentDescription("1세트 완료"),
            compose.onNodeWithTag("workout-set-load-state-set-1")
        )
        val bounds = fields.map { it.fetchSemanticsNode().boundsInRoot }
        val density = context.resources.displayMetrics.density
        bounds.forEach { assertEquals(bounds[0].center.y, it.center.y, density) }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left + 1f) }
        val card = compose.onNodeWithTag("workout-set-record-box").fetchSemanticsNode().boundsInRoot
        assertTrue("Normal phone size must show the load state without horizontal scrolling", bounds.last().right <= card.right)
        fields[1].performTextReplacement("22.5")
        fields[2].performTextReplacement("12")
        fields[4].performClick()
        compose.runOnIdle {
            assertEquals(22.5, fixture.saved.single().weightKg!!, 0.0)
            assertEquals(12, fixture.saved.single().reps)
            assertTrue(fixture.saved.single().completed)
            assertEquals(listOf(90), fixture.timers)
        }
        fields[1].performTextReplacement("25")
        fields[4].assertIsOff().performClick()
        fields[4].assertIsOn()
        compose.runOnIdle {
            assertEquals(25.0, fixture.saved.last().weightKg!!, 0.0)
            assertTrue(fixture.saved.last().completed)
            assertEquals("Editing a completed set must not restart rest", listOf(90), fixture.timers)
        }
        compose.onNodeWithText("저장").assertDoesNotExist()
        compose.onNodeWithText("세트 삭제").assertDoesNotExist()
        compose.onNodeWithContentDescription("1세트 설정").performClick()
        compose.onNodeWithText("세트 삭제").performClick()
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
        compose.runOnIdle { assertTrue(fixture.state.value.detail.sets.isEmpty()) }
    }

    @Test fun insertionAndDeletingEarlierRowsKeepTheFocusedEditorAndImageNodes() {
        val fixture = show(detail(listOf(set("set-1", 1, null), set("set-2", 2, null))))
        val weight = hasContentDescription("중량(kg)") and hasSetTextAction() and
            hasAnyAncestor(hasTestTag("workout-set-set-2"))
        val boxId = compose.onNodeWithTag("workout-set-record-box").fetchSemanticsNode().id
        val imageId = compose.onNodeWithContentDescription("${occurrence.name} 운동 이미지").fetchSemanticsNode().id
        compose.onNode(weight).performScrollTo().performTextReplacement("33.5")
        val editorId = compose.onNode(weight).fetchSemanticsNode().id
        compose.onNodeWithTag("workout-add-set").performScrollTo().performClick()
        compose.onNode(weight).assertTextEquals("33.5").assertIsFocused()
        compose.runOnIdle { fixture.deleteSet("record", "set-1") { assertTrue(it) } }
        compose.onNode(weight).assertTextEquals("33.5").assertIsFocused()
        assertEquals(editorId, compose.onNode(weight).fetchSemanticsNode().id)
        assertEquals(boxId, compose.onNodeWithTag("workout-set-record-box").fetchSemanticsNode().id)
        assertEquals(imageId, compose.onNodeWithContentDescription("${occurrence.name} 운동 이미지").fetchSemanticsNode().id)
    }

    @Test fun compactRowsRemainSingleLineInDarkThemeAndLargeFont() {
        show(detail(listOf(set("set-1", 1, null), set("set-2", 2, null), set("set-3", 3, null))),
            dark = true, fontScale = 1.3f)
        compose.onNodeWithTag("workout-set-load-state-set-1").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText("외부 중량", useUnmergedTree = true)[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onAllNodesWithTag("workout-set-record-box").assertCountEquals(1)
        screenshot("workout-set-recording-dark-large-font.png")
    }

    @Test fun completedWeightAndRepetitionRowsUseDifferentColorsInBothThemes() {
        val dark = mutableStateOf(false)
        val ready = WorkoutExerciseDetailUiState.Ready("owner", detail(), false, readOnly = true)
        compose.setContent {
            FitnessComposeTheme(dark.value) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(12.dp)) {
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

    private fun show(detail: WorkoutExerciseDetail, dark: Boolean = false, fontScale: Float = 1f): Fixture {
        val fixture = Fixture(WorkoutExerciseDetailUiState.Ready("owner", detail, false))
        compose.setContent {
            FitnessComposeTheme(dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutDetailScreen(fixture.state.value, "owner", MassUnit.KG, fixture)
                }
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
            state.value = state.value.copy(detail = state.value.detail.copy(sets = state.value.detail.sets.map {
                if (it.id != setId) it else it.copy(weightKg = input.weightKg ?: 0.0,
                    actualReps = input.reps ?: 0, rir = input.rir, isCompleted = input.completed,
                    loadState = input.loadState, inputLoadValue = input.inputLoadValue,
                    inputLoadUnit = input.inputLoadUnit)
            }))
            onResult(true)
        }
        override fun deleteSet(recordId: String, setId: String, onResult: (Boolean) -> Unit) {
            state.value = state.value.copy(detail = state.value.detail.copy(sets = state.value.detail.sets.filter { it.id != setId }))
            onResult(true)
        }
        override fun startRestTimer(restSeconds: Int?) { timers += restSeconds }
        override fun updateExerciseRestSeconds(recordId: String, exerciseId: String, seconds: Int, onResult: (Boolean) -> Unit) {
            state.value = state.value.copy(detail = state.value.detail.copy(exerciseRestSeconds = seconds))
            onResult(true)
        }
        override fun toast(message: String) { error(message) }
    }
}
