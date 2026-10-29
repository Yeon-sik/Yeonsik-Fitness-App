package com.yeonsik.fitnessapp.feature.workout.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.routine.model.RoutineSummary
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/** UI fixtures only; no database, account or real workout writes. */
class RecordingEntryUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = mutableStateOf("owner")
    private val scale = mutableStateOf(1f)
    private val dark = mutableStateOf(true)
    private val home = mutableStateOf<HomeUiState>(HomeUiState.Ready(snapshot()))
    private val routine = mutableStateOf<RoutineEntryUiState>(RoutineEntryUiState.Ready("owner", ""))
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions = Proxy.newProxyInstance(StrengthActions::class.java.classLoader,
        arrayOf(StrengthActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "StrengthTestActions"
            else -> { calls += method.name to args.orEmpty().toList(); null }
        }
    } as StrengthActions

    @Test fun routinesComeFirstAndAllEntryActionsKeepTheirCallbacks() {
        show()
        val card = compose.onNodeWithTag("strength-routine-one")
        card.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertTrue(card.getUnclippedBoundsInRoot().top < compose.onNodeWithTag("strength-start-empty").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("strength-routine-name").assertDoesNotExist()
        card.performClick()
        compose.onNodeWithTag("strength-start-empty").performScrollTo().performClick()
        compose.onNodeWithTag("strength-create-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("strength-routine-name").performScrollTo().performTextInput("새 운동")
        compose.onNodeWithText("루틴 만들기").performScrollTo().performClick()
        compose.onNodeWithTag("strength-past").performScrollTo().performClick()
        assertTrue(calls.contains("selectRoutine" to listOf("one")))
        assertTrue(calls.contains("createRoutine" to listOf("새 운동")))
        assertTrue(calls.any { it.first == "startEmptyWorkout" })
        assertTrue(calls.any { it.first == "showPastWorkout" })
    }

    @Test fun accountChangeHidesOldRoutinesAndOnlyMatchingErrorsCanRetry() {
        show()
        compose.runOnIdle { owner.value = "other" }
        compose.onNodeWithTag("strength-routine-one").assertDoesNotExist()
        compose.runOnIdle { routine.value = RoutineEntryUiState.Error("owner", "private diagnostic") }
        compose.onNodeWithTag("strength-retry").assertDoesNotExist()
        compose.runOnIdle { routine.value = RoutineEntryUiState.Error("other", "private diagnostic") }
        compose.onNodeWithText("private diagnostic").assertDoesNotExist()
        compose.onNodeWithTag("strength-retry").performClick()
        assertTrue(calls.any { it.first == "retryRoutines" })
    }

    @Test fun longRoutineNamesWrapAtTwoHundredPercentInBothThemes() {
        show()
        for (mode in listOf(false, true)) for (font in listOf(1f, 2f)) {
            compose.runOnIdle { dark.value = mode; scale.value = font }
            compose.onNodeWithTag("strength-routine-one").performScrollTo()
            assertAllTextFits()
            capture("strength-${if (mode) "dark" else "light"}-$font")
            compose.onNodeWithTag("strength-past").performScrollTo().assertIsDisplayed()
        }
    }

    private fun show() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
            FitnessComposeTheme(dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StrengthScreen(home.value, routine.value, owner.value, actions)
                    }
                }
            }
        }
    }

    private fun assertAllTextFits() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.forEach { text ->
                    assertFalse("Clipped: ${text.layoutInput.text}", text.multiParagraph.height > text.size.height + 1f ||
                        (0 until text.lineCount).any { text.isLineEllipsized(it) || text.getLineLeft(it) < -1f || text.getLineRight(it) > text.size.width + 1f })
                }
            }
        }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("flowVisualQa") != "true") return
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "workout-flows-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun snapshot() = HomeSnapshot("owner", "2026-10-21", emptyList(), null,
        listOf(RoutineSummary("one", "가슴과 어깨를 함께 운동하는 긴 이름의 루틴", 3)),
        emptyMap(), emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())
}
