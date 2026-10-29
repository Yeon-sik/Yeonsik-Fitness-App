package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Pure screen fixtures: no repository, login, sync or file operation is executed. */
class SettingsUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()
    private val actions = Proxy.newProxyInstance(
        SettingsScreenActions::class.java.classLoader, arrayOf(SettingsScreenActions::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "SettingsUsabilityTestActions"
            else -> {
                calls.add(method.name)
                null
            }
        }
    } as SettingsScreenActions

    @Test fun allSixDataActionsRemainReachableAfterGrouping() {
        show()
        val groups = listOf(
            "백업 및 복원" to listOf("로컬 백업" to "createLocalBackup", "백업 복원" to "restoreLocalBackup"),
            "다른 앱 기록 가져오기" to listOf("FLEEK 가져오기" to "openFleekDataImport", "운동 전송 가져오기" to "openWorkoutTransferImport"),
            "파일 내보내기" to listOf("운동 전송 내보내기" to "exportWorkoutTransfer", "CSV 내보내기" to "exportRecordsCsv")
        )
        groups.forEach { (group, buttons) ->
            buttons.forEach { (label, _) -> compose.onNodeWithText(label).assertDoesNotExist() }
            compose.onNodeWithText(group).performScrollTo().performClick()
            buttons.forEach { (label, method) ->
                compose.onNodeWithText(label).performScrollTo().assertIsEnabled().performClick()
                assertEquals(method, calls.last())
            }
            compose.onNodeWithText(group).performScrollTo().performClick()
        }
        assertEquals(6, calls.size)
    }

    @Test fun largeFontSelectionsExposeRoleAndSelectedStateAndDispatchActions() {
        show(fontScale = 2f)
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNode(hasText("어둡게") and radio).performScrollTo().assertIsDisplayed().assertIsSelected()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNode(hasText("밝게") and radio).performScrollTo().assertIsNotSelected().performClick()
        assertEquals("setThemeMode", calls.last())
        compose.onNode(hasText("기기 설정") and radio).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(MassUnit.LB.labelKo()) and radio).performScrollTo().assertIsNotSelected().performClick()
        assertEquals("setPreferredMassUnit", calls.last())
    }

    @Test fun transferProgressRemainsVisibleAndItsActionsStayDisabled() {
        show(state().copy(isDataImporting = true, dataImportDetail = "읽는 중",
            isDataTransferInProgress = true, dataTransferDetail = "처리 중"))
        compose.onNodeWithText("다른 앱 기록 가져오기").performScrollTo().performClick()
        compose.onNodeWithText("FLEEK 가져오기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("운동 전송 가져오기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("파일 내보내기").performScrollTo().performClick()
        compose.onNodeWithText("운동 전송 내보내기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("가져오기 상태 · 읽는 중").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전송 상태 · 처리 중").performScrollTo().assertIsDisplayed()
        assertEquals(0, calls.size)
    }

    @Test fun expandedDataGroupSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { SettingsScreen(state(), actions) }
            }
        }
        compose.onNodeWithText("백업 및 복원").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("로컬 백업").performScrollTo().assertIsDisplayed()
    }

    @Test fun accountWorkDisablesSyncAndExplainsWhy() {
        val ui = mutableStateOf(state())
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { SettingsScreen(ui.value, actions) }
            }
        }
        compose.onNodeWithText("지금 동기화").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            ui.value = ui.value.copy(sharedConfig = SupabaseConfig(
                "https://example.com", "public-test-key", "test-user", "test@example.com",
                "test-access-token", "", SupabaseConfig.LOCAL_SETTINGS_SOURCE
            ))
        }
        compose.onNodeWithText("지금 동기화").performScrollTo().assertIsEnabled().performClick()
        assertEquals("runManualSync", calls.last())
        compose.runOnIdle { ui.value = ui.value.copy(isAccountOperationInProgress = true) }
        compose.onNodeWithText("지금 동기화").assertIsNotEnabled()
        compose.onNodeWithText("계정 처리가 끝나면 동기화할 수 있습니다.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            ui.value = ui.value.copy(isAccountOperationInProgress = false, isManualSyncing = true)
        }
        compose.onNode(hasText("동기화 중") and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    private fun show(state: SettingsUiState = state(), fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                FitnessComposeTheme(false) {
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        SettingsScreen(state, actions)
                    }
                }
            }
        }
    }

    private fun state() = SettingsUiState(
        "dark", MassUnit.KG, "local-only", "", false, false, "", false, "",
        SupabaseConfig.empty(), false, SupabaseConfig.empty(), false, SupabaseConfig.empty(), false, false
    )
}
