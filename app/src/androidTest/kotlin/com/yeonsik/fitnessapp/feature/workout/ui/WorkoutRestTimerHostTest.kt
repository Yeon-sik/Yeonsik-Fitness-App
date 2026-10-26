package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutRestTimerHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expiryRunsWhileHomeContentIsDisplayedWithoutATimerBar() {
        var now = 1_000L
        var completions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Text("메인")
            WorkoutRestTimerHost(WorkoutRestTimerState.Active("owner", 2_000L, 1), "owner",
                onElapsed = { completions++ }, nowMillis = { now })
        }
        compose.runOnIdle { now = 2_000L }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, completions) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun anEarlierOwnersTimerCannotNotifyTheNewOwner() {
        var completions = 0
        compose.setContent {
            WorkoutRestTimerHost(WorkoutRestTimerState.Active("old", 1L, 1), "new",
                onElapsed = { completions++ }, nowMillis = { 2L })
        }
        compose.runOnIdle { assertEquals(0, completions) }
    }
}
