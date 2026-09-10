package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;

/**
 * 화면 렌더러가 MainActivity에 접근하는 유일한 통로.
 * 화면은 "어떻게 그릴지"만 담당하고, 내비게이션·의존성·앱 수준 상태는 host가 소유한다.
 */
public interface AppUiActions {
    /** Owner id used to reject stale ViewModel work after an account switch. */
    String currentOwnerId();

    void toast(String message);

    /** Platform bridge for the GPS service; lifecycle/permission work stays in the Activity. */
    void startCardioTracking(String recordId);

    void resumeCardioTracking(String recordId);

    void pauseCardioTracking(String recordId);

    void stopCardioTracking();

    /** Requests the OS permission flow before the cardio ViewModel resumes a paused record. */
    void requestCardioResume(CardioActivityType activityType, String recordId);

    /** Platform-only permission bridge after CardioSessionViewModel has approved a start. */
    void requestCardioStart(CardioActivityType activityType);

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    void openFleekDataImport();

    default void openWorkoutTransferImport() {
    }

    default void exportWorkoutTransfer() {
    }

    void createLocalBackup();

    void restoreLocalBackup();

    void exportRecordsCsv();

}
