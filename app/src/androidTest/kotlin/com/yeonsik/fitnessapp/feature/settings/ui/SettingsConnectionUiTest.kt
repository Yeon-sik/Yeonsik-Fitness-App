package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class SettingsConnectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions = Proxy.newProxyInstance(SettingsScreenActions::class.java.classLoader,
        arrayOf(SettingsScreenActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "SettingsConnectionTestActions"
            else -> {
                calls.add(method.name to (args?.toList() ?: emptyList()))
                null
            }
        }
    } as SettingsScreenActions

    @Test fun qaUsersCanEnterAndSaveAllThreeConnectionsWithoutDeveloperAccess() {
        show(state())
        listOf(SettingsConnection.SHARED, SettingsConnection.NUTRITION, SettingsConnection.PRICE_TRACE).forEach { scope ->
            compose.onNodeWithTag("settings-url-${scope.name}").performScrollTo().performTextInput("https://${scope.name.lowercase()}.example.com")
            compose.onNodeWithTag("settings-key-${scope.name}").performScrollTo().performTextInput("sb_publishable_${scope.name}")
            compose.onNodeWithTag("settings-save-${scope.name}").performScrollTo().performClick()
            assertEquals("saveConnection", calls.last().first)
            assertEquals(listOf(scope, "https://${scope.name.lowercase()}.example.com", "sb_publishable_${scope.name}"), calls.last().second)
        }
    }

    @Test fun loginSendsTheEnteredCredentialsAndDisablesDuplicateAccountActions() {
        val connected = SupabaseConfig("https://shared.example.com", "sb_publishable_test", "", "", "", "", SupabaseConfig.LOCAL_SETTINGS_SOURCE)
        val ui = mutableStateOf(state(connected))
        compose.setContent { FitnessComposeTheme(false) { Column(Modifier.verticalScroll(rememberScrollState())) { SettingsScreen(ui.value, actions) } } }
        compose.onNodeWithTag("settings-email-SHARED").performScrollTo().performTextInput("person@example.com")
        compose.onNodeWithTag("settings-password-SHARED").performScrollTo().performTextInput("password123")
        compose.onNodeWithTag("settings-login-SHARED").performScrollTo().performClick()
        assertEquals("signIn", calls.last().first)
        assertEquals(listOf(SettingsConnection.SHARED, "person@example.com", "password123"), calls.last().second)
        compose.runOnIdle { ui.value = ui.value.copy(isAccountOperationInProgress = true, syncLabel = "authenticating") }
        compose.onNodeWithTag("settings-login-SHARED").assertIsNotEnabled()
        compose.onNodeWithTag("settings-email-SHARED").assertIsNotEnabled()
        compose.onNodeWithTag("settings-password-SHARED").assertIsNotEnabled()
        compose.runOnIdle { ui.value = ui.value.copy(isAccountOperationInProgress = false, syncLabel = "authentication failed",
            authenticationErrorDetail = "공통 DB · 이메일 인증이 필요합니다.") }
        compose.onNodeWithTag("settings-login-SHARED").assertIsEnabled()
        compose.onAllNodesWithText("공통 DB · 이메일 인증이 필요합니다.").assertCountEquals(2)
    }

    private fun show(state: SettingsUiState) {
        compose.setContent { FitnessComposeTheme(false) { Column(Modifier.verticalScroll(rememberScrollState())) { SettingsScreen(state, actions) } } }
    }

    private fun state(shared: SupabaseConfig = SupabaseConfig.empty()) = SettingsUiState(
        "dark", MassUnit.KG, "login required", "", false, false, "", false, "",
        shared, false, SupabaseConfig.empty(), false, SupabaseConfig.empty(), false, false
    )
}
