package com.yeonsik.fitnessapp.state;

/**
 * 앱의 단일 화면 상태. 기존 showingXXXScreen boolean 플래그 조합을 대체한다.
 */
public enum FitnessScreen {
    HOME,
    WORKOUT,
    ROUTINE_ADD,
    ROUTINE_DETAIL,
    WORKOUT_SESSION,
    WORKOUT_EXERCISE_DETAIL,
    WORKOUT_EXERCISE_ADD,
    WORKOUT_SUMMARY,
    RECORDS,
    SETTINGS;

    /** 모든 화면이 동일한 라이트 크롬을 사용한다. */
    public boolean inverse() {
        return false;
    }
}
