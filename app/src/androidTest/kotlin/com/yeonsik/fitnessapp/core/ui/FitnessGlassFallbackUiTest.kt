package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.app.navigation.RestTimerCapsule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * API 30, high-contrast text and missing backdrops all render the opaque fallback; behavior holds.
 * Dialog and sheet glass also runs with glass enabled: emulators often report cross-window blur
 * off, which must leave the dialog usable on the opaque surface.
 */
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
        // The capsule's horizontal padding is rim: a tap there must not reach the page. The
        // clickable page merges the capsule's semantics, so find it in the unmerged tree.
        compose.onNodeWithTag("rest-timer-capsule", useUnmergedTree = true).performTouchInput {
            click(Offset(width - 4f, height / 2f))
        }
        compose.runOnIdle {
            assertEquals(1, skips)
            assertEquals(0, contentTaps)
        }
    }

    @Test
    fun alertDialogConfirmsWithAndWithoutGlass() {
        var glass by mutableStateOf(false)
        var open by mutableStateOf(false)
        var confirms by mutableIntStateOf(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                CompositionLocalProvider(LocalFitnessGlassEnabled provides glass) {
                    if (open) {
                        FitnessAlertDialog(
                            onDismissRequest = { open = false },
                            confirmButton = { TextButton(onClick = { confirms++; open = false }) { Text("저장") } },
                            dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } },
                            title = { Text("기록 저장") },
                            text = { Text("변경 내용을 저장할까요?") }
                        )
                    }
                }
            }
        }
        listOf(false, true).forEachIndexed { index, enabled ->
            compose.runOnIdle {
                glass = enabled
                open = true
            }
            compose.onNode(isDialog()).assertIsDisplayed()
            compose.onNodeWithText("변경 내용을 저장할까요?").assertIsDisplayed()
            compose.onNodeWithText("저장").performClick()
            compose.runOnIdle {
                assertEquals("glass=$enabled", index + 1, confirms)
                assertFalse("glass=$enabled", open)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun bottomSheetShowsContentAndHandleWithGlass() {
        var closes by mutableIntStateOf(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                CompositionLocalProvider(LocalFitnessGlassEnabled provides true) {
                    FitnessModalBottomSheet(
                        onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        dragHandle = { Box(Modifier.padding(12.dp).testTag("glass-sheet-handle")) }
                    ) {
                        Text("선택일 기록")
                        TextButton(onClick = { closes++ }) { Text("닫기") }
                    }
                }
            }
        }
        compose.onNodeWithText("선택일 기록").assertIsDisplayed()
        compose.onNodeWithTag("glass-sheet-handle", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }
}
