package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.ui.SettingsUiPolicy

@Composable
internal fun SettingsAccountStatusCard(config: SupabaseConfig) {
    val status = when {
        config.isConfigured -> "연결됨 · 로그인됨"
        config.isConnectionConfigured -> "연결됨 · 로그인 필요"
        else -> "연결 안 됨 · 로컬 전용"
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("계정 상태", style = MaterialTheme.typography.titleMedium)
            Text("Personal OS 공통 계정 · " + status, style = MaterialTheme.typography.bodyMedium)
            if (config.isConfigured && config.email.isNotBlank()) {
                Text("로그인 계정 · " + config.email, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSupportingText("계정 상태와 동기화 상태는 별도로 표시합니다.")
        }
    }
}

@Composable
internal fun SettingsSyncStatusCard(state: SettingsUiState) {
    val label = SettingsUiPolicy.syncStatusLabel(state.syncLabel)
    val category = SettingsUiPolicy.syncStateForLabel(state.syncLabel).label()
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("동기화 상태", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
            SettingsSupportingText("상태 분류 · " + category)
            SettingsSupportingText(syncDetailForDisplay(state))
            SettingsSupportingText("동기화 여부와 관계없이 로컬 기록을 먼저 보관합니다.")
        }
    }
}

@Composable
internal fun SettingsPrivacyCard() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("개인정보·보안", style = MaterialTheme.typography.titleMedium)
            SettingsSupportingText("이 화면에는 비밀번호와 access/refresh token을 표시하지 않습니다.")
            SettingsSupportingText("기록은 계정 소유 범위 안에서 읽고, 외부 동기화는 사용자가 명시적으로 실행합니다.")
        }
    }
}

@Composable
internal fun SettingsAppInfoCard() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("앱 정보", style = MaterialTheme.typography.titleMedium)
            Text("FitnessApp", style = MaterialTheme.typography.bodyMedium)
            SettingsSupportingText("버전 · " + BuildConfig.VERSION_NAME)
            SettingsSupportingText("운동·체중·식사 기록을 로컬 우선으로 관리하는 앱입니다.")
        }
    }
}

@Composable
internal fun SettingsSupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> "밝은 테마"
    "dark" -> "어두운 테마"
    "system" -> "기기 설정 따르기"
    else -> "기본 테마"
}

internal fun syncDetailForDisplay(state: SettingsUiState): String = when (state.syncLabel) {
    "synced" -> state.syncDetail
        .takeIf { it.startsWith("마지막 동기화") }
        ?: SettingsUiPolicy.safeSyncDetailForSurface(state.syncLabel)
    "configured", "login required", "local-only" -> state.syncDetail
    else -> SettingsUiPolicy.safeSyncDetailForSurface(state.syncLabel)
}
