package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

import java.util.List;

/**
 * 화면 렌더러가 MainActivity에 접근하는 유일한 통로.
 * 화면은 "어떻게 그릴지"만 담당하고, 내비게이션·의존성·앱 수준 상태는 host가 소유한다.
 */
public interface ScreenHost {
    Activity activity();

    FitnessUi ui();

    LinearLayout content();

    FitnessRepository repository();

    NutritionCatalogRepository nutritionCatalogRepository();

    CardioRepository cardioRepository();

    RoutineRepository routineRepository();

    ExerciseMasterRepository exerciseMasterRepository();

    WorkoutSessionState sessionState();

    String today();

    FitnessScreen currentScreen();

    void navigate(FitnessScreen screen);

    void rerender();

    void toast(String message);

    // ── 화면 간 공유 액션 ─────────────────────────────────────────────

    void openWorkoutSession(String recordId);

    void openWorkoutExercisePicker();

    void finishActiveWorkout();

    void continueWorkoutIfAvailable();

    void startRoutineWorkout(List<RoutineExerciseInstance> exercises);

    String currentWorkoutRecordId();

    void confirmDeleteSession(String recordId);

    void startEmptyWorkout();

    void startCardioWorkout(CardioActivityType activityType);

    void openCardioSummary(String recordId);

    void pauseCardioWorkout();

    void resumeCardioWorkout();

    void finishCardioWorkout();

    void editCardioAverageHeartRate();

    void cancelCardioWorkout();

    void showBodyMetricDialog();

    void showBodyMetricDialog(String date, String recordId);

    void openMealManagement();

    /** 세트 완료 시 휴식 타이머를 시작한다. null 또는 0 이하이면 기본 90초. */
    void startRestTimer(Integer restSeconds);

    // ── 테마 ─────────────────────────────────────────────────────────

    /** 현재 테마 모드: "light" | "dark" | "system". 기본은 light. */
    String themeMode();

    void setThemeMode(String mode);

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    void openFleekDataImport();

    boolean isDataImporting();

    String dataImportDetail();

    SupabaseConfig supabaseConfig();

    boolean isSharedSupabaseConnectionManaged();

    void saveSupabaseConfig(String url, String anonKey);

    SupabaseConfig nutritionSupabaseConfig();

    void saveNutritionSupabaseConfig(String url, String anonKey);

    void signInToSupabase(String email, String password);

    void signUpToSupabase(String email, String password);

    void signOutFromSupabase();

    void signInToNutritionSupabase(String email, String password);

    void signUpToNutritionSupabase(String email, String password);

    void signOutFromNutritionSupabase();

    void runManualSync();

    boolean isManualSyncing();

    String syncLabel();

    String syncDetail();

    String repositoryUserLabel();
}
