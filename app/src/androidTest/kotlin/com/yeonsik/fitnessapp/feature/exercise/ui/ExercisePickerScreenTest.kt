package com.yeonsik.fitnessapp.feature.exercise.ui

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Renders fixture state only; never opens the app's repositories or creates workout records. */
class ExercisePickerScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun routineEntryFocusesSearchAndKeepsFocusWhileTyping() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.ROUTINE_ADD)
            await { findNode { it.isEditable && it.isFocused } != null }
            val editor = requireNotNull(findNode { it.isEditable && it.isFocused })
            assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "가슴")
            }))
            await { findNode { it.isEditable && it.isFocused && it.text?.contains("가슴") == true } != null }
            scenario.onActivity {
                assertEquals("", fixture.state.value.query)
                assertEquals(0, fixture.actions.searchCount)
            }
        }
    }

    @Test
    fun typingDoesNotSearchUntilTheSearchButtonIsPressed() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD)
            listOf("덤", "덤벨", "덤벨 레터럴", "덤벨 레터럴 레이즈").forEach { query ->
                setSearchText(query)
                scenario.onActivity {
                    assertEquals("", fixture.state.value.query)
                    assertEquals(0, fixture.actions.searchCount)
                }
            }
            click(requireNotNull(findNode { it.text?.toString() == "검색" }))
            await { fixture.actions.searchCount == 1 }
            scenario.onActivity { assertEquals("덤벨 레터럴 레이즈", fixture.state.value.query) }
            setSearchText("")
            scenario.onActivity {
                assertEquals("덤벨 레터럴 레이즈", fixture.state.value.query)
                assertEquals(1, fixture.actions.searchCount)
            }
            click(requireNotNull(findNode { it.text?.toString() == "검색" }))
            await { fixture.actions.searchCount == 2 }
            scenario.onActivity { assertEquals("", fixture.state.value.query) }
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 30)
    fun keyboardSearchSubmitsTheFinishedNameOnce() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.ROUTINE_ADD)
            await { findNode { it.isEditable && it.isFocused } != null }
            setSearchText("바벨 OHP")
            val editor = requireNotNull(findNode { it.isEditable && it.isFocused })
            assertTrue(editor.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
            await { fixture.actions.searchCount == 1 }
            scenario.onActivity { assertEquals("바벨 OHP", fixture.state.value.query) }
        }
    }

    @Test
    fun filterResetClearsUnsubmittedText() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD)
            setSearchText("입력 중")
            click(scrollMainToText("필터 초기화"))
            scrollBackToSearch()
            await { findNode { it.isEditable && it.text?.contains("입력 중") == true } == null }
            scenario.onActivity {
                assertEquals("", fixture.state.value.query)
                assertEquals(0, fixture.actions.searchCount)
            }
        }
    }

    @Test
    fun singlePresetCardUsesItsOwnImageSlotInsteadOfTheFamilyRepresentative() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val catalog = com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog.load(instrumentation.targetContext)
            val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("펙덱 플라이").single()
            val preset = result.presets.single()
            showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD,
                families = listOf(result))
            findMainButton("${preset.displayName()} 선택")
            // The native preview is decorative to accessibility. Inspect its actual bitmap and
            // measured slot instead of treating a missing accessibility node as a missing image.
            scenario.onActivity { activity ->
                val preview = com.yeonsik.fitnessapp.ui.ExerciseIllustrationPreview(activity,
                    com.yeonsik.fitnessapp.ui.FitnessUi(activity, java.util.function.BooleanSupplier { false }))
                val expected = (requireNotNull(preview.createExact(preset.storageExerciseId)).drawable
                    as android.graphics.drawable.BitmapDrawable).bitmap
                fun imageViews(view: android.view.View): List<android.widget.ImageView> = when (view) {
                    is android.widget.ImageView -> listOf(view)
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { imageViews(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val rendered = imageViews(activity.window.decorView).firstOrNull { image ->
                    image.isShown && (image.drawable as? android.graphics.drawable.BitmapDrawable)
                        ?.bitmap?.sameAs(expected) == true
                }
                assertNotNull("Card must render the selected preset's export", rendered)
                val image = requireNotNull(rendered)
                assertEquals(android.widget.ImageView.ScaleType.FIT_CENTER, image.scaleType)
                assertTrue("Image must stay in its square slot", abs(image.width - image.height) <= 1)
                assertTrue(image.width > 0 && image.height > 0)
            }
            val screenshot = automation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir, "exercise-picker-export.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun newExportImageRendersWithWrappedContextAndInsideTheVariantSheet() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val catalog = com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog.load(instrumentation.targetContext)
            val result = RuntimeExercisePicker(catalog.runtimeCatalog()).search("스쿼트")
                .first { family -> family.presets.any { it.storageExerciseId == "legs_dumbbell_sumo_squat" } }
            assertTrue(result.presets.size > 1)
            val preset = result.presets.first { it.storageExerciseId == "legs_dumbbell_sumo_squat" }
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD,
                families = listOf(result), wrappedContext = true)
            click(scrollMainToDescription("${result.family.displayName()} 변형 선택"))
            await { fixture.state.value.selectedFamilyId == result.family.familyId }
            val buttonDescription = "${preset.displayName()} 선택"
            await { findNode { it.contentDescription?.toString() == "${result.family.displayName()} 변형 목록" } != null }
            scrollSheetToPreset(buttonDescription,
                FixtureFamily(result.family.familyId, result.family.displayName(),
                    result.family.displayName(), result.presets.size))
            scenario.onActivity { activity ->
                val preview = com.yeonsik.fitnessapp.ui.ExerciseIllustrationPreview(activity,
                    com.yeonsik.fitnessapp.ui.FitnessUi(activity, java.util.function.BooleanSupplier { false }))
                val expected = (requireNotNull(preview.createExact(preset.storageExerciseId)).drawable
                    as android.graphics.drawable.BitmapDrawable).bitmap
                fun containsImage(view: android.view.View): Boolean = when (view) {
                    is android.widget.ImageView -> view.isShown &&
                        (view.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.sameAs(expected) == true
                    is android.view.ViewGroup -> (0 until view.childCount).any { containsImage(view.getChildAt(it)) }
                    else -> false
                }
                // ModalBottomSheet has its own window, unlike a main-list card.
                val roots = android.view.inspector.WindowInspector.getGlobalWindowViews()
                assertTrue("The new export must render in the sheet", roots.any(::containsImage))
            }
        }
    }

    private fun scrollMainToDescription(description: String): AccessibilityNodeInfo {
        repeat(40) {
            findNode { it.contentDescription?.toString() == description && it.isVisibleToUser }?.let { return it }
            assertTrue(requireNotNull(findNode { it.isScrollable })
                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not scroll to $description")
    }

    private fun setSearchText(query: String) {
        val editor = requireNotNull(findNode { it.isEditable })
        assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }))
        instrumentation.waitForIdleSync()
        await { findNode { it.isEditable && it.text?.toString() == query } != null }
    }

    private fun scrollMainToText(text: String): AccessibilityNodeInfo {
        repeat(40) {
            findNode { it.text?.toString() == text && it.isVisibleToUser }?.let { return it }
            assertTrue(requireNotNull(findNode { it.isScrollable })
                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not scroll to $text")
    }

    private fun scrollBackToSearch() {
        repeat(40) {
            if (findNode { it.isEditable && it.isVisibleToUser } != null) return
            assertTrue(requireNotNull(findNode { it.isScrollable })
                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not scroll back to search")
    }

    @Test
    fun variantCountsOpenSheetsWithoutMovingTheMainList() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD)

            multiVariantFamilies().forEach { family ->
                val mainCardTopBefore = openFamilySheet(family)
                await {
                    findNode {
                        it.contentDescription?.toString() == "${family.title} 변형 목록"
                    } != null
                }
                await { findNode { it.text?.toString() == "${family.title} 변형 선택" } != null }
                scenario.onActivity { assertEquals(family.familyId, fixture.state.value.selectedFamilyId) }

                val selectedIndex = family.presetCount
                val selectedPresetId = "${family.familyId}_$selectedIndex"
                val selectedPresetName = presetName(family, selectedIndex)
                val presetButton = scrollSheetToPreset("$selectedPresetName 선택", family)
                click(presetButton)
                await {
                    findNode {
                        it.contentDescription?.toString() == "$selectedPresetName 선택됨"
                    } != null
                }
                scenario.onActivity {
                    assertEquals(selectedPresetId, fixture.state.value.selectedPresetId)
                }

                click(requireNotNull(findNode { it.text?.toString() == "이 변형으로 선택" }))
                await { fixture.actions.chosenPresetId == selectedPresetId }
                dismissSheet()

                val mainCardTopAfter = mainFamilyButtonTop(family)
                assertCameraStayed(mainCardTopBefore, mainCardTopAfter)
                scenario.onActivity {
                    assertEquals(null, fixture.state.value.selectedFamilyId)
                    assertEquals(selectedPresetId, fixture.actions.chosenPresetId)
                }
            }
        }
    }

    @Test
    fun restoredFamilyAndPresetReopenTheSheetWithTheSelection() {
        val family = multiVariantFamilies().last()
        val selectedPresetId = "${family.familyId}_${family.presetCount}"
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(
                scenario,
                ExercisePickerSelectionMode.WORKOUT_ADD,
                selectedFamilyId = family.familyId,
                selectedPresetId = selectedPresetId
            )
            val selectedPresetName = presetName(family, family.presetCount)
            await {
                findNode {
                    it.contentDescription?.toString() == "${family.title} 변형 목록"
                } != null
            }
            val restoredPresetButton = scrollSheetToPreset("$selectedPresetName 선택됨", family)
            assertEquals("$selectedPresetName 선택됨", restoredPresetButton.contentDescription.toString())
            scenario.onActivity {
                assertEquals(selectedPresetId, fixture.state.value.selectedPresetId)
            }
            assertTrue(findNode { it.text?.toString() == "선택됨" } != null)
            click(requireNotNull(findNode { it.text?.toString() == "이 변형으로 선택" }))
            await { fixture.actions.chosenPresetId == selectedPresetId }
        }
    }

    @Test
    fun singlePresetFamilyStillChoosesDirectlyWithoutOpeningASheet() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val fixture = showPicker(scenario, ExercisePickerSelectionMode.WORKOUT_ADD)
            val presetName = "단일 운동 변형 01"
            val presetButton = findMainButton("$presetName 선택")
            click(presetButton)
            await { fixture.actions.chosenPresetId == "d_single_1" }
            assertEquals(null, findNode { it.contentDescription?.toString()?.endsWith("변형 목록") == true })
        }
    }

    private fun assertCameraStayed(beforeTop: Int, afterTop: Int) {
        assertTrue("Tapped family moved on screen: $beforeTop -> $afterTop", abs(afterTop - beforeTop) <= 24)
    }

    private fun showPicker(
        scenario: ActivityScenario<ComponentActivity>,
        mode: ExercisePickerSelectionMode,
        selectedFamilyId: String? = null,
        selectedPresetId: String? = null,
        families: List<RuntimeExercisePicker.FamilyResult> = fixtureFamilies(),
        wrappedContext: Boolean = false
    ): PickerFixture {
        val screen = if (mode == ExercisePickerSelectionMode.ROUTINE_ADD) {
            FitnessScreen.ROUTINE_ADD
        } else FitnessScreen.WORKOUT_EXERCISE_ADD
        val state = mutableStateOf(ExercisePickerUiState.Ready(
            ownerId = "fixture-owner", mode = screen, selectionMode = mode,
            recordId = "fixture-record", replacementId = null, routineId = "fixture-routine",
            query = "", bodyPart = null, primarySubPart = null, equipmentCategory = null,
            sortOrder = RuntimeExercisePicker.SortOrder.RECENT,
            families = families, availablePrimarySubParts = emptyList(),
            selectedFamilyId = selectedFamilyId, selectedPresetId = selectedPresetId
        ))
        val actions = FixtureActions(state)
        scenario.onActivity { activity ->
            activity.setContent {
                FitnessComposeTheme(dark = false) {
                    val context = if (wrappedContext) android.view.ContextThemeWrapper(activity,
                        android.R.style.Theme_Material_Light_NoActionBar) else activity
                    CompositionLocalProvider(LocalContext provides context) {
                        ExercisePickerScreen(state.value, "fixture-owner", screen, actions)
                    }
                }
            }
        }
        if (selectedFamilyId == null) {
            await { findNode { it.isEditable } != null }
        } else {
            val selectedTitle = fixtureFamilies().firstOrNull {
                it.family.familyId == selectedFamilyId
            }?.family?.displayName().orEmpty()
            await {
                findNode {
                    it.contentDescription?.toString() == "$selectedTitle 변형 목록"
                } != null
            }
        }
        return PickerFixture(state, actions)
    }

    private fun openFamilySheet(family: FixtureFamily): Int {
        val description = "${family.title} 변형 선택"
        repeat(40) {
            val button = findNode {
                it.contentDescription?.toString() == description && it.isVisibleToUser
            }
            if (button != null) {
                val topBefore = bounds(button).top
                click(button)
                await {
                    findNode {
                        it.contentDescription?.toString() == "${family.title} 변형 목록"
                    } != null
                }
                return topBefore
            }
            val list = requireNotNull(findNode { it.isScrollable })
            if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                throw AssertionError("Could not scroll to $description: ${list.actionList}, ${bounds(list)}")
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not find $description")
    }

    private fun scrollSheetToPreset(
        buttonDescription: String,
        family: FixtureFamily
    ): AccessibilityNodeInfo {
        repeat(20) {
            findNode { it.contentDescription?.toString() == buttonDescription && it.isVisibleToUser }
                ?.let { return it }
            val list = requireNotNull(findNode {
                it.isScrollable && it.contentDescription?.toString() == "${family.title} 변형 목록"
            })
            if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                throw AssertionError("Could not scroll the ${family.presetCount}-variant sheet to $buttonDescription")
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not find $buttonDescription in the variant sheet")
    }

    private fun dismissSheet() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await { findNode { it.contentDescription?.toString()?.endsWith("변형 목록") == true } == null }
    }

    private fun mainFamilyButtonTop(family: FixtureFamily): Int {
        val description = "${family.title} 변형 선택"
        await {
            findNode { it.contentDescription?.toString() == description && it.isVisibleToUser } != null &&
                findNode { it.contentDescription?.toString()?.endsWith("변형 목록") == true } == null
        }
        return bounds(requireNotNull(findNode {
            it.contentDescription?.toString() == description && it.isVisibleToUser
        })).top
    }

    private fun findMainButton(description: String): AccessibilityNodeInfo {
        for (attempt in 0 until 40) {
            findNode { it.contentDescription?.toString() == description && it.isVisibleToUser }
                ?.let { return it }
            val list = requireNotNull(findNode { it.isScrollable })
            if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        return requireNotNull(findNode { it.contentDescription?.toString() == description && it.isVisibleToUser })
    }

    private fun click(node: AccessibilityNodeInfo) {
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertTrue("Node is not clickable: ${node.contentDescription}",
            requireNotNull(clickable).performAction(AccessibilityNodeInfo.ACTION_CLICK))
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
        throw AssertionError("Timed out waiting for the picker UI. Accessibility: ${accessibilitySnapshot()}")
    }

    private fun accessibilitySnapshot(): String {
        val entries = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (!node.refresh()) return
            val text = node.text?.toString()
            val description = node.contentDescription?.toString()
            if (node.isVisibleToUser && (text != null || description != null || node.isScrollable)) {
                entries += "${" ".repeat(depth)}text=$text desc=$description scroll=${node.isScrollable} bounds=${bounds(node)}"
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { visit(it, depth + 1) }
            }
        }
        automation.rootInActiveWindow?.let { visit(it, 0) }
        return entries.takeLast(80).joinToString(" | ")
    }

    private fun fixtureFamilies(): List<RuntimeExercisePicker.FamilyResult> {
        val families = JSONObject()
        val exercises = JSONArray()
        (multiVariantFamilies() + FixtureFamily("d_single", "단일", "단일 운동 그룹", 1)).forEach { family ->
            val (familyId, name) = family.familyId to family.name
            families.put(familyId, JSONObject()
                .put("nameKo", family.title).put("nameEn", familyId)
                .put("defaultUiPart", "chest")
                .put("allowedLoadStates", JSONArray().put("external_load")))
            repeat(family.presetCount) { index ->
                val presetId = "${familyId}_${index + 1}"
                val title = presetName(family, index + 1)
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

    private fun multiVariantFamilies(): List<FixtureFamily> = listOf(
        FixtureFamily("a_two", "두", "두 운동 그룹", 2),
        FixtureFamily("b_five", "다섯", "다섯 운동 그룹", 5),
        FixtureFamily("c_ten", "열", "열 운동 그룹", 10)
    )

    private fun presetName(family: FixtureFamily, index: Int): String =
        "${family.name} 운동 변형 ${index.toString().padStart(2, '0')}"

    private data class FixtureFamily(
        val familyId: String,
        val name: String,
        val title: String,
        val presetCount: Int
    )

    private data class PickerFixture(
        val state: MutableState<ExercisePickerUiState.Ready>,
        val actions: FixtureActions
    )

    private class FixtureActions(private val state: MutableState<ExercisePickerUiState.Ready>) : ExercisePickerScreenActions {
        var chosenPresetId: String? = null
            private set
        var searchCount: Int = 0
            private set

        override fun back() = Unit
        override fun search(query: String) {
            searchCount++
            state.value = state.value.copy(query = query)
        }
        override fun setBodyPart(bodyPart: BodyPart?) = Unit
        override fun setPrimarySubPart(primarySubPart: String?) = Unit
        override fun selectMuscleGroup(groupId: String) = Unit
        override fun clearBodyPartSelection() {
            state.value = state.value.copy(
                bodyPart = null,
                primarySubPart = null,
                selectedFamilyId = null,
                selectedPresetId = null
            )
        }
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
        override fun choose(preset: RuntimeExercisePreset) {
            chosenPresetId = preset.presetId
        }
        override fun chooseManual(exercise: com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise) = Unit
    }
}
