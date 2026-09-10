package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.List;

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

    void clearInProgressWorkout(String recordId);

    /** Opens a stored record using its owning feature (strength or cardio). */
    default void openRecord(String recordId) {
        openWorkoutSession(recordId);
    }

    void openWorkoutExercisePicker();

    void finishActiveWorkout();

    void continueWorkoutIfAvailable();

    void startRoutineWorkout(
            List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance> exercises
    );


    String currentWorkoutRecordId();

    void confirmDeleteSession(String recordId);

    void startEmptyWorkout();

    void startCardioWorkout(CardioActivityType activityType);

    void openMealManagement();

    void openMealManagement(String date);

    void openMealManagement(String date, FitnessScreen returnScreen);

    /** 세트 완료 시 휴식 타이머를 시작한다. null 또는 0 이하이면 기본 90초. */
    void startRestTimer(Integer restSeconds);

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
