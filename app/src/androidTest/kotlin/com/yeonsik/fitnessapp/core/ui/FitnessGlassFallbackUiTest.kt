package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.app.navigation.RestTimerCapsule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** API 30, high-contrast text and missing backdrops all render the opaque fallback; behavior holds. */
class FitnessGlassFallbackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabBarSelectsTabsOnTheOpaqueFallback() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                CompositionLocalProvider(LocalFitnessGlassEnabled provides false) {
                    FitnessGlassTabBar(
                        tabs = listOf(FitnessGlassTab("메인", null), FitnessGlassTab("기록", null)),
                        selectedIndex = selected,
                        onTabSelected = { selected = it }
                    )
                }
            }
        }
        compose.onNodeWithText("기록").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("기록").assertIsSelected()
        compose.onNodeWithText("메인").assertIsNotSelected()
    }

    @Test
    fun restTimerSkipsAndKeepsRimTapsAwayFromContentBelow() {
        var skips by mutableIntStateOf(0)
        var contentTaps by mutableIntStateOf(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                CompositionLocalProvider(LocalFitnessGlassEnabled provides false) {
                    Box(Modifier.fillMaxSize().clickable { contentTaps++ }) {
                        RestTimerCapsule(
                            timeLabel = "1:30",
                            progress = 0.5f,
                            expanded = false,
                            onToggleExpanded = {},
                            onSkip = { skips++ },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("건너뛰기").performClick()
        // The capsule's horizontal padding is rim: a tap there must not reach the page.
        compose.onNodeWithTag("rest-timer-capsule").performTouchInput {
            click(Offset(width - 4f, height / 2f))
        }
        compose.runOnIdle {
            assertEquals(1, skips)
            assertEquals(0, contentTaps)
        }
    }
}
