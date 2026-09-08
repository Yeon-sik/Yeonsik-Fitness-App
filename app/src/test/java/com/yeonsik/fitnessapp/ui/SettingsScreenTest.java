package com.yeonsik.fitnessapp.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SettingsScreenTest {
    @Test
    public void syncStatusColorHighlightsActionableAccountStates() {
        assertEquals(
                FitnessUi.COLOR_WARNING,
                SettingsUiPolicy.syncStatusColorForLabel("login required")
        );
        assertEquals(
                FitnessUi.COLOR_WARNING,
                SettingsUiPolicy.syncStatusColorForLabel("confirmation required")
        );
    }

    @Test
    public void syncStatusColorMarksOwnershipFailuresAsErrors() {
        assertEquals(
                FitnessUi.COLOR_NEGATIVE,
                SettingsUiPolicy.syncStatusColorForLabel("local ownership failed")
        );
        assertEquals(
                FitnessUi.COLOR_NEGATIVE,
                SettingsUiPolicy.syncStatusColorForLabel("nutrition ownership failed")
        );
    }

    @Test
    public void syncStatusColorKeepsNeutralAndSuccessfulStatesDistinct() {
        assertEquals(
                FitnessUi.COLOR_POSITIVE,
                SettingsUiPolicy.syncStatusColorForLabel("synced")
        );
        assertEquals(
                FitnessUi.COLOR_TERTIARY,
                SettingsUiPolicy.syncStatusColorForLabel("local-only")
        );
    }

    @Test
    public void syncStatusLabelTranslatesInternalStatesForUsers() {
        assertEquals("동기화 완료", SettingsUiPolicy.syncStatusLabel("synced"));
        assertEquals("로그인 필요", SettingsUiPolicy.syncStatusLabel("login required"));
        assertEquals("소유권 확인 실패", SettingsUiPolicy.syncStatusLabel("nutrition ownership failed"));
        assertEquals("로컬 전용", SettingsUiPolicy.syncStatusLabel("local-only"));
    }

    @Test
    public void userSurfaceMapsSyncLabelsToSharedStateGrammar() {
        assertEquals(UiState.SUCCESS, SettingsUiPolicy.syncStateForLabel("synced"));
        assertEquals(UiState.LOADING, SettingsUiPolicy.syncStateForLabel("syncing"));
        assertEquals(UiState.PERMISSION_REQUIRED,
                SettingsUiPolicy.syncStateForLabel("login required"));
        assertEquals(UiState.SYNC_DELAYED, SettingsUiPolicy.syncStateForLabel("partial"));
        assertEquals(UiState.SERVER_ERROR, SettingsUiPolicy.syncStateForLabel("sync failed"));
        assertEquals(UiState.OFFLINE, SettingsUiPolicy.syncStateForLabel("local-only"));
    }

    @Test
    public void userSurfaceUsesSafeSyncCopyInsteadOfInternalDetails() {
        assertEquals(
                "연결을 확인한 뒤 다시 시도하세요.",
                SettingsUiPolicy.safeSyncDetailForSurface("sync failed")
        );
        assertEquals(
                "현재 기록은 이 기기에 안전하게 보관됩니다.",
                SettingsUiPolicy.safeSyncDetailForSurface("local-only")
        );
    }
}
