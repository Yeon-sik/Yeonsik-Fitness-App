package com.yeonsik.fitnessapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;
import com.yeonsik.fitnessapp.sync.SupabaseSyncManager;
import com.yeonsik.fitnessapp.ui.BaseScreen;
import com.yeonsik.fitnessapp.ui.FitnessUi;
import com.yeonsik.fitnessapp.ui.HomeScreen;
import com.yeonsik.fitnessapp.ui.RecordsScreen;
import com.yeonsik.fitnessapp.ui.RoutineEditorScreen;
import com.yeonsik.fitnessapp.ui.ScreenHost;
import com.yeonsik.fitnessapp.ui.SettingsScreen;
import com.yeonsik.fitnessapp.ui.WorkoutExerciseDetailScreen;
import com.yeonsik.fitnessapp.ui.WorkoutScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSessionScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSummaryScreen;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 얇은 진입점: 의존성 초기화, 현재 화면 상태, 하단 내비게이션, 화면 간 공유 액션만 담당한다.
 * 화면 렌더링은 ui 패키지의 각 Screen 클래스가, 공통 스타일은 FitnessUi가 담당한다.
 */
public final class MainActivity extends Activity implements ScreenHost {

    private enum Tab {
        HOME,
        WORKOUT,
        RECORDS,
        SETTINGS
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String today = LocalDate.now().toString();
    private final WorkoutSessionState sessionState = new WorkoutSessionState();

    private FitnessRepository repository;
    private ExerciseMasterRepository exerciseMasterRepository;
    private RoutineRepository routineRepository;
    private SupabaseConfigStore configStore;
    private SupabaseSyncManager syncManager;
    private SupabaseConfig supabaseConfig;

    private FitnessUi ui;
    private Map<FitnessScreen, BaseScreen> screens;
    private FitnessScreen currentScreen = FitnessScreen.HOME;

    private LinearLayout rootView;
    private ScrollView mainScrollView;
    private View sessionTopBar;
    private View sessionBottomBar;
    private LinearLayout content;
    private LinearLayout bottomNav;
    private LinearLayout homeTabArea;
    private LinearLayout workoutTabArea;
    private LinearLayout recordsTabArea;
    private LinearLayout settingsTabArea;
    private TextView homeTabLabel;
    private TextView workoutTabLabel;
    private TextView recordsTabLabel;
    private TextView settingsTabLabel;

    private boolean isManualSyncing = false;
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new SupabaseConfigStore(this);
        supabaseConfig = configStore.load();
        FitnessDatabaseHelper databaseHelper = new FitnessDatabaseHelper(this);
        repository = new FitnessRepository(databaseHelper, supabaseConfig.effectiveUserId());
        exerciseMasterRepository = new ExerciseMasterRepository(this);
        routineRepository = new RoutineRepository(databaseHelper, supabaseConfig.effectiveUserId());
        syncManager = new SupabaseSyncManager(databaseHelper);
        applySyncStatusFromConfig();

        ui = new FitnessUi(this, () -> currentScreen.inverse());
        screens = buildScreens();

        configureWindow();
        setContentView(buildRootView());
        render();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private Map<FitnessScreen, BaseScreen> buildScreens() {
        Map<FitnessScreen, BaseScreen> map = new EnumMap<>(FitnessScreen.class);
        RoutineEditorScreen routineEditor = new RoutineEditorScreen(this);
        map.put(FitnessScreen.HOME, new HomeScreen(this));
        map.put(FitnessScreen.WORKOUT, new WorkoutScreen(this));
        map.put(FitnessScreen.RECORDS, new RecordsScreen(this));
        map.put(FitnessScreen.SETTINGS, new SettingsScreen(this));
        map.put(FitnessScreen.WORKOUT_SESSION, new WorkoutSessionScreen(this));
        map.put(FitnessScreen.WORKOUT_EXERCISE_DETAIL, new WorkoutExerciseDetailScreen(this));
        map.put(FitnessScreen.WORKOUT_SUMMARY, new WorkoutSummaryScreen(this));
        map.put(FitnessScreen.ROUTINE_ADD, routineEditor);
        map.put(FitnessScreen.ROUTINE_DETAIL, routineEditor);
        map.put(FitnessScreen.WORKOUT_EXERCISE_ADD, routineEditor);
        return map;
    }

    // ── 창 / 루트 뷰 ──────────────────────────────────────────────────

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(FitnessUi.COLOR_BACKGROUND);
        window.setNavigationBarColor(FitnessUi.COLOR_BACKGROUND);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View buildRootView() {
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(FitnessUi.COLOR_BACKGROUND);

        sessionTopBar = buildSessionTopBar();
        root.addView(sessionTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        mainScrollView = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(FitnessUi.COLOR_BACKGROUND);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(20), ui.dp(40), ui.dp(20), ui.dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scrollView, scrollParams);
        sessionBottomBar = buildSessionBottomBar();
        root.addView(sessionBottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomNav = (LinearLayout) buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private View buildSessionTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4));
        bar.setBackgroundColor(FitnessUi.COLOR_SURFACE);
        bar.setVisibility(View.GONE);

        TextView back = ui.text("←", 22, FitnessUi.COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ui.borderDrawable(FitnessUi.COLOR_SURFACE, FitnessUi.COLOR_BORDER, ui.dp(999)));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> navigate(FitnessScreen.WORKOUT));
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        return bar;
    }

    private View buildSessionBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(10));
        bar.setBackgroundColor(FitnessUi.COLOR_SURFACE);
        bar.setVisibility(View.GONE);
        bar.addView(ui.buttonRow(
                ui.button("종목 추가", false, v -> openWorkoutExercisePicker()),
                ui.button("운동 완료", true, v -> finishActiveWorkout())
        ), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    // ── 하단 내비게이션 ────────────────────────────────────────────────

    private View buildBottomNav() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(FitnessUi.COLOR_SURFACE);

        View line = new View(this);
        line.setBackgroundColor(FitnessUi.COLOR_BORDER);
        wrapper.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(14));

        homeTabArea = navArea("메인", Tab.HOME);
        workoutTabArea = navArea("피트니스", Tab.WORKOUT);
        recordsTabArea = navArea("기록", Tab.RECORDS);
        settingsTabArea = navArea("설정", Tab.SETTINGS);

        nav.addView(homeTabArea, navParams());
        nav.addView(navGap());
        nav.addView(workoutTabArea, navParams());
        nav.addView(navGap());
        nav.addView(recordsTabArea, navParams());
        nav.addView(navGap());
        nav.addView(settingsTabArea, navParams());
        wrapper.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return wrapper;
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View navGap() {
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(6), ui.dp(1)));
        return gap;
    }

    private LinearLayout navArea(String label, Tab tab) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER);
        area.setMinimumHeight(ui.dp(48));
        area.setClickable(true);
        area.setFocusable(true);
        area.setOnClickListener(v -> navigate(rootScreenOf(tab)));

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(13);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, ui.dp(13), 0, ui.dp(13));

        area.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (tab == Tab.HOME) {
            homeTabLabel = textView;
        } else if (tab == Tab.WORKOUT) {
            workoutTabLabel = textView;
        } else if (tab == Tab.RECORDS) {
            recordsTabLabel = textView;
        } else {
            settingsTabLabel = textView;
        }

        return area;
    }

    private FitnessScreen rootScreenOf(Tab tab) {
        switch (tab) {
            case HOME:
                return FitnessScreen.HOME;
            case RECORDS:
                return FitnessScreen.RECORDS;
            case SETTINGS:
                return FitnessScreen.SETTINGS;
            default:
                return FitnessScreen.WORKOUT;
        }
    }

    private Tab tabOf(FitnessScreen screen) {
        switch (screen) {
            case HOME:
                return Tab.HOME;
            case RECORDS:
                return Tab.RECORDS;
            case SETTINGS:
                return Tab.SETTINGS;
            default:
                return Tab.WORKOUT;
        }
    }

    private void refreshNavState() {
        Tab activeTab = tabOf(currentScreen);
        styleNavArea(homeTabArea, homeTabLabel, activeTab == Tab.HOME);
        styleNavArea(workoutTabArea, workoutTabLabel, activeTab == Tab.WORKOUT);
        styleNavArea(recordsTabArea, recordsTabLabel, activeTab == Tab.RECORDS);
        styleNavArea(settingsTabArea, settingsTabLabel, activeTab == Tab.SETTINGS);
    }

    private void styleNavArea(LinearLayout area, TextView label, boolean active) {
        int fill = active ? FitnessUi.COLOR_PRIMARY : FitnessUi.COLOR_SURFACE;
        area.setBackground(ui.rippleDrawable(fill, fill, ui.dp(999), FitnessUi.COLOR_RIPPLE_LIGHT));
        label.setTextColor(active ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_MUTED);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ── 화면 디스패치 ─────────────────────────────────────────────────

    private void render() {
        sessionState.nextGeneration();
        content.removeAllViews();
        refreshNavState();
        boolean sessionScreen = currentScreen == FitnessScreen.WORKOUT_SESSION;
        sessionTopBar.setVisibility(sessionScreen ? View.VISIBLE : View.GONE);
        sessionBottomBar.setVisibility(sessionScreen ? View.VISIBLE : View.GONE);
        boolean fullscreenPicker = currentScreen == FitnessScreen.WORKOUT_EXERCISE_ADD
                && sessionState.activeRecordId() != null;
        applyScreenChrome(currentScreen.inverse());
        bottomNav.setVisibility(currentScreen.inverse()
                || sessionScreen
                || currentScreen == FitnessScreen.WORKOUT_SUMMARY
                || fullscreenPicker
                ? View.GONE
                : View.VISIBLE);

        BaseScreen screen = screens.get(currentScreen);
        if (screen != null) {
            screen.render();
        }
    }

    private void applyScreenChrome(boolean inverse) {
        int background = inverse ? FitnessUi.COLOR_PRIMARY : FitnessUi.COLOR_BACKGROUND;
        if (rootView != null) {
            rootView.setBackgroundColor(background);
        }
        if (mainScrollView != null) {
            mainScrollView.setBackgroundColor(background);
        }
        content.setBackgroundColor(background);
        Window window = getWindow();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        window.getDecorView().setSystemUiVisibility(inverse ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    // ── ScreenHost 구현 ───────────────────────────────────────────────

    @Override
    public Activity activity() {
        return this;
    }

    @Override
    public FitnessUi ui() {
        return ui;
    }

    @Override
    public LinearLayout content() {
        return content;
    }

    @Override
    public FitnessRepository repository() {
        return repository;
    }

    @Override
    public RoutineRepository routineRepository() {
        return routineRepository;
    }

    @Override
    public ExerciseMasterRepository exerciseMasterRepository() {
        return exerciseMasterRepository;
    }

    @Override
    public WorkoutSessionState sessionState() {
        return sessionState;
    }

    @Override
    public String today() {
        return today;
    }

    @Override
    public FitnessScreen currentScreen() {
        return currentScreen;
    }

    @Override
    public void navigate(FitnessScreen screen) {
        currentScreen = screen;
        render();
    }

    @Override
    public void rerender() {
        render();
    }

    @Override
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void openWorkoutSession(String recordId) {
        sessionState.setActiveRecordId(recordId);
        sessionState.setActiveExerciseId(null);
        navigate(FitnessScreen.WORKOUT_SESSION);
    }

    @Override
    public void openWorkoutExercisePicker() {
        String recordId = currentWorkoutRecordId();
        if (recordId == null) {
            toast("먼저 운동을 시작하세요.");
            return;
        }
        sessionState.setActiveRecordId(recordId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_ADD);
    }

    @Override
    public void finishActiveWorkout() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null) {
            toast("진행 중인 운동을 찾지 못했습니다.");
            return;
        }
        if (!repository.hasCompletedWorkout(recordId)) {
            repository.deleteSession(recordId);
            sessionState.clearIfMatches(recordId);
            toast("수행한 세트가 없어 운동을 저장하지 않았습니다.");
            navigate(FitnessScreen.WORKOUT);
            return;
        }
        repository.finishSession(recordId);
        toast("운동을 완료했습니다.");
        navigate(FitnessScreen.WORKOUT_SUMMARY);
    }

    @Override
    public void continueWorkoutIfAvailable() {
        String recordId = repository.latestInProgressSessionId();
        if (recordId == null) {
            toast("진행 중인 운동이 없습니다.");
            return;
        }
        openWorkoutSession(recordId);
    }

    @Override
    public void startRoutineWorkout(List<RoutineExerciseInstance> routineExercises) {
        if (routineExercises == null || routineExercises.isEmpty()) {
            toast("만들어진 루틴이 없습니다.");
            return;
        }

        String recordId = repository.createSessionFromRoutine(today,
                routineRepository.activeRoutineName(), routineExercises);
        toast("루틴 운동을 시작했습니다.");
        openWorkoutSession(recordId);
    }

    @Override
    public String currentWorkoutRecordId() {
        boolean onSessionScreen = currentScreen == FitnessScreen.WORKOUT_SESSION
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
        if (onSessionScreen && sessionState.activeRecordId() != null) {
            return sessionState.activeRecordId();
        }
        return repository.latestInProgressSessionId();
    }

    @Override
    public void confirmDeleteSession(String recordId) {
        new AlertDialog.Builder(this)
                .setTitle("운동 기록 삭제")
                .setMessage("이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    repository.deleteSession(recordId);
                    sessionState.clearIfMatches(recordId);
                    toast("운동 기록을 삭제했습니다.");
                    if (currentScreen == FitnessScreen.RECORDS) {
                        render();
                    } else {
                        navigate(FitnessScreen.WORKOUT);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void startEmptyWorkout() {
        openWorkoutSession(repository.createEmptySession(today));
    }

    @Override
    public void showBodyMetricDialog() {
        showBodyMetricDialog(today, null);
    }

    @Override
    public void showBodyMetricDialog(String date, String recordId) {
        FitnessRepository.BodyMetricEntry existing = recordId == null
                ? repository.bodyMetricForDate(date)
                : repository.bodyMetricEntryById(recordId);
        LinearLayout form = ui.form();
        EditText dateInput = ui.input("날짜 (YYYY-MM-DD)", date);
        EditText weight = ui.decimalInput("체중 kg", existing == null ? "" : FitnessUi.trimDouble(existing.weightKg));
        EditText memo = ui.input("메모 (선택)", "");
        if (existing != null) {
            memo.setText(existing.memo);
        }
        ui.addAll(form, dateInput, weight, memo);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "체중 기록" : "체중 수정")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    String selectedDate = FitnessUi.inputText(dateInput);
                    if (existing == null) {
                        repository.addBodyMetric(selectedDate, FitnessUi.parseDouble(weight, 0), FitnessUi.inputText(memo));
                    } else {
                        repository.updateBodyMetric(existing.id, selectedDate,
                                FitnessUi.parseDouble(weight, existing.weightKg), FitnessUi.inputText(memo));
                    }
                    render();
                })
                .setNegativeButton("취소", null);
        if (existing != null) {
            builder.setNeutralButton("삭제", (dialog, which) -> {
                repository.deleteBodyMetric(existing.id);
                render();
            });
        }
        builder.show();
    }

    @Override
    public void showMealDialog() {
        LinearLayout form = ui.form();
        EditText date = ui.input("날짜 (YYYY-MM-DD)", today);
        EditText type = ui.input("식사 구분 (breakfast/lunch/dinner/snack)", "lunch");
        EditText menu = ui.input("식단 내용", "닭가슴살 샐러드");
        EditText calories = ui.numberInput("칼로리 kcal (선택)", "");
        EditText protein = ui.decimalInput("단백질 g (선택)", "");
        ui.addAll(form, date, type, menu, calories, protein);
        new AlertDialog.Builder(this)
                .setTitle("식단 기록")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addMeal(FitnessUi.inputText(date), FitnessUi.inputText(type),
                            FitnessUi.inputText(menu), FitnessUi.optionalInt(calories),
                            FitnessUi.optionalDouble(protein));
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    @Override
    public SupabaseConfig supabaseConfig() {
        return supabaseConfig;
    }

    @Override
    public void saveSupabaseConfig(String url, String anonKey, String userId) {
        supabaseConfig = configStore.save(url, anonKey, userId);
        repository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
        routineRepository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
        applySyncStatusFromConfig();
        toast("설정을 저장했습니다.");
        render();
    }

    @Override
    public void runManualSync() {
        if (!supabaseConfig.isConfigured()) {
            toast("Supabase URL, anon key, user ID를 먼저 저장하세요.");
            return;
        }

        isManualSyncing = true;
        syncLabel = "syncing";
        syncDetail = "Supabase와 수동 동기화 중입니다.";
        render();

        executor.execute(() -> {
            try {
                SupabaseSyncManager.SyncResult result = syncManager.manualSync(supabaseConfig);
                repository.setUserId(supabaseConfig.effectiveUserId());
                routineRepository.setUserId(supabaseConfig.effectiveUserId());
                lastSyncedAt = result.syncedAt;
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "synced";
                    syncDetail = "push " + result.pushedRows + "건 · pull " + result.pulledRows + "건";
                    toast("수동 동기화를 완료했습니다.");
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "sync failed";
                    syncDetail = error.getMessage() == null ? "동기화에 실패했습니다." : error.getMessage();
                    toast("수동 동기화에 실패했습니다.");
                    render();
                });
            }
        });
    }

    private void applySyncStatusFromConfig() {
        if (supabaseConfig.isConfigured()) {
            syncLabel = lastSyncedAt.isEmpty() ? "configured" : "synced";
            syncDetail = lastSyncedAt.isEmpty()
                    ? "Supabase 설정이 저장되었습니다."
                    : "마지막 동기화 " + lastSyncedAt;
            return;
        }

        syncLabel = "local-only";
        syncDetail = "Supabase 설정이 없어 로컬 전용 모드입니다.";
    }

    @Override
    public boolean isManualSyncing() {
        return isManualSyncing;
    }

    @Override
    public String syncLabel() {
        return syncLabel;
    }

    @Override
    public String syncDetail() {
        return syncDetail;
    }

    @Override
    public String repositoryUserLabel() {
        if (!supabaseConfig.userId.isEmpty()) {
            return supabaseConfig.userId;
        }
        return SupabaseConfig.DEFAULT_USER_ID;
    }
}
