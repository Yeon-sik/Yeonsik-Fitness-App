package com.yeonsik.fitnessapp.state;

/**
 * 앱의 단일 화면 상태. 기존 showingXXXScreen boolean 플래그 조합을 대체한다.
 */
public enum FitnessScreen {
    HOME,
    WORKOUT,
    STRENGTH,
    CARDIO,
    ROUTINE_ADD,
    ROUTINE_DETAIL,
    WORKOUT_SESSION,
    WORKOUT_EXERCISE_DETAIL,
    WORKOUT_EXERCISE_ADD,
    WORKOUT_SUMMARY,
    CARDIO_SESSION,
    CARDIO_SUMMARY,
    MEALS,
    SUPPLEMENTS,
    RECORDS,
    DEVELOPMENT,
    SETTINGS
}
