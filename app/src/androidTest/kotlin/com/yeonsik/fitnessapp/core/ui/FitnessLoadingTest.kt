package com.yeonsik.fitnessapp.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

@Composable
internal fun ImmediateLoadingTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitnessLoadingTiming provides FitnessLoadingTiming(0, 0)) {
        FitnessComposeTheme(false, content = content)
    }
}

class FitnessLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun delaysLoadingAndKeepsReadyContentHiddenUntilMinimumVisibilityEnds() {
        var loading by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                val visible = rememberDelayedLoadingVisible(loading, "owner/date")
                if (loading || visible) {
                    FitnessLoadingState(listOf("기록을 불러오는 중"), FitnessLoadingVariant.SECTION, visible = visible)
                } else Text("준비 완료")
            }
        }
        compose.mainClock.advanceTimeBy(299, ignoreFrameDuration = true)
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
        compose.onNodeWithText("준비 완료").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(33)
        compose.onNodeWithTag("thinking-orb").assertExists()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { loading = false }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("thinking-orb").assertExists()
        compose.onNodeWithText("준비 완료").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
        compose.onNodeWithText("준비 완료").assertExists()
    }

    @Test fun fastLoadNeverFlashesAnOrb() {
        var loading by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                val visible = rememberDelayedLoadingVisible(loading)
                if (loading || visible) FitnessLoadingState(listOf("기록을 불러오는 중"),
                    FitnessLoadingVariant.SECTION, visible = visible)
                else Text("준비 완료")
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { loading = false }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
        compose.onNodeWithText("준비 완료").assertExists()
    }

    @Test fun changingOwnerOrDateDiscardsThePreviousMinimumVisibility() {
        var identity by mutableStateOf("owner-a/date-a")
        var loading by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                val visible = rememberDelayedLoadingVisible(loading, identity)
                if (loading || visible) FitnessLoadingState(listOf("기록을 불러오는 중"),
                    FitnessLoadingVariant.SECTION, visible = visible)
                else Text(identity)
            }
        }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("기록을 불러오는 중").assertExists()
        compose.runOnIdle { identity = "owner-b/date-b"; loading = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
        compose.onNodeWithText("owner-b/date-b").assertExists()
    }
}
