package com.yeonsik.fitnessapp.ui;

/** Shared user-facing state vocabulary for loading, data, and action surfaces. */
public enum UiState {
    LOADING("불러오는 중"),
    EMPTY("내용 없음"),
    OFFLINE("오프라인"),
    PERMISSION_REQUIRED("권한 필요"),
    SYNC_DELAYED("동기화 지연"),
    VALIDATION_ERROR("입력 확인 필요"),
    SERVER_ERROR("서버 오류"),
    DESTRUCTIVE_CONFIRMATION("확인 필요"),
    SUCCESS("완료");

    private final String label;

    UiState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
