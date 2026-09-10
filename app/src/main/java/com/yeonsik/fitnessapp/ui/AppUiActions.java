package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.state.FitnessScreen;

/**
 * 화면 렌더러가 MainActivity에 접근하는 유일한 통로.
 * 화면은 "어떻게 그릴지"만 담당하고, 내비게이션·의존성·앱 수준 상태는 host가 소유한다.
 */
public interface AppUiActions {
    /** Owner id used to reject stale ViewModel work after an account switch. */
    String currentOwnerId();

    String selectedRoutineId();

    void openWorkoutExerciseDetail(String exerciseId);

    void openWorkoutExerciseReplacementPicker(String exerciseId);

    void refreshWorkoutExerciseDetail();

    void navigate(FitnessScreen screen);

    /** Pops one screen entry. Dialog dismissal is owned by the system Back dispatcher. */
    default boolean back() {
        return false;
    }

    /** Replaces the current history entry for flow transitions such as session → summary. */
    default void replace(FitnessScreen screen) {
        navigate(screen);
    }

    void toast(String message);

    void selectRoutine(String routineId);

    // ── 화면 간 공유 액션 ─────────────────────────────────────────────

    void openWorkoutSession(String recordId);

    /** Platform bridge for the GPS service; lifecycle/permission work stays in the Activity. */
    void startCardioTracking(String recordId);

    void resumeCardioTracking(String recordId);

    void pauseCardioTracking(String recordId);

    void stopCardioTracking();

    /** Requests the OS permission flow before the cardio ViewModel resumes a paused record. */
    void requestCardioResume(CardioActivityType activityType, String recordId);

    /** Consumes a finish request delivered by the tracking-service Intent exactly once. */
    boolean consumePendingCardioFinishRequest();

    void clearActiveWorkout(String recordId);

    String currentWorkoutRecordId();

    /** Exercise being replaced while the workout exercise picker is open. */
    default String currentWorkoutReplacementExerciseId() {
        return null;
    }

    /** Platform-only permission bridge after CardioSessionViewModel has approved a start. */
    void requestCardioStart(CardioActivityType activityType);

    void openMealManagement();

    void openMealManagement(String date);

    void openMealManagement(String date, FitnessScreen returnScreen);

    // ── 테마 ─────────────────────────────────────────────────────────

    /** 현재 테마 모드: "light" | "dark" | "system". 기본은 light. */
    String themeMode();

    void setThemeMode(String mode);

    /** Preferred presentation/input unit; all repository calculations remain in kg. */
    default MassUnit preferredMassUnit() {
        return MassUnit.KG;
    }

    default void setPreferredMassUnit(MassUnit unit) {
    }

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
