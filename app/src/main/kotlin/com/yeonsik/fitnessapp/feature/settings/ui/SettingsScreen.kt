package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

interface SettingsScreenActions {
    fun setPreferredMassUnit(unit: MassUnit)
    fun setThemeMode(mode: String)
    fun runManualSync()
    fun openFleekDataImport()
    fun openWorkoutTransferImport()
    fun exportWorkoutTransfer()
    fun createLocalBackup()
    fun restoreLocalBackup()
    fun exportRecordsCsv()
    fun saveConnection(connection: SettingsConnection, url: String, anonKey: String)
    fun signIn(connection: SettingsConnection, email: String, password: String)
    fun signUp(connection: SettingsConnection, email: String, password: String)
    fun signOut(connection: SettingsConnection)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsScreen(state: SettingsUiState, actions: SettingsScreenActions) {
    var advancedConnectionsVisible by rememberSaveable { mutableStateOf(false) }
    AppHeader("설정")
    Text("표시 단위")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        MassUnit.values().forEach { unit ->
            AppOutlinedButton(onClick = { actions.setPreferredMassUnit(unit) }, selected = state.preferredMassUnit == unit) { Text(unit.labelKo()) }
        }
    }
    Text("테마")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        listOf("light", "dark", "system").forEach { mode ->
            AppOutlinedButton(onClick = { actions.setThemeMode(mode) }, selected = state.themeMode == mode) { Text(mode) }
        }
    }
    AppButton(onClick = actions::runManualSync, enabled = !state.isManualSyncing,
        modifier = Modifier.fillMaxWidth()) { Text(state.syncLabel) }
    Text(state.syncDetail)
    AppOutlinedButton(onClick = actions::openFleekDataImport, enabled = !state.isDataImporting,
        modifier = Modifier.fillMaxWidth()) { Text("FLEEK 가져오기") }
    AppOutlinedButton(onClick = actions::openWorkoutTransferImport, enabled = !state.isDataTransferInProgress,
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 가져오기") }
    AppOutlinedButton(onClick = actions::exportWorkoutTransfer, enabled = !state.isDataTransferInProgress,
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 내보내기") }
    AppOutlinedButton(onClick = actions::createLocalBackup, Modifier.fillMaxWidth()) { Text("로컬 백업") }
    AppOutlinedButton(onClick = actions::restoreLocalBackup, Modifier.fillMaxWidth()) { Text("백업 복원") }
    AppOutlinedButton(onClick = actions::exportRecordsCsv, Modifier.fillMaxWidth()) { Text("CSV 내보내기") }
    if (state.developerSurfaceAllowed) {
        AppOutlinedButton(
            onClick = { advancedConnectionsVisible = !advancedConnectionsVisible },
            Modifier.fillMaxWidth()
        ) { Text(if (advancedConnectionsVisible) "연결 설정 접기" else "연결 설정") }
        if (advancedConnectionsVisible) {
            ConnectionAccountSection(
                "Personal OS 공통 DB", SettingsConnection.SHARED,
                state.sharedConfig, state.sharedConnectionManaged, actions
            )
            ConnectionAccountSection(
                "영양 전용 DB", SettingsConnection.NUTRITION,
                state.nutritionConfig, state.nutritionConnectionManaged, actions
            )
            ConnectionAccountSection(
                "PriceTrace DB", SettingsConnection.PRICE_TRACE,
                state.priceTraceConfig, state.priceTraceConnectionManaged, actions
            )
        }
    } else {
        AccountControls(
            state.sharedConfig, SettingsConnection.SHARED, actions
        )
    }
}

@Composable
private fun ConnectionAccountSection(
    title: String,
    connection: SettingsConnection,
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    managed: Boolean,
    actions: SettingsScreenActions
) {
    var url by rememberSaveable(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by rememberSaveable(config.supabaseAnonKey) { mutableStateOf(config.supabaseAnonKey) }
    Text(title, fontWeight = FontWeight.Bold)
    Text(if (config.isConfigured) "로그인됨 · ${config.email}" else if (config.isConnectionConfigured) "로그인 필요" else "연결 없음")
    if (!managed) {
        AppTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("DB URL") })
        AppTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("DB anon key") })
        AppOutlinedButton(
            onClick = { actions.saveConnection(connection, url, key) },
            Modifier.fillMaxWidth()
        ) { Text("연결 저장") }
    } else {
        Text("빌드 기본값으로 연결되었습니다.")
    }
    AccountControls(config, connection, actions)
}

@Composable
private fun AccountControls(
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    connection: SettingsConnection,
    actions: SettingsScreenActions
) {
    var email by rememberSaveable(config.email) { mutableStateOf(config.email) }
    var password by rememberSaveable(config.projectRef()) { mutableStateOf("") }
    if (config.isConfigured) {
        AppOutlinedButton(
            onClick = { actions.signOut(connection) },
            Modifier.fillMaxWidth()
        ) { Text("로그아웃") }
    } else if (config.isConnectionConfigured) {
        AppTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") })
        AppTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppButton(
                onClick = { actions.signIn(connection, email, password) },
                Modifier.weight(1f)
            ) { Text("로그인") }
            AppOutlinedButton(
                onClick = { actions.signUp(connection, email, password) },
                Modifier.weight(1f)
            ) { Text("계정 만들기") }
        }
    }
}

