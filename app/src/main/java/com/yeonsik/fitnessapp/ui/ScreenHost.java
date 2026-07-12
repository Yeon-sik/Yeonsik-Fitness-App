package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
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

    void showBodyMetricDialog();

    void showBodyMetricDialog(String date, String recordId);

    void showMealDialog();

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    SupabaseConfig supabaseConfig();

    void saveSupabaseConfig(String url, String anonKey, String userId);

    void runManualSync();

    boolean isManualSyncing();

    String syncLabel();

    String syncDetail();

    String repositoryUserLabel();
}
