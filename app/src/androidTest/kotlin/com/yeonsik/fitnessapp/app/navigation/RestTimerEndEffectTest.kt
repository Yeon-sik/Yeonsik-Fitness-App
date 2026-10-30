package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRestTimerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression: the end check lived inside the rest timer capsule, which only shows on session
 * screens, so a rest ending on the home tab stayed silent until the user went back.
 */
class RestTimerEndEffectTest {
    @get:Rule val compose = createComposeRule()

    private val owner = "rest-owner"

    @Test
    fun restEndsOnTimeWithNoCapsuleOnScreen() {
        compose.mainClock.autoAdvance = false
        var ended by mutableIntStateOf(0)
        val endsAt = compose.mainClock.currentTime + 1_000L
        compose.setContent {
            // Only the app-level effect, as on the home tab where the capsule is hidden.
            RestTimerEndEffect(
                WorkoutRestTimerState.Active(owner, endsAt, 1),
                owner,
                onRestEnded = { ended++ },
                clock = { compose.mainClock.currentTime }
            )
        }
        compose.mainClock.advanceTimeBy(800L)
        compose.runOnIdle { assertEquals(0, ended) }
        compose.mainClock.advanceTimeBy(400L)
        compose.runOnIdle { assertEquals(1, ended) }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.runOnIdle { assertEquals(1, ended) }
    }

    @Test
    fun anotherOwnersRestNeverEnds() {
        compose.mainClock.autoAdvance = false
        var ended by mutableIntStateOf(0)
        val endsAt = compose.mainClock.currentTime + 500L
        compose.setContent {
            RestTimerEndEffect(
                WorkoutRestTimerState.Active("previous-owner", endsAt, 1),
                owner,
                onRestEnded = { ended++ },
                clock = { compose.mainClock.currentTime }
            )
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.runOnIdle { assertEquals(0, ended) }
    }

    @Test
    fun skippedRestDoesNotEnd() {
        compose.mainClock.autoAdvance = false
        var ended by mutableIntStateOf(0)
        val endsAt = compose.mainClock.currentTime + 1_000L
        var state by mutableStateOf<WorkoutRestTimerState>(WorkoutRestTimerState.Active(owner, endsAt, 1))
        compose.setContent {
            RestTimerEndEffect(state, owner, onRestEnded = { ended++ }, clock = { compose.mainClock.currentTime })
        }
        compose.mainClock.advanceTimeBy(500L)
        compose.runOnIdle { state = WorkoutRestTimerState.Inactive }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.runOnIdle { assertEquals(0, ended) }
    }
}
