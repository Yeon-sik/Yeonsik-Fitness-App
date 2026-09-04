package com.yeonsik.fitnessapp.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class UiStateTest {
    @Test
    public void exposesThePhase6SharedStateVocabulary() {
        assertEquals(
                9,
                UiState.values().length
        );
        assertEquals("불러오는 중", UiState.LOADING.label());
        assertEquals("내용 없음", UiState.EMPTY.label());
        assertEquals("오프라인", UiState.OFFLINE.label());
        assertEquals("권한 필요", UiState.PERMISSION_REQUIRED.label());
        assertEquals("동기화 지연", UiState.SYNC_DELAYED.label());
        assertEquals("입력 확인 필요", UiState.VALIDATION_ERROR.label());
        assertEquals("서버 오류", UiState.SERVER_ERROR.label());
        assertEquals("확인 필요", UiState.DESTRUCTIVE_CONFIRMATION.label());
        assertEquals("완료", UiState.SUCCESS.label());
    }
}
