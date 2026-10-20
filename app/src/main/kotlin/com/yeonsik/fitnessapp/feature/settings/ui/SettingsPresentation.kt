package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppButton
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.ui.UiState
import com.yeonsik.fitnessapp.ui.SettingsUiPolicy

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsStatusCard(
    config: SupabaseConfig,
    state: SettingsUiState,
    onSync: () -> Unit,
    syncEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val accountLabel = when {
        config.isConfigured -> "로그인됨"
        config.isConnectionConfigured -> "로그인 필요"
        else -> "로컬 전용"
    }
    val syncLabel = if (state.isManualSyncing) "syncing" else state.syncLabel
    AppCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("계정", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(accountLabel, fontSize = 14.sp, lineHeight = 24.sp)
            }
            if (config.isConfigured && config.email.isNotBlank()) {
                SettingsSupportingText(config.email)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("동기화", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    SettingsUiPolicy.syncStatusLabel(syncLabel),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    fontSize = 14.sp, lineHeight = 24.sp,
                    color = if (semanticStatusForSync(syncLabel) == FitnessSemanticStatus.ERROR)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            SettingsSupportingText(syncDetailForDisplay(state))
            AppButton(onClick = onSync, enabled = syncEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        state.isManualSyncing -> "동기화 중"
                        syncLabel == "sync failed" || syncLabel == "partial" -> "다시 동기화"
                        else -> "지금 동기화"
                    },
                    fontSize = 16.sp, lineHeight = 24.sp
                )
            }
            if (state.isAccountOperationInProgress && !state.isManualSyncing) {
                SettingsSupportingText("계정 처리가 끝나면 동기화할 수 있습니다.")
            }
            SettingsSupportingText("기록은 기기에 먼저 저장됩니다.")
        }
    }
}

internal fun semanticStatusForSync(label: String): FitnessSemanticStatus =
    when (SettingsUiPolicy.syncStateForLabel(label)) {
        UiState.SUCCESS -> FitnessSemanticStatus.SUCCESS
        UiState.LOADING,
        UiState.PERMISSION_REQUIRED,
        UiState.SYNC_DELAYED -> FitnessSemanticStatus.WARNING
        UiState.SERVER_ERROR -> FitnessSemanticStatus.ERROR
        UiState.OFFLINE -> FitnessSemanticStatus.INFO
        UiState.EMPTY,
        UiState.VALIDATION_ERROR,
        UiState.DESTRUCTIVE_CONFIRMATION -> FitnessSemanticStatus.UNKNOWN
    }

@Composable
internal fun SettingsPrivacyCard() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("개인정보·보안", style = MaterialTheme.typography.titleMedium)
            SettingsSupportingText("내 계정의 기록을 관리합니다. 동기화는 직접 실행할 수 있습니다.")
            SettingsSupportingText("비밀번호는 입력 중에도 가려서 표시합니다.")
        }
}

@Composable
internal fun SettingsAppInfoCard() {
        Column(
            Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("앱 정보", style = MaterialTheme.typography.titleMedium)
            Text("FitnessApp", style = MaterialTheme.typography.bodyMedium)
            SettingsSupportingText("버전 · " + BuildConfig.VERSION_NAME)
            SettingsSupportingText("운동·체중·식단 기록을 로컬 우선으로 관리하는 앱입니다.")
        }
}

@Composable
internal fun SettingsSupportingText(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> "밝게"
    "dark" -> "어둡게"
    "system" -> "기기 설정"
    else -> "기본 테마"
}

internal fun syncDetailForDisplay(state: SettingsUiState): String = when {
    state.isManualSyncing -> SettingsUiPolicy.safeSyncDetailForSurface("syncing")
    state.syncLabel == "synced" -> if (state.syncDetail.startsWith("마지막 동기화 ")) {
        formatSettingsSyncTime(state.syncDetail.removePrefix("마지막 동기화 ").trim())
    } else {
        SettingsUiPolicy.safeSyncDetailForSurface(state.syncLabel)
    }
    state.syncLabel == "configured" -> "동기화를 실행하면 최근 상태를 확인할 수 있습니다."
    state.syncLabel == "login required" -> "계정에 로그인하면 동기화할 수 있습니다."
    state.syncLabel == "local-only" -> "아래 연결 설정에서 계정을 연결할 수 있습니다."
    state.syncLabel == "authentication failed" -> state.authenticationErrorDetail.ifBlank {
        SettingsUiPolicy.safeSyncDetailForSurface(state.syncLabel)
    }
    else -> SettingsUiPolicy.safeSyncDetailForSurface(state.syncLabel)
}
