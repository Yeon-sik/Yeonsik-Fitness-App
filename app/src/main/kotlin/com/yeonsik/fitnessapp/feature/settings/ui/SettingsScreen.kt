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
internal fun SettingsScreen(state: SettingsUiState, actions: SettingsScreenActions) {
    var advancedConnectionsVisible by rememberSaveable {
        mutableStateOf(!state.sharedConfig.isConnectionConfigured)
    }
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var importExpanded by rememberSaveable { mutableStateOf(false) }
    var exportExpanded by rememberSaveable { mutableStateOf(false) }
    var informationExpanded by rememberSaveable { mutableStateOf(false) }
    val accountControlsEnabled = !state.isAccountOperationInProgress && !state.isManualSyncing
    AppHeader("설정", "계정과 기록 관리")
    SettingsStatusCard(
        state.sharedConfig, state,
        onSync = actions::runManualSync,
        syncEnabled = accountControlsEnabled && state.sharedConfig.isConfigured,
        modifier = Modifier.fillMaxWidth()
    )
    if (!advancedConnectionsVisible && !state.sharedConfig.isConfigured) {
        AccountControls(state.sharedConfig, SettingsConnection.SHARED, actions, accountControlsEnabled)
    }
    SettingsSectionTitle("표시")
    SettingsChoiceGroup(
        title = "무게 단위",
        choices = MassUnit.values().map { it.name to it.labelKo() },
        selectedKey = state.preferredMassUnit.name,
        onSelect = { actions.setPreferredMassUnit(MassUnit.valueOf(it)) },
        modifier = Modifier.fillMaxWidth()
    )
    SettingsSupportingText("선택한 단위로 표시하고 입력합니다. 기존 기록은 유지됩니다.")
    SettingsChoiceGroup(
        title = "테마",
        choices = listOf("dark", "light", "system").map { it to themeModeLabel(it) },
        selectedKey = state.themeMode,
        onSelect = actions::setThemeMode,
        modifier = Modifier.fillMaxWidth()
    )
    SettingsSectionTitle("데이터 관리")
    SettingsExpandableGroup(
        title = "백업 및 복원",
        summary = "기록을 파일로 보관하거나 복구",
        expanded = backupExpanded,
        onExpandedChange = { backupExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsAction("로컬 백업", actions::createLocalBackup)
        SettingsAction("백업 복원", actions::restoreLocalBackup)
    }
    SettingsExpandableGroup(
        title = "다른 앱 기록 가져오기",
        summary = "FLEEK · 운동 전송 파일",
        expanded = importExpanded,
        onExpandedChange = { importExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsAction("FLEEK 가져오기", actions::openFleekDataImport, enabled = !state.isDataImporting)
        SettingsAction("운동 전송 가져오기", actions::openWorkoutTransferImport, enabled = !state.isDataTransferInProgress)
    }
    SettingsExpandableGroup(
        title = "파일 내보내기",
        summary = "운동 전송 · CSV",
        expanded = exportExpanded,
        onExpandedChange = { exportExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsAction("운동 전송 내보내기", actions::exportWorkoutTransfer, enabled = !state.isDataTransferInProgress)
        SettingsAction("CSV 내보내기", actions::exportRecordsCsv)
    }
    if (state.isDataImporting && state.dataImportDetail.isNotBlank()) {
        Text("가져오기 상태 · " + state.dataImportDetail, style = MaterialTheme.typography.bodyMedium)
    }
    if (state.isDataTransferInProgress && state.dataTransferDetail.isNotBlank()) {
        Text("전송 상태 · " + state.dataTransferDetail, style = MaterialTheme.typography.bodyMedium)
    }
    SettingsSectionTitle("계정 및 앱 정보")
    SettingsExpandableGroup(
        title = "개인정보 및 앱 정보",
        summary = "기록 보관 안내 · 버전",
        expanded = informationExpanded,
        onExpandedChange = { informationExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsPrivacyCard()
        SettingsAppInfoCard()
    }
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
    } else if (state.sharedConfig.isConfigured) {
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
    Text(title, Modifier.padding(top = 24.dp).semantics { heading() },
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

