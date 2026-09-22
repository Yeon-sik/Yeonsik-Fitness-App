package com.yeonsik.fitnessapp.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowInsetsController
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.MainActivity
import com.yeonsik.fitnessapp.app.navigation.AppNavigationViewModel
import com.yeonsik.fitnessapp.app.navigation.RecordsHubTab
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerUiState
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState
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
            val originalTheme = onActivity(scenario) { it.settingsViewModel().themeMode() }
            val screens = listOf(FitnessScreen.HOME, FitnessScreen.WORKOUT, FitnessScreen.STRENGTH,
                FitnessScreen.RECORDS, FitnessScreen.STATISTICS, FitnessScreen.DEVELOPMENT, FitnessScreen.SETTINGS,
                FitnessScreen.MEALS, FitnessScreen.SUPPLEMENTS, FitnessScreen.ROUTINE_DETAIL, FitnessScreen.CARDIO)
            try {
                for (mode in listOf("light", "dark")) {
                    for (screen in screens) {
                        scenario.onActivity { it.settingsViewModel().setThemeMode(mode); it.navigate(screen) }
                        await {
                            onActivity(scenario) { activity ->
                                val hub = RecordsHubTab.forScreen(screen)
                                activity.currentScreen() == (if (hub == null) screen else FitnessScreen.RECORDS) &&
                                    (hub == null || ViewModelProvider(activity)[AppNavigationViewModel::class.java].recordsHubTab() == hub)
                            }
                        }
                        instrumentation.waitForIdleSync()
                        // Navigation state can update before Compose publishes the next frame.
                        // Wait for the destination's selected tab before positioning its content.
                        assertNavigationTabs(screen, scenario)
                        assertSystemBarAppearance(scenario, mode)
                        // Each Compose destination retains its own scroll position. Position the
                        // capture explicitly without assuming an Android widget.ScrollView exists.
                        scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        await { hasText(destinationTitle(screen)) && !hasLoadingMessage() }
                        assertDestinationContent(screen)
                        assertNavigationTabs(screen, scenario)
                        capture("${screen.name.lowercase()}-$mode")
                        scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        assertFalse("$screen must render loaded content", hasLoadingMessage())
                        capture("${screen.name.lowercase()}-$mode-bottom")
                    }
                }
            } finally {
                scenario.onActivity { it.settingsViewModel().setThemeMode(originalTheme) }
            }
        }
    }

    @Test
    fun routineDraftSurvivesThemeChangeWithoutSaving() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val originalTheme = onActivity(scenario) { it.settingsViewModel().themeMode() }
            try {
                scenario.onActivity {
                    it.settingsViewModel().setThemeMode("light")
                    it.navigate(FitnessScreen.WORKOUT)
                }
                scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                tap("무산소 운동")
                await { onActivity(scenario) { it.currentScreen() == FitnessScreen.STRENGTH } && editors().isNotEmpty() }
                val routinesBefore = routineNames(scenario)
                val draft = "시각 QA 저장하지 않은 루틴"
                assertTrue(editors().first().performAction(AccessibilityNodeInfo.ACTION_CLICK))
                setText(editors().first(), draft)
                await { keyboardVisible(scenario) && editors().firstOrNull()?.text?.toString() == draft }
                capture("routine-draft-keyboard-light")
                scenario.onActivity { it.settingsViewModel().setThemeMode("dark") }
                instrumentation.waitForIdleSync()
                await { editors().firstOrNull()?.text?.toString() == draft }
                assertEquals(FitnessScreen.STRENGTH, onActivity(scenario) { it.currentScreen() })
                assertEquals(routinesBefore, routineNames(scenario))
                capture("routine-draft-keyboard-dark")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { !keyboardVisible(scenario) }
                assertEquals("Dismissing the keyboard keeps the draft screen open", FitnessScreen.STRENGTH,
                    onActivity(scenario) { it.currentScreen() })
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { onActivity(scenario) { it.currentScreen() == FitnessScreen.WORKOUT } }
                // Read through the normal ViewModel after backing out, without invoking
                // create, rename, workout or record save actions.
                scenario.onActivity { it.routineEntryViewModel().enter(AccountScope(it.currentOwnerId())) }
                await { onActivity(scenario) { it.routineEntryViewModel().uiState.value is RoutineEntryUiState.Ready } }
                assertEquals("Backing out must not save the draft", routinesBefore, routineNames(scenario))
                capture("routine-draft-cancelled")
            } finally {
                scenario.onActivity { it.settingsViewModel().setThemeMode(originalTheme) }
            }
        }
    }

    private fun routineNames(scenario: ActivityScenario<MainActivity>) = onActivity(scenario) { activity ->
        (activity.routineEntryViewModel().uiState.value as RoutineEntryUiState.Ready)
            .routines.associate { it.id to it.name }
    }

    private fun assertSystemBarAppearance(scenario: ActivityScenario<MainActivity>, mode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        val expected = if (mode == "light") mask else 0
        fun appearance() = onActivity(scenario) { activity ->
            activity.window.insetsController?.systemBarsAppearance?.and(mask)
        }
        val deadline = SystemClock.uptimeMillis() + 15_000
        var actual = appearance()
        while (actual != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            actual = appearance()
        }
        assertEquals("$mode theme must update status and navigation bar icon appearance", expected, actual)
    }

    private fun keyboardVisible(scenario: ActivityScenario<MainActivity>) = onActivity(scenario) { activity ->
        ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun destinationTitle(screen: FitnessScreen): String = when (screen) {
        FitnessScreen.HOME -> "메인"
        FitnessScreen.WORKOUT -> "운동"
        FitnessScreen.STRENGTH -> "무산소"
        FitnessScreen.RECORDS -> "기록"
        FitnessScreen.STATISTICS -> "통계"
        FitnessScreen.DEVELOPMENT -> "발전"
        FitnessScreen.SETTINGS -> "설정"
        FitnessScreen.MEALS -> "식사"
        FitnessScreen.SUPPLEMENTS -> "보충제"
        FitnessScreen.ROUTINE_DETAIL -> "루틴"
        FitnessScreen.CARDIO -> "유산소"
        else -> error("No visual QA destination for $screen")
    }

    private fun assertDestinationContent(screen: FitnessScreen) {
        val label = when (screen) {
            FitnessScreen.HOME -> "오늘 상태"
            FitnessScreen.WORKOUT -> "무산소 운동"
            FitnessScreen.STRENGTH -> "루틴 만들기"
            FitnessScreen.RECORDS -> "오늘로 이동"
            FitnessScreen.STATISTICS -> "데이터 충분성"
            FitnessScreen.DEVELOPMENT -> "신체 정보 수정"
            FitnessScreen.SETTINGS -> "계정 상태"
            FitnessScreen.MEALS -> "오늘로 이동"
            FitnessScreen.SUPPLEMENTS -> "오늘로 이동"
            FitnessScreen.ROUTINE_DETAIL -> "루틴 이름"
            FitnessScreen.CARDIO -> "걷기"
            else -> error("No visual QA content for $screen")
        }
        await {
            if (!hasText(label)) {
                // A destination can finish loading after the initial scroll reset.
                scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            hasText(label)
        }
        assertNotNull("$screen must expose its own application content", automation.rootInActiveWindow)
        assertEquals(instrumentation.targetContext.packageName,
            automation.rootInActiveWindow.packageName.toString())
    }

    private fun assertNavigationTabs(screen: FitnessScreen, scenario: ActivityScenario<MainActivity>) {
        if (screen !in listOf(FitnessScreen.HOME, FitnessScreen.WORKOUT, FitnessScreen.RECORDS,
                FitnessScreen.STATISTICS, FitnessScreen.DEVELOPMENT, FitnessScreen.SETTINGS)) return
        val selected = when (screen) {
            FitnessScreen.HOME -> "메인"
            FitnessScreen.WORKOUT -> "피트니스"
            FitnessScreen.SETTINGS -> "설정"
            else -> "기록"
        }
        val labels = listOf("메인", "피트니스", "기록", "설정")
        val deadline = SystemClock.uptimeMillis() + 15_000
        var snapshot = nodes()
        var tabs = labels.associateWith { navigationTab(it, snapshot) }
        while (labels.any { label -> tabs[label]?.isSelected != (label == selected) } &&
            SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            snapshot = nodes()
            tabs = labels.associateWith { navigationTab(it, snapshot) }
        }
        val tabStates = labels.joinToString { label ->
            "$label=${tabs[label]?.isSelected ?: "missing"}"
        }
        if (labels.any { label -> tabs[label]?.isSelected != (label == selected) }) {
            val route = onActivity(scenario) { activity ->
                val navigation = ViewModelProvider(activity)[AppNavigationViewModel::class.java]
                "screen=${activity.currentScreen()}, hub=${navigation.recordsHubTab()}, " +
                    "published=${navigation.uiState.value}"
            }
            val matches = snapshot.filter { node -> labels.any { node.matchesText(it) } }
                .joinToString("\n") { node ->
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    "text=${node.text}, description=${node.contentDescription}, " +
                        "clickable=${node.isClickable}, selected=${node.isSelected}, " +
                        "visible=${node.isVisibleToUser}, class=${node.className}, bounds=$bounds"
                }
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa").apply { mkdirs() }
            val name = "navigation-failure-${screen.name.lowercase()}"
            File(directory, "${InstrumentationRegistry.getArguments().getString("capturePrefix", "")}$name.txt")
                .writeText("expected=$selected; $tabStates\n$route\n$matches")
            capture(name)
        }
        for (label in labels) {
            val tab = tabs[label]
            val diagnostics = snapshot.filter { it.matchesText(label) }.takeLast(3).joinToString { node ->
                "text=${node.text}, description=${node.contentDescription}, " +
                    "clickable=${node.isClickable}, selected=${node.isSelected}, class=${node.className}"
            }
            assertNotNull("$screen retains the $label tab; matches: $diagnostics", tab)
            assertEquals("$screen selected state for $label; all tabs: $tabStates", label == selected, tab!!.isSelected)
        }
    }

    private fun navigationTab(label: String, snapshot: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? =
        snapshot.filter { it.matchesText(label) }.mapNotNull { match ->
            var node: AccessibilityNodeInfo? = match
            // A selected Compose tab may expose selection without a redundant click action.
            while (node != null) {
                // Compose can update selection before the accessibility cache catches up.
                if (!node.refresh()) return@mapNotNull null
                if (node.isClickable || node.isSelected) {
                    return@mapNotNull node.takeIf { it.isVisibleToUser }
                }
                node = node.parent
            }
            null
        }.lastOrNull() // The Records hub's same-named inner tab precedes the bottom bar.

    private fun hasText(text: String) = nodes().any { it.matchesText(text) }

    private fun AccessibilityNodeInfo.matchesText(value: String): Boolean =
        listOfNotNull(text?.toString(), contentDescription?.toString(), hintText?.toString())
            .any { it.lineSequence().any { line -> line.trim() == value } }

    private fun hasLoadingMessage() = nodes().any { node ->
        node.text?.toString()?.let { it.contains("불러오는 중") || it.contains("준비하는 중") } == true
    }

    private fun scrollToEdge(action: Int) {
        repeat(30) {
            val scrollable = nodes().firstOrNull { node ->
                node.isScrollable && node.actionList.any { it.id == action }
            } ?: return
            if (!scrollable.performAction(action)) return
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
        }
        assertFalse("Scrolling must reach the requested edge", nodes().any { node ->
            node.isScrollable && node.actionList.any { it.id == action }
        })
    }

    private fun editors() = nodes().filter { it.className?.toString() == "android.widget.EditText" }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.refresh()) return
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
