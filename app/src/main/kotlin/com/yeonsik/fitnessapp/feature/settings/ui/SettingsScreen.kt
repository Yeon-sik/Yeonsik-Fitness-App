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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.*
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
    var advancedConnectionsVisible by rememberSaveable {
        mutableStateOf(!state.sharedConfig.isConnectionConfigured)
    }
    val accountControlsEnabled = !state.isAccountOperationInProgress && !state.isManualSyncing
    AppHeader("설정", "계정·동기화·데이터 안전·표시 환경")
    SettingsSectionTitle("상태")
    SettingsStatusCard(state.sharedConfig, state, Modifier.fillMaxWidth())
    SettingsSectionTitle("단위")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        MassUnit.values().forEach { unit ->
            AppOutlinedButton(onClick = { actions.setPreferredMassUnit(unit) }, selected = state.preferredMassUnit == unit) { Text(unit.labelKo()) }
        }
    }
    SettingsSupportingText("표시·입력 단위 설정은 로컬 기록을 kg 기준으로 보존합니다.")
    SettingsSectionTitle("테마")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        listOf("dark", "light", "system").forEach { mode ->
            AppOutlinedButton(onClick = { actions.setThemeMode(mode) }, selected = state.themeMode == mode) { Text(themeModeLabel(mode)) }
        }
    }
    SettingsSectionTitle("데이터 안전")
    AppButton(onClick = actions::runManualSync, enabled = accountControlsEnabled,
        modifier = Modifier.fillMaxWidth()) { Text(if (state.isManualSyncing) "동기화 중" else "지금 동기화") }
    SettingsSupportingText(syncDetailForDisplay(state))
    SettingsSectionTitle("가져오기·내보내기")
    AppOutlinedButton(onClick = actions::openFleekDataImport, enabled = !state.isDataImporting,
        modifier = Modifier.fillMaxWidth()) { Text("FLEEK 가져오기") }
    AppOutlinedButton(onClick = actions::openWorkoutTransferImport, enabled = !state.isDataTransferInProgress,
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 가져오기") }
    AppOutlinedButton(onClick = actions::exportWorkoutTransfer, enabled = !state.isDataTransferInProgress,
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 내보내기") }
    AppOutlinedButton(onClick = actions::createLocalBackup, Modifier.fillMaxWidth()) { Text("로컬 백업") }
    AppOutlinedButton(onClick = actions::restoreLocalBackup, Modifier.fillMaxWidth()) { Text("백업 복원") }
    AppOutlinedButton(onClick = actions::exportRecordsCsv, Modifier.fillMaxWidth()) { Text("CSV 내보내기") }
    if (state.isDataImporting && state.dataImportDetail.isNotBlank()) {
        Text("가져오기 상태 · " + state.dataImportDetail, style = MaterialTheme.typography.bodyMedium)
    }
    if (state.isDataTransferInProgress && state.dataTransferDetail.isNotBlank()) {
        Text("전송 상태 · " + state.dataTransferDetail, style = MaterialTheme.typography.bodyMedium)
    }
    SettingsPrivacyCard()
    SettingsAppInfoCard()
    AppOutlinedButton(
        onClick = { advancedConnectionsVisible = !advancedConnectionsVisible },
        Modifier.fillMaxWidth()
    ) { Text(if (advancedConnectionsVisible) "연결 설정 접기" else "연결 설정") }
    if (advancedConnectionsVisible) {
        SettingsSupportingText("DB URL과 공개 API 키를 저장한 뒤 각 DB 계정에 로그인하세요. 저장한 연결은 앱 업데이트 후에도 유지됩니다.")
        ConnectionAccountSection(
            "Personal OS 공통 DB", SettingsConnection.SHARED,
            state.sharedConfig, actions, accountControlsEnabled
        )
        ConnectionAccountSection(
            "영양 전용 DB", SettingsConnection.NUTRITION,
            state.nutritionConfig, actions, accountControlsEnabled
        )
        ConnectionAccountSection(
            "PriceTrace DB", SettingsConnection.PRICE_TRACE,
            state.priceTraceConfig, actions, accountControlsEnabled
        )
    } else {
        AccountControls(
            state.sharedConfig, SettingsConnection.SHARED, actions, accountControlsEnabled
        )
    }
}

@Composable
private fun ConnectionAccountSection(
    title: String,
    connection: SettingsConnection,
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    actions: SettingsScreenActions,
    enabled: Boolean
) {
    var url by rememberSaveable(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by rememberSaveable(config.supabaseAnonKey) { mutableStateOf(config.supabaseAnonKey) }
    SettingsSectionTitle(title)
    Text(if (config.isConfigured) "로그인됨 · ${config.email}" else if (config.isConnectionConfigured) "로그인 필요" else "연결 없음",
        style = MaterialTheme.typography.bodyMedium)
    AppTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("settings-url-${connection.name}"),
        label = { Text("DB URL") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        enabled = enabled)
    AppTextField(key, { key = it }, Modifier.fillMaxWidth().testTag("settings-key-${connection.name}"),
        label = { Text("공개 API 키 (anon / publishable)") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        enabled = enabled)
    AppOutlinedButton(
        onClick = { actions.saveConnection(connection, url, key) },
        Modifier.fillMaxWidth().testTag("settings-save-${connection.name}"), enabled = enabled
    ) { Text("연결 저장") }
    AccountControls(config, connection, actions, enabled)
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, Modifier.padding(top = AppSpacing.section).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AccountControls(
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    connection: SettingsConnection,
    actions: SettingsScreenActions,
    enabled: Boolean
) {
    var email by rememberSaveable(config.email) { mutableStateOf(config.email) }
    var password by remember(config.supabaseUrl, config.isConfigured) { mutableStateOf("") }
    if (config.isConfigured) {
        AppOutlinedButton(
            onClick = { actions.signOut(connection) },
            Modifier.fillMaxWidth(), enabled = enabled
        ) { Text("로그아웃") }
    } else if (config.isConnectionConfigured) {
        AppTextField(email, { email = it }, Modifier.fillMaxWidth().testTag("settings-email-${connection.name}"), label = { Text("이메일") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            enabled = enabled)
        AppTextField(
            password, { password = it }, Modifier.fillMaxWidth().testTag("settings-password-${connection.name}"), label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            enabled = enabled
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppButton(
                onClick = { actions.signIn(connection, email, password) },
                Modifier.weight(1f).testTag("settings-login-${connection.name}"), enabled = enabled
            ) { Text("로그인") }
            AppOutlinedButton(
                onClick = { actions.signUp(connection, email, password) },
                Modifier.weight(1f), enabled = enabled
            ) { Text("계정 만들기") }
        }
    }
}

