package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioEnvironment
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardioStartUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchAndEnvironmentSelectionAreRequiredBeforeStarting() {
        val state = mutableStateOf(CardioStartUiState(loading = false))
        var started: Pair<CardioActivityType, CardioEnvironment>? = null
        compose.setContent { FitnessComposeTheme(false) {
            CardioStartContent(state.value, { state.value = state.value.copy(query = it) },
                { state.value = state.value.select(it) }, { state.value = state.value.copy(environment = it) },
                { type, env -> started = type to env }, {})
        } }
        compose.onNodeWithTag("cardio_start").assertIsNotEnabled()
        compose.onNodeWithTag("cardio_search").performTextInput("러닝")
        compose.onNodeWithTag("cardio_activity_running").performClick()
        compose.onNodeWithTag("cardio_start").assertIsNotEnabled()
        compose.onNodeWithText("실내").performScrollTo().performClick()
        compose.onNodeWithTag("cardio_start").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(CardioActivityType.RUNNING to CardioEnvironment.INDOOR, started) }
    }

    @Test fun fixedEquipmentDisablesBothEnvironmentButtonsAndShowsItsAvailableInputs() {
        val state = CardioStartUiState(loading = false).select(CardioActivityType.ROWING)
        compose.setContent { FitnessComposeTheme(false) { CardioStartContent(state, {}, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("실내").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("실외").assertIsNotEnabled()
        compose.onNodeWithText("실내 기구 운동 · 환경 변경 불가").assertExists()
        compose.onNodeWithText("시간 · 기구 거리(선택) · 평균 심박수(선택)").assertExists()
        compose.onNodeWithTag("cardio_start").assertIsEnabled()
    }

    @Test fun backReturnsToTheWorkoutSelectorAndBusyStatePreventsDuplicateStarts() {
        var backs = 0
        var starts = 0
        val state = CardioStartUiState(loading = false).select(CardioActivityType.STAIR_STEPPER)
        compose.setContent { FitnessComposeTheme(false) {
            CardioStartContent(state, {}, {}, {}, { _, _ -> starts++ }, { backs++ }, starting = true)
        } }
        compose.onNodeWithTag("cardio_start").assertIsNotEnabled()
        compose.onNodeWithText("‹ 뒤로").performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, starts) }
    }

    @Test fun startButtonStaysVisibleAtLargeFontSizesAndNarrowWidths() {
        val state = CardioStartUiState(loading = false).select(CardioActivityType.ELLIPTICAL)
        compose.setContent { FitnessComposeTheme(false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CardioStartContent(state, {}, {}, {}, { _, _ -> }, {}, modifier = Modifier.width(280.dp))
            }
        } }
        compose.onNodeWithTag("cardio_start").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("실내").performScrollTo().assertIsNotEnabled()
    }
}
