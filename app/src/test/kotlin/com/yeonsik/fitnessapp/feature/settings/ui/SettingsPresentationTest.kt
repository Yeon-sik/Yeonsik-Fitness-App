package com.yeonsik.fitnessapp.feature.settings.ui

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun themeModesUseFriendlyLabelsWithoutChangingStoredIds() {
        assertEquals("밝은 테마", themeModeLabel("light"))
        assertEquals("어두운 테마", themeModeLabel("dark"))
        assertEquals("기기 설정 따르기", themeModeLabel("system"))
    }

    @Test
    fun failedSyncDetailDoesNotExposeRawErrorText() {
        val state = state("sync failed", "server returned an access token")
        assertEquals("연결을 확인한 뒤 다시 시도하세요.", syncDetailForDisplay(state))
    }

    @Test
    fun successfulSyncKeepsLastSyncedAtDetail() {
        val state = state("synced", "마지막 동기화 2026-09-16T10:00:00Z")
        assertEquals(state.syncDetail, syncDetailForDisplay(state))
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
