package com.yeonsik.fitnessapp.feature.exercise.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Renders fixture state only; never opens the app's repositories or creates workout records. */
class ExercisePickerScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun routineEntryFocusesSearchAndKeepsFocusWhileTyping() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val state = showPicker(scenario, ExercisePickerSelectionMode.ROUTINE_ADD)
            await { findNode { it.isEditable && it.isFocused } != null }
            val editor = requireNotNull(findNode { it.isEditable && it.isFocused })
            assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "가슴")
            }))
            await { findNode { it.isEditable && it.isFocused && it.text?.contains("가슴") == true } != null }
            scenario.onActivity { assertEquals("가슴", state.value.query) }
        }
    }

    @Test
    fun switchingExpandedFamiliesKeepsTheTappedFamilyAtItsCurrentPosition() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val state = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD)
            capture("picker-entry")
            tapFamily("첫 운동 그룹 변형 선택", forward = true)
            val (secondBefore, secondAfter) = tapFamily("둘째 운동 그룹 변형 선택", forward = true)
            assertCameraStayed(secondBefore, secondAfter)
            assertVariantVisible("둘째 운동 변형 01")
            scenario.onActivity { assertEquals("b_family", state.value.selectedFamilyId) }
            capture("picker-second-family")

            val (firstBefore, firstAfter) = tapFamily("첫 운동 그룹 변형 선택", forward = false)
            assertCameraStayed(firstBefore, firstAfter)
            assertVariantVisible("첫 운동 변형 01")
            scenario.onActivity { assertEquals("a_family", state.value.selectedFamilyId) }
        }
    }

    private fun assertCameraStayed(beforeTop: Int, afterTop: Int) {
        assertTrue("Tapped family moved on screen: $beforeTop -> $afterTop", abs(afterTop - beforeTop) <= 24)
    }

    private fun assertVariantVisible(firstVariant: String) {
        await { findNode { it.text?.contains(firstVariant) == true && it.isVisibleToUser } != null }
    }

    private fun showPicker(
        scenario: ActivityScenario<ComponentActivity>,
        mode: ExercisePickerSelectionMode
    ): MutableState<ExercisePickerUiState.Ready> {
        val screen = if (mode == ExercisePickerSelectionMode.ROUTINE_ADD) {
            FitnessScreen.ROUTINE_ADD
        } else FitnessScreen.WORKOUT_EXERCISE_ADD
        val state = mutableStateOf(ExercisePickerUiState.Ready(
            ownerId = "fixture-owner", mode = screen, selectionMode = mode,
            recordId = "fixture-record", replacementId = null, routineId = "fixture-routine",
            query = "", bodyPart = null, primarySubPart = null, equipmentCategory = null,
            sortOrder = RuntimeExercisePicker.SortOrder.RECENT,
            families = fixtureFamilies(), availablePrimarySubParts = emptyList(),
            selectedFamilyId = null, selectedPresetId = null
        ))
        val actions = FixtureActions(state)
        scenario.onActivity { activity ->
            activity.setContent {
                FitnessComposeTheme(dark = false) {
                    ExercisePickerScreen(state.value, "fixture-owner", screen, actions)
                }
            }
        }
        await { findNode { it.isEditable } != null }
        return state
    }

    private fun tapFamily(description: String, forward: Boolean): Pair<Int, Int> {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        repeat(40) {
            val button = findNode { it.contentDescription?.toString() == description && it.isVisibleToUser }
            if (button != null) {
                val topBefore = bounds(button).top
                var clickable: AccessibilityNodeInfo? = button
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                assertTrue(requireNotNull(clickable).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await {
                    findNode {
                        it.contentDescription?.toString() == description.replace("변형 선택", "변형 닫기") &&
                            it.isVisibleToUser
                    } != null
                }
                val expandedButton = requireNotNull(findNode {
                    it.contentDescription?.toString() == description.replace("변형 선택", "변형 닫기") &&
                        it.isVisibleToUser
                })
                return Pair(topBefore, bounds(expandedButton).top)
            }
            val list = requireNotNull(findNode { it.isScrollable })
            if (!list.performAction(action)) {
                capture("picker-scroll-failure")
                throw AssertionError("Could not scroll to $description: ${list.actionList}, ${bounds(list)}")
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not find $description")
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.refresh()) return null
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                visit(child)?.let { return it }
            }
            return null
        }
        return automation.rootInActiveWindow?.let(::visit)
    }

    private fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for the picker UI")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("picker-ui-qa"))
        val bitmap = requireNotNull(automation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun fixtureFamilies(): List<RuntimeExercisePicker.FamilyResult> {
        val families = JSONObject()
        val exercises = JSONArray()
        listOf("a_family" to "첫", "b_family" to "둘째").forEach { (familyId, name) ->
            families.put(familyId, JSONObject()
                .put("nameKo", "$name 운동 그룹").put("nameEn", familyId)
                .put("defaultUiPart", "chest")
                .put("allowedLoadStates", JSONArray().put("external_load")))
            // Both expanded cards exceed the viewport, so the list can align either header at top.
            repeat(24) { index ->
                val presetId = "${familyId}_${index + 1}"
                val title = "$name 운동 변형 ${(index + 1).toString().padStart(2, '0')}"
                exercises.put(JSONObject()
                    .put("status", "mapped").put("legacyExerciseId", presetId)
                    .put("familyId", familyId).put("canonicalPresetId", presetId)
                    .put("presetNameKo", title).put("presetNameEn", presetId)
                    .put("nameKo", title).put("nameEn", presetId)
                    .put("defaultUiPart", "chest").put("primarySubPart", "overall_chest")
                    .put("primarySubPartNameKo", "가슴").put("legacyEquipment", "barbell")
                    .put("legacyRecordType", "weight_reps").put("defaultLoadState", "external_load")
                    .put("variant", JSONObject().put("equipment", "barbell").put("grip", "fixture_$index")))
            }
        }
        val catalog = RuntimeExerciseCatalog.fromJson(JSONObject()
            .put("schemaVersion", 1).put("families", families).put("legacyExercises", exercises))
        return RuntimeExercisePicker(catalog).search("").sortedBy { it.family.familyId }
    }

    private class FixtureActions(private val state: MutableState<ExercisePickerUiState.Ready>) : ExercisePickerScreenActions {
        override fun back() = Unit
        override fun search(query: String) { state.value = state.value.copy(query = query) }
        override fun setBodyPart(bodyPart: BodyPart?) = Unit
        override fun setPrimarySubPart(primarySubPart: String?) = Unit
        override fun selectMuscleGroup(groupId: String) = Unit
        override fun setEquipmentCategory(category: UiEquipmentCategory?) = Unit
        override fun setSortOrder(order: RuntimeExercisePicker.SortOrder) = Unit
        override fun resetFilters() = Unit
        override fun selectFamily(familyId: String) {
            state.value = state.value.copy(
                selectedFamilyId = familyId.takeUnless { it == state.value.selectedFamilyId },
                selectedPresetId = null
            )
        }
        override fun selectPreset(familyId: String, presetId: String) {
            state.value = state.value.copy(selectedFamilyId = familyId, selectedPresetId = presetId)
        }
        override fun choose(preset: RuntimeExercisePreset) = Unit
    }
}
