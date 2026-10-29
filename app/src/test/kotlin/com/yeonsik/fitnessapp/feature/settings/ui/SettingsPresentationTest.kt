package com.yeonsik.fitnessapp.feature.settings.ui

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.ThemeModePreferences
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun themeModesUseFriendlyLabelsWithoutChangingStoredIds() {
        assertEquals("밝게", themeModeLabel("light"))
        assertEquals("어둡게", themeModeLabel("dark"))
        assertEquals("기기 설정", themeModeLabel("system"))
        assertEquals("dark", ThemeModePreferences.DEFAULT_THEME_MODE)
    }

    @Test
    fun syncStatusUsesSemanticUiState() {
        assertEquals(FitnessSemanticStatus.SUCCESS, semanticStatusForSync("synced"))
        assertEquals(FitnessSemanticStatus.WARNING, semanticStatusForSync("partial"))
        assertEquals(FitnessSemanticStatus.ERROR, semanticStatusForSync("sync failed"))
        assertEquals(FitnessSemanticStatus.INFO, semanticStatusForSync("local-only"))
    }

    @Test
    fun failedSyncDetailDoesNotExposeRawErrorText() {
        val state = state("sync failed", "server returned an access token")
        assertEquals("연결을 확인한 뒤 다시 시도하세요.", syncDetailForDisplay(state))
    }

    @Test
    fun successfulSyncFormatsOnlyTheExistingTimestamp() {
        val timestamp = "2026-09-16T10:00:00Z"
        assertEquals(formatSettingsSyncTime(timestamp), syncDetailForDisplay(state("synced", "마지막 동기화 $timestamp")))
        assertEquals("최근 동기화가 완료되었습니다.", syncDetailForDisplay(state("synced", "push 42 rows")))
    }

    @Test
    fun unknownTimeAndConnectionDetailsDoNotLeakRawValues() {
        assertEquals("최근 동기화 시각을 확인할 수 없습니다.",
            syncDetailForDisplay(state("synced", "마지막 동기화 access_token=private")))
        assertEquals("동기화를 실행하면 최근 상태를 확인할 수 있습니다.",
            syncDetailForDisplay(state("configured", "internal connection detail")))
    }

    @Test
    fun inFlightSyncDoesNotDisplayStaleSuccess() {
        assertEquals("기록을 서비스와 맞추는 중입니다.",
            syncDetailForDisplay(state("synced", "마지막 동기화 2026-09-16T10:00:00Z").copy(isManualSyncing = true)))
    }

    @Test
    fun inFlightSyncDisplaysControlledPhaseProgressWithoutRawDetails() {
        val syncing = state("syncing", "access_token=private").copy(
            isManualSyncing = true, syncProgressDetail = "완료 운동 요약을 전송하고 있습니다. (1/2)"
        )
        assertEquals("완료 운동 요약을 전송하고 있습니다. (1/2)", syncDetailForDisplay(syncing))
        assertEquals("연결을 확인한 뒤 다시 시도하세요.",
            syncDetailForDisplay(syncing.copy(isManualSyncing = false, syncLabel = "sync failed")))
    }

    @Test
    fun authenticationShowsControlledDetailAndHidesRawServerText() {
        val raw = state("authentication failed", "access_token=private")
        assertEquals("로그인 정보를 확인한 뒤 다시 시도하세요.", syncDetailForDisplay(raw))
        val controlled = raw.copy(authenticationErrorDetail = "공통 DB · 이메일 인증이 필요합니다.")
        assertEquals(controlled.authenticationErrorDetail, syncDetailForDisplay(controlled))
    }

    private fun state(label: String, detail: String) = SettingsUiState(
        themeMode = "light",
        preferredMassUnit = MassUnit.KG,
        syncLabel = label,
        syncDetail = detail,
        isManualSyncing = false,
        isDataImporting = false,
        dataImportDetail = "",
        isDataTransferInProgress = false,
        dataTransferDetail = "",
        sharedConfig = SupabaseConfig.empty(),
        sharedConnectionManaged = false,
        nutritionConfig = SupabaseConfig.empty(),
        nutritionConnectionManaged = false,
        priceTraceConfig = SupabaseConfig.empty(),
        priceTraceConnectionManaged = false,
        developerSurfaceAllowed = false
    )
}
