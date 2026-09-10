package com.yeonsik.fitnessapp.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.MainActivity
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerUiState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailUiState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

/** Runs only when explicitly selected on a disposable emulator; uses the real UI and storage flow. */
@RunWith(AndroidJUnit4::class)
class FitnessVisualQaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Before
    fun requireDisposableEmulatorOptIn() {
        assumeTrue("Pass -e fitnessVisualQa true only on a disposable emulator",
            InstrumentationRegistry.getArguments().getString("fitnessVisualQa") == "true")
        assumeTrue("This test must not create records on a personal phone",
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
    }

    @Test
    fun workoutInputThemeRecreationAndCompletion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.settingsViewModel().setThemeMode("light")
                it.workoutSessionViewModel().startEmpty(
                    AccountScope(it.currentOwnerId()), it.today()
                )
            }
            await { onActivity(scenario) { it.workoutSessionViewModel().uiState.value is WorkoutSessionUiState.Ready } }
            capture("session-empty-light")
            scenario.onActivity {
                val recordId = it.workoutSessionViewModel().activeRecordId()
                assertNotNull(recordId)
                it.exercisePickerViewModel().clearReplacementExercise()
                it.workoutSessionViewModel().rememberActiveRecord(recordId!!)
                it.navigate(FitnessScreen.WORKOUT_EXERCISE_ADD)
            }
            await { onActivity(scenario) { it.exercisePickerViewModel().uiState.value is ExercisePickerUiState.Ready } }
            capture("exercise-picker-light")
            scenario.onActivity {
                val ready = it.exercisePickerViewModel().uiState.value as ExercisePickerUiState.Ready
                it.exercisePickerViewModel().choose(ready.presets.first { preset -> preset.displayName() == "JM 프레스" })
            }
            await { onActivity(scenario) {
                (it.workoutSessionViewModel().uiState.value as? WorkoutSessionUiState.Ready)?.session?.exercises?.isNotEmpty() == true
            } }
            capture("session-light")
            scenario.onActivity {
                val ready = it.workoutSessionViewModel().uiState.value as WorkoutSessionUiState.Ready
                it.workoutExerciseDetailViewModel().rememberActiveExercise(
                    ready.session.exercises.first().id
                )
                it.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
            }
            await { editors().size >= 2 }
            val weight = editors()[0]
            weight.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setText(weight, "20.5")
            setText(editors()[1], "8")
            capture("detail-keyboard-light")
            assertTrue(editors()[0].text.toString().contains("20.5"))
            // Recreation must keep the unsaved input draft.
            scenario.recreate()
            capture("detail-recreated")
            await { editors().size >= 2 }
            assertEquals("20.5", editors()[0].text.toString())
            assertEquals("8", editors()[1].text.toString())
            tap("완료")
            tap("저장")
            await { onActivity(scenario) {
                val state = it.workoutExerciseDetailViewModel().uiState.value as? WorkoutExerciseDetailUiState.Ready
                state?.detail?.sets?.any { set -> set.isCompleted && set.weightKg == 20.5 && set.actualReps == 8 } == true
            } }
            capture("detail-saved-light")
            scenario.onActivity {
                val recordId = (it.workoutExerciseDetailViewModel().uiState.value as WorkoutExerciseDetailUiState.Ready).detail.recordId
                assertTrue(it.back())
                // Existing recreation policy restores a HOME-rooted stack; resume through the public action.
                assertEquals(FitnessScreen.HOME, it.currentScreen())
                it.workoutSessionViewModel().rememberActiveRecord(recordId)
                it.workoutExerciseDetailViewModel().clearActiveExercise()
                it.navigate(FitnessScreen.WORKOUT_SESSION)
            }
            await { onActivity(scenario) {
                (it.workoutSessionViewModel().uiState.value as? WorkoutSessionUiState.Ready)?.session?.completedSetCount == 1
            } }
            scenario.onActivity {
                val ready = it.workoutSessionViewModel().uiState.value as WorkoutSessionUiState.Ready
                assertEquals(164.0, ready.session.totalVolumeKg, 0.001)
            }
            capture("session-completed-set-light")
            scenario.onActivity { it.settingsViewModel().setThemeMode("dark") }
            capture("session-dark")
            scenario.onActivity {
                it.workoutExerciseDetailViewModel().rememberActiveExercise(
                    (it.workoutSessionViewModel().uiState.value as WorkoutSessionUiState.Ready)
                        .session.exercises.first().id
                )
                it.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
            }
            await { editors().size >= 2 }
            editors()[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            capture("detail-keyboard-dark")
            scenario.onActivity {
                it.back()
                val recordId = it.workoutSessionViewModel().activeRecordId()
                assertNotNull(recordId)
                it.workoutSessionViewModel().finish(
                    AccountScope(it.currentOwnerId()), recordId!!
                )
            }
            await { onActivity(scenario) { it.currentScreen() == FitnessScreen.WORKOUT_SUMMARY && it.workoutSessionViewModel().uiState.value is WorkoutSessionUiState.Ready } }
            capture("summary-dark")
            // Remove only the synthetic session created by this test through its existing confirmation UI.
            scenario.onActivity {
                val ready = it.workoutSessionViewModel().uiState.value as WorkoutSessionUiState.Ready
                it.workoutSessionViewModel().openDeleteConfirmation(
                    AccountScope(it.currentOwnerId()), ready.session.recordId
                )
            }
            tap("삭제")
            scenario.onActivity { it.settingsViewModel().setThemeMode("light") }
        }
    }

    @Test
    fun destinationsUseBothThemes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val screens = listOf(FitnessScreen.HOME, FitnessScreen.WORKOUT, FitnessScreen.STRENGTH,
                FitnessScreen.RECORDS, FitnessScreen.DEVELOPMENT, FitnessScreen.SETTINGS,
                FitnessScreen.MEALS, FitnessScreen.SUPPLEMENTS, FitnessScreen.ROUTINE_DETAIL, FitnessScreen.CARDIO)
            for (mode in listOf("light", "dark")) {
                for (screen in screens) {
                    scenario.onActivity { it.settingsViewModel().setThemeMode(mode); it.navigate(screen) }
                    capture("${screen.name.lowercase()}-$mode")
                    assertNotNull(automation.rootInActiveWindow)
                    // A new destination must not inherit the previous screen's scroll offset.
                    scenario.onActivity { activity ->
                        fun scroll(view: android.view.View): android.widget.ScrollView? {
                            if (view is android.widget.ScrollView) return view
                            if (view is android.view.ViewGroup) {
                                for (i in 0 until view.childCount) scroll(view.getChildAt(i))?.let { return it }
                            }
                            return null
                        }
                        assertEquals("$screen must start at the top", 0,
                            scroll(activity.findViewById(android.R.id.content))!!.scrollY)
                    }
                    repeat(8) {
                        nodes().firstOrNull { it.className?.toString() == "android.widget.ScrollView" }
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        instrumentation.waitForIdleSync()
                    }
                    capture("${screen.name.lowercase()}-$mode-bottom")
                }
            }
            scenario.onActivity { it.settingsViewModel().setThemeMode("light") }
        }
    }

    private fun editors() = nodes().filter { it.className?.toString() == "android.widget.EditText" }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result += node
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(automation.rootInActiveWindow)
        return result
    }
    private fun setText(node: AccessibilityNodeInfo, value: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        instrumentation.waitForIdleSync()
    }
    private fun tap(text: String) {
        await { nodes().any { it.text?.toString() == text || it.contentDescription?.toString() == text } }
        var node: AccessibilityNodeInfo? = nodes().first { it.text?.toString() == text || it.contentDescription?.toString() == text }
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("Clickable $text", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa").apply { mkdirs() }
        val bitmap = automation.takeScreenshot()
        assertNotNull(bitmap)
        File(directory, "${InstrumentationRegistry.getArguments().getString("capturePrefix", "")}$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun <T> onActivity(scenario: ActivityScenario<MainActivity>, block: (MainActivity) -> T): T {
        var result: T? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue("UI did not reach the expected state", condition())
    }
}
