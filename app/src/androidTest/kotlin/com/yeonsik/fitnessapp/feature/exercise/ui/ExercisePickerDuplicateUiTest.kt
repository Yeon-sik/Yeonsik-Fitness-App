package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseSelectionIdentity
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compose semantics and actual touch input against fixtures, never personal workout data. */
class ExercisePickerDuplicateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val catalog get() = ExerciseFamilyCatalog.load(instrumentation.targetContext)

    @Test fun twoVariantsFromOneFamilyStaySelectedInTheCartAndCanBeRemoved() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스미스")
            .first { it.presets.any { preset -> preset.storageExerciseId == "chest_smith_incline_bench_press" } }
        val selected = result.presets.filter { it.storageExerciseId in listOf(
            "chest_smith_incline_bench_press", "chest_smith_flat_bench_press") }
        val fixture = show(result, null)
        val familyDescription = "${result.family.displayName()} 변형 선택"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(familyDescription))
        compose.onNodeWithContentDescription(familyDescription).performClick()
        compose.onAllNodesWithText("선택됨").assertCountEquals(0)
        val sheet = compose.onNodeWithContentDescription("${result.family.displayName()} 변형 목록")
        selected.forEach { preset ->
            val label = "${preset.pickerDisplayName()} 선택"
            sheet.performScrollToNode(hasContentDescription(label))
            compose.onNodeWithContentDescription(label).performClick()
            compose.onNodeWithContentDescription("${preset.pickerDisplayName()} 선택됨").assertIsEnabled()
        }
        compose.runOnIdle { assertEquals(2, fixture.state.value.pendingPresets.size) }
        compose.onNodeWithText("선택 완료 · 2개 담김").performClick()
        compose.onNodeWithTag("exercise-picker-pending").assertIsDisplayed()
        compose.onNodeWithTag("exercise-picker-confirm").assertIsEnabled()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "requirements-picker-cart.png")
            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onNodeWithContentDescription("${selected.first().displayName()} 목록에서 빼기").performClick()
        compose.onNodeWithText("1개 종목 추가").performClick()
        compose.runOnIdle { assertEquals(listOf(selected.last().presetId), fixture.confirmedIds) }
    }

    @Test fun alreadyAddedSinglePresetIsDisabledAndIgnoresTouch() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("펙덱 플라이").single()
        val preset = result.presets.single()
        val fixture = show(result, preset)
        val description = "${preset.pickerDisplayName()} 추가됨"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertNull(fixture.chosenPresetId) }
    }

    @Test fun existingVariantIgnoresTouchWhileAnotherVariantCanStillBeChosen() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스미스")
            .first { family -> family.presets.any { it.storageExerciseId == "chest_smith_incline_bench_press" } }
        val incline = result.presets.first { it.storageExerciseId == "chest_smith_incline_bench_press" }
        val flat = result.presets.first { it.storageExerciseId == "chest_smith_flat_bench_press" }
        val fixture = show(result, incline)
        val familyDescription = "${result.family.displayName()} 변형 선택"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(familyDescription))
        compose.onNodeWithContentDescription(familyDescription).performClick()
        val sheet = compose.onNodeWithContentDescription("${result.family.displayName()} 변형 목록")
        val duplicate = "${incline.displayName()} 추가됨"
        sheet.performScrollToNode(hasContentDescription(duplicate))
        compose.onNodeWithContentDescription(duplicate).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertNull(fixture.state.value.selectedPresetId)
            assertNull(fixture.chosenPresetId)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, "exercise-picker-duplicate.png")
            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val available = "${flat.displayName()} 선택"
        sheet.performScrollToNode(hasContentDescription(available))
        compose.onNodeWithContentDescription(available).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(flat.presetId, fixture.chosenPresetId) }
    }

    @Test fun scrollingDownAtTheTopOfTheListAndHeaderKeepsTheSheetStationary() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스미스")
            .first { it.presets.size > 2 }
        val fixture = show(result, null)
        val familyDescription = "${result.family.displayName()} 변형 선택"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(familyDescription))
        compose.onNodeWithContentDescription(familyDescription).performClick()
        val handle = compose.onNodeWithTag("exercise-variant-handle", useUnmergedTree = true)
        val topBefore = handle.fetchSemanticsNode().boundsInRoot.top
        val list = compose.onNodeWithTag("exercise-variant-list")
        list.performTouchInput { swipeUp() }
        list.performScrollToIndex(0)
        repeat(3) { list.performTouchInput { swipeDown() } }
        compose.onNodeWithText(familyDescription).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(result.family.familyId, fixture.state.value.selectedFamilyId) }
        assertEquals(topBefore, handle.fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun handleDragAndScrimTapStillDismissTheSheet() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스미스")
            .first { it.presets.size > 2 }
        val fixture = show(result, null)
        val familyDescription = "${result.family.displayName()} 변형 선택"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(familyDescription))
        compose.onNodeWithContentDescription(familyDescription).performClick()
        compose.onNodeWithTag("exercise-variant-handle", useUnmergedTree = true).performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(center.x, center.y + 500f), 200)
        }
        compose.onNodeWithTag("exercise-variant-list").assertDoesNotExist()
        compose.runOnIdle { assertNull(fixture.state.value.selectedFamilyId) }
        compose.onNodeWithContentDescription(familyDescription).performClick()
        val handle = compose.onNodeWithTag("exercise-variant-handle", useUnmergedTree = true)
        val handleTop = handle.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 20f - handleTop)) }
        compose.onNodeWithTag("exercise-variant-list").assertDoesNotExist()
    }

    @Test fun selectedVariantsCanBeToggledOffWithinTheSheet() {
        val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스미스")
            .first { it.presets.size > 2 }
        val fixture = show(result, null)
        val familyDescription = "${result.family.displayName()} 변형 선택"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(familyDescription))
        compose.onNodeWithContentDescription(familyDescription).performClick()
        val preset = result.presets.first()
        compose.onNodeWithContentDescription("${preset.pickerDisplayName()} 선택").performClick()
        compose.onNodeWithContentDescription("${preset.pickerDisplayName()} 선택됨").performClick()
        compose.runOnIdle { assertTrue(fixture.state.value.pendingPresets.isEmpty()) }
        compose.onNodeWithContentDescription("${preset.pickerDisplayName()} 선택").assertIsEnabled()
    }

    private fun show(result: RuntimeExercisePicker.FamilyResult, existing: RuntimeExercisePreset?): Fixture {
        val fixture = Fixture(result, existing)
        compose.setContent {
            FitnessComposeTheme(false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ExercisePickerScreen(fixture.state.value, "owner", FitnessScreen.WORKOUT_EXERCISE_ADD, fixture)
                }
            }
        }
        return fixture
    }

    private class Fixture(result: RuntimeExercisePicker.FamilyResult, existing: RuntimeExercisePreset?) : ExercisePickerScreenActions {
        val state = mutableStateOf(ExercisePickerUiState.Ready("owner", FitnessScreen.WORKOUT_EXERCISE_ADD,
            ExercisePickerSelectionMode.WORKOUT_ADD, "record", null, null, "", null, null, null,
            RuntimeExercisePicker.SortOrder.RECENT, listOf(result), emptyList(), null, null,
            unavailableExercises = existing?.let { setOf(WorkoutExerciseSelectionIdentity.from(
                ExerciseMasterAdapter.toWorkoutExerciseReplacement(it))) }.orEmpty()))
        var chosenPresetId: String? = null
        var confirmedIds: List<String> = emptyList()
        override fun back() = Unit
        override fun search(query: String) = Unit
        override fun setBodyPart(bodyPart: BodyPart?) = Unit
        override fun setPrimarySubPart(primarySubPart: String?) = Unit
        override fun selectMuscleGroup(groupId: String) = Unit
        override fun clearBodyPartSelection() = Unit
        override fun setEquipmentCategory(category: UiEquipmentCategory?) = Unit
        override fun setSortOrder(order: RuntimeExercisePicker.SortOrder) = Unit
        override fun resetFilters() = Unit
        override fun selectFamily(familyId: String) {
            state.value = state.value.copy(selectedFamilyId = familyId.takeUnless {
                it == state.value.selectedFamilyId }, selectedPresetId = null)
        }
        override fun selectPreset(familyId: String, presetId: String) {
            state.value = state.value.copy(selectedFamilyId = familyId, selectedPresetId = presetId)
        }
        override fun choose(preset: RuntimeExercisePreset) {
            chosenPresetId = preset.presetId
            state.value = state.value.copy(pendingPresets = state.value.pendingPresets + preset, selectedPresetId = null)
        }
        override fun chooseManual(exercise: ManualWorkoutExercise) = Unit
        override fun removePendingPreset(presetId: String) {
            state.value = state.value.copy(pendingPresets = state.value.pendingPresets.filterNot { it.presetId == presetId })
        }
        override fun confirmPendingSelection() { confirmedIds = state.value.pendingPresets.map { it.presetId } }
    }
}
