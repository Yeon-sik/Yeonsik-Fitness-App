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
import com.yeonsik.fitnessapp.app.navigation.*
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsScreen(host: ScreenHost) {
    var advancedConnectionsVisible by rememberSaveable { mutableStateOf(false) }
    AppHeader("설정")
    Text("표시 단위")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        MassUnit.values().forEach { unit ->
            AppOutlinedButton(onClick = { host.setPreferredMassUnit(unit) }, selected = host.preferredMassUnit() == unit) { Text(unit.labelKo()) }
        }
    }
    Text("테마")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        listOf("light", "dark", "system").forEach { mode ->
            AppOutlinedButton(onClick = { host.setThemeMode(mode) }, selected = host.themeMode() == mode) { Text(mode) }
        }
    }
    AppButton(onClick = { host.runManualSync() }, enabled = !host.isManualSyncing(),
        modifier = Modifier.fillMaxWidth()) { Text(host.syncLabel()) }
    Text(host.syncDetail())
    AppOutlinedButton(onClick = { host.openFleekDataImport() }, enabled = !host.isDataImporting(),
        modifier = Modifier.fillMaxWidth()) { Text("FLEEK 가져오기") }
    AppOutlinedButton(onClick = { host.openWorkoutTransferImport() }, enabled = !host.isDataTransferInProgress(),
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 가져오기") }
    AppOutlinedButton(onClick = { host.exportWorkoutTransfer() }, enabled = !host.isDataTransferInProgress(),
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 내보내기") }
    AppOutlinedButton(onClick = { host.createLocalBackup() }, Modifier.fillMaxWidth()) { Text("로컬 백업") }
    AppOutlinedButton(onClick = { host.restoreLocalBackup() }, Modifier.fillMaxWidth()) { Text("백업 복원") }
    AppOutlinedButton(onClick = { host.exportRecordsCsv() }, Modifier.fillMaxWidth()) { Text("CSV 내보내기") }
    if (host.isDeveloperSurfaceAllowed()) {
        AppOutlinedButton(
            onClick = { advancedConnectionsVisible = !advancedConnectionsVisible },
            Modifier.fillMaxWidth()
        ) { Text(if (advancedConnectionsVisible) "연결 설정 접기" else "연결 설정") }
        if (advancedConnectionsVisible) {
            ConnectionAccountSection(
                "Personal OS 공통 DB", host.supabaseConfig(), host.isSharedSupabaseConnectionManaged(),
                host::saveSupabaseConfig, host::signInToSupabase, host::signUpToSupabase,
                host::signOutFromSupabase
            )
            ConnectionAccountSection(
                "영양 전용 DB", host.nutritionSupabaseConfig(), host.isNutritionSupabaseConnectionManaged(),
                host::saveNutritionSupabaseConfig, host::signInToNutritionSupabase,
                host::signUpToNutritionSupabase, host::signOutFromNutritionSupabase
            )
            ConnectionAccountSection(
                "PriceTrace DB", host.priceTraceSupabaseConfig(), host.isPriceTraceSupabaseConnectionManaged(),
                host::savePriceTraceSupabaseConfig, host::signInToPriceTraceSupabase,
                host::signUpToPriceTraceSupabase, host::signOutFromPriceTraceSupabase
            )
        }
    } else {
        AccountControls(
            host.supabaseConfig(), host::signInToSupabase, host::signUpToSupabase,
            host::signOutFromSupabase
        )
    }
}

@Composable
private fun ConnectionAccountSection(
    title: String,
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    managed: Boolean,
    saveConnection: (String, String) -> Unit,
    signIn: (String, String) -> Unit,
    signUp: (String, String) -> Unit,
    signOut: () -> Unit
) {
    var url by rememberSaveable(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by rememberSaveable(config.supabaseAnonKey) { mutableStateOf(config.supabaseAnonKey) }
    Text(title, fontWeight = FontWeight.Bold)
    Text(if (config.isConfigured) "로그인됨 · ${config.email}" else if (config.isConnectionConfigured) "로그인 필요" else "연결 없음")
    if (!managed) {
        AppTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("DB URL") })
        AppTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("DB anon key") })
        AppOutlinedButton(onClick = { saveConnection(url, key) }, Modifier.fillMaxWidth()) { Text("연결 저장") }
    } else {
        Text("빌드 기본값으로 연결되었습니다.")
    }
    AccountControls(config, signIn, signUp, signOut)
}

@Composable
private fun AccountControls(
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    signIn: (String, String) -> Unit,
    signUp: (String, String) -> Unit,
    signOut: () -> Unit
) {
    var email by rememberSaveable(config.email) { mutableStateOf(config.email) }
    var password by rememberSaveable(config.projectRef()) { mutableStateOf("") }
    if (config.isConfigured) {
        AppOutlinedButton(onClick = signOut, Modifier.fillMaxWidth()) { Text("로그아웃") }
    } else if (config.isConnectionConfigured) {
        AppTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") })
        AppTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppButton(onClick = { signIn(email, password) }, Modifier.weight(1f)) { Text("로그인") }
            AppOutlinedButton(onClick = { signUp(email, password) }, Modifier.weight(1f)) { Text("계정 만들기") }
        }
    }
}

