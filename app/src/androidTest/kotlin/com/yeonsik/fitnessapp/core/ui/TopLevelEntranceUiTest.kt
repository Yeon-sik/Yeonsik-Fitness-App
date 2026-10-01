package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TopLevelEntranceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun arrivalWaitsForActiveReadyContentAndRefreshDoesNotReplay() {
        compose.mainClock.autoAdvance = false
        val ready = mutableStateOf(false)
        val active = mutableStateOf(false)
        lateinit var entrance: TopLevelEntranceState
        compose.setContent {
            FitnessComposeTheme(false) {
                entrance = rememberTopLevelEntranceState("HOME")
                val motion = rememberTopLevelEntranceMotion(entrance, 1L, active.value, ready.value)
                Column {
                    if (ready.value) TopLevelEntranceContent(motion, 0) {
                        Text("content", Modifier.testTag("entrance-content"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(0L, entrance.lastConsumedToken)
            ready.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0L, entrance.lastConsumedToken) }
        val previewTop = top()
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeByFrame()
        val enteringTop = top()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1L, entrance.lastConsumedToken) }
        assertTrue(enteringTop > previewTop + 1f)
        assertEquals(previewTop, top(), 1f)
        compose.runOnIdle { ready.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { ready.value = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(previewTop, top(), 1f)
        compose.runOnIdle { assertEquals(1f, entrance.clock.value, 0f) }
    }

    @Test
    fun freshTokenReplaysAndInterruptingWithSwipeKeepsContentSettled() {
        compose.mainClock.autoAdvance = false
        val token = mutableStateOf<Long?>(1L)
        val active = mutableStateOf(true)
        lateinit var entrance: TopLevelEntranceState
        compose.setContent {
            FitnessComposeTheme(false) {
                entrance = rememberTopLevelEntranceState("WORKOUT")
                val motion = rememberTopLevelEntranceMotion(entrance, token.value, active.value, true)
                TopLevelEntranceContent(motion, 0) {
                    Text("content", Modifier.testTag("entrance-content"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val settledTop = top()
        compose.runOnIdle { token.value = 2L }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(top() > settledTop + 1f)
        compose.runOnIdle {
            active.value = false
            token.value = null
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(settledTop, top(), 1f)
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(settledTop, top(), 1f)
        compose.runOnIdle { assertEquals(2L, entrance.lastConsumedToken) }
    }

    @Test
    fun savedConsumptionRestoresFullyVisibleEvenDuringAnInterruptedEntrance() {
        compose.mainClock.autoAdvance = false
        val registry = mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
        val emitContent = mutableStateOf(true)
        lateinit var entrance: TopLevelEntranceState
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry.value) {
                if (emitContent.value) FitnessComposeTheme(false) {
                    entrance = rememberTopLevelEntranceState("RECORDS")
                    val motion = rememberTopLevelEntranceMotion(entrance, 1L, true, true)
                    TopLevelEntranceContent(motion, 0) {
                        Text("content", Modifier.testTag("entrance-content"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(1L, entrance.lastConsumedToken) }
        val interrupted = entrance
        lateinit var saved: Map<String, List<Any?>>
        compose.runOnIdle {
            saved = registry.value.performSave()
            emitContent.value = false
        }
        // With a manual clock, explicitly dispose on one frame before emitting restored content.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("entrance-content").assertDoesNotExist()
        compose.runOnIdle {
            registry.value = SaveableStateRegistry(saved) { true }
            emitContent.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        val restoredTop = top()
        compose.runOnIdle {
            assertNotSame(interrupted, entrance)
            assertEquals(1L, entrance.lastConsumedToken)
            assertEquals(1f, entrance.clock.value, 0f)
        }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(restoredTop, top(), 0.1f)
    }

    private fun top(): Float = compose.onNodeWithTag("entrance-content")
        .fetchSemanticsNode().boundsInRoot.top
}
