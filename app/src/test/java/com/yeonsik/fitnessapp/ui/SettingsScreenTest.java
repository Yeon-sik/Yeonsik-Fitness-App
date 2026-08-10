package com.yeonsik.fitnessapp.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SettingsScreenTest {
    @Test
    public void syncStatusColorHighlightsActionableAccountStates() {
        assertEquals(
                FitnessUi.COLOR_WARNING,
                SettingsScreen.syncStatusColorForLabel("login required")
        );
        assertEquals(
                FitnessUi.COLOR_WARNING,
                SettingsScreen.syncStatusColorForLabel("confirmation required")
        );
    }

    @Test
    public void syncStatusColorMarksOwnershipFailuresAsErrors() {
        assertEquals(
                FitnessUi.COLOR_NEGATIVE,
                SettingsScreen.syncStatusColorForLabel("local ownership failed")
        );
        assertEquals(
                FitnessUi.COLOR_NEGATIVE,
                SettingsScreen.syncStatusColorForLabel("nutrition ownership failed")
        );
    }

    @Test
    public void syncStatusColorKeepsNeutralAndSuccessfulStatesDistinct() {
        assertEquals(
                FitnessUi.COLOR_POSITIVE,
                SettingsScreen.syncStatusColorForLabel("synced")
        );
        assertEquals(
                FitnessUi.COLOR_TERTIARY,
                SettingsScreen.syncStatusColorForLabel("local-only")
        );
    }

    @Test
    public void syncStatusLabelTranslatesInternalStatesForUsers() {
        assertEquals("동기화 완료", SettingsScreen.syncStatusLabel("synced"));
        assertEquals("로그인 필요", SettingsScreen.syncStatusLabel("login required"));
        assertEquals("소유권 확인 실패", SettingsScreen.syncStatusLabel("nutrition ownership failed"));
        assertEquals("로컬 전용", SettingsScreen.syncStatusLabel("local-only"));
    }
}
