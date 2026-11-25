package com.yeonsik.fitnessapp.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BottomNavigationDragUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dragCommitsOnlyOnReleaseAndCancellationPreservesTheTab() {
        val screen = mutableStateOf(FitnessScreen.HOME)
        val visited = mutableListOf<FitnessScreen>()
        compose.setContent {
            MaterialTheme {
                BottomNavigation(
                    HomeUiState.Idle, screen.value,
                    onTabSelected = { screen.value = it; visited.add(it) },
                    dark = false, itemHeight = 56.dp, backdrop = rememberLayerBackdrop(),
                    useBackdropGlass = false, highContrastEnabled = false
                )
            }
        }
        val bar = compose.onNodeWithTag("bottom-navigation")
        val home = compose.onNodeWithText("메인")
        val records = compose.onNodeWithText("기록")
        val settings = compose.onNodeWithText("설정")

        bar.performTouchInput {
            down(Offset(width / 8f, center.y))
            moveTo(Offset(width * 7 / 8f, center.y))
        }
        home.assertIsSelected()
        settings.assertIsNotSelected()
        compose.runOnIdle { assertEquals(emptyList<FitnessScreen>(), visited) }
        bar.performTouchInput { up() }
        settings.assertIsSelected()

        bar.performTouchInput {
            down(Offset(width * 7 / 8f, center.y))
            moveTo(Offset(width / 8f, center.y))
            cancel()
        }
        settings.assertIsSelected()
        bar.performTouchInput {
            down(Offset(width * 7 / 8f, center.y))
            moveTo(Offset(width / 8f, center.y))
            moveTo(Offset(width / 8f, -20f))
            up()
        }
        settings.assertIsSelected()
        records.performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(FitnessScreen.SETTINGS, FitnessScreen.RECORDS), visited) }
    }
}
