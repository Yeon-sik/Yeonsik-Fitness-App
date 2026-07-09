package com.yeonsik.fitnessapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.exercise.WeightExercise;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.sync.SupabaseSyncManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = Color.rgb(246, 246, 243);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(18, 18, 18);
    private static final int COLOR_MUTED = Color.rgb(105, 105, 105);
    private static final int COLOR_BORDER = Color.rgb(224, 224, 220);
    private static final int COLOR_PRIMARY = Color.rgb(18, 18, 18);
    private static final int COLOR_SUBTLE = Color.rgb(237, 237, 232);
    private static final int COLOR_ACTIVE_TAB = Color.rgb(18, 18, 18);

    private enum Tab {
        HOME,
        WORKOUT,
        RECORDS,
        SETTINGS
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String today = LocalDate.now().toString();

    private FitnessDatabaseHelper databaseHelper;
    private FitnessRepository repository;
    private ExerciseMasterRepository exerciseMasterRepository;
    private RoutineRepository routineRepository;
    private SupabaseConfigStore configStore;
    private SupabaseSyncManager syncManager;
    private SupabaseConfig supabaseConfig;

    private LinearLayout content;
    private LinearLayout bottomNav;
    private LinearLayout homeTabArea;
    private LinearLayout workoutTabArea;
    private LinearLayout recordsTabArea;
    private LinearLayout settingsTabArea;
    private View homeTabIndicator;
    private View workoutTabIndicator;
    private View recordsTabIndicator;
    private View settingsTabIndicator;
    private TextView homeTabLabel;
    private TextView workoutTabLabel;
    private TextView recordsTabLabel;
    private TextView settingsTabLabel;

    private Tab activeTab = Tab.HOME;
    private String selectedDate = today;
    private boolean showingRoutineAddScreen = false;
    private boolean showingRoutineDetailScreen = false;
    private boolean showingWorkoutExerciseAddScreen = false;
    private boolean showingWorkoutSessionScreen = false;
    private boolean showingWorkoutSummaryScreen = false;
    private String activeWorkoutRecordId = null;
    private boolean isManualSyncing = false;
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new SupabaseConfigStore(this);
        supabaseConfig = configStore.load();
        databaseHelper = new FitnessDatabaseHelper(this);
        repository = new FitnessRepository(databaseHelper, supabaseConfig.effectiveUserId());
        exerciseMasterRepository = new ExerciseMasterRepository(this);
        routineRepository = new RoutineRepository(databaseHelper, supabaseConfig.effectiveUserId());
        syncManager = new SupabaseSyncManager(databaseHelper);
        applySyncStatusFromConfig();
        configureWindow();
        setContentView(buildRootView());
        renderActiveTab();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View buildRootView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(44), dp(20), dp(20));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scrollView, scrollParams);
        bottomNav = (LinearLayout) buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(COLOR_SURFACE);
        nav.setPadding(0, 0, 0, dp(8));

        homeTabArea = navArea("메인", Tab.HOME);
        workoutTabArea = navArea("피트니스", Tab.WORKOUT);
        recordsTabArea = navArea("기록", Tab.RECORDS);
        settingsTabArea = navArea("설정", Tab.SETTINGS);

        nav.addView(homeTabArea, navParams());
        nav.addView(divider());
        nav.addView(workoutTabArea, navParams());
        nav.addView(divider());
        nav.addView(recordsTabArea, navParams());
        nav.addView(divider());
        nav.addView(settingsTabArea, navParams());
        return nav;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        return params;
    }

    private LinearLayout navArea(String label, Tab tab) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER);
        area.setMinimumHeight(dp(62));
        area.setClickable(true);
        area.setFocusable(true);
        area.setBackgroundColor(COLOR_SURFACE);
        area.setOnClickListener(v -> {
            activeTab = tab;
            showingRoutineAddScreen = false;
            showingRoutineDetailScreen = false;
            showingWorkoutExerciseAddScreen = false;
            showingWorkoutSessionScreen = false;
            showingWorkoutSummaryScreen = false;
            renderActiveTab();
        });

        View indicator = new View(this);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(13);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, dp(16), 0, dp(12));

        area.addView(indicator);
        area.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (tab == Tab.HOME) {
            homeTabIndicator = indicator;
            homeTabLabel = textView;
        } else if (tab == Tab.WORKOUT) {
            workoutTabIndicator = indicator;
            workoutTabLabel = textView;
        } else if (tab == Tab.RECORDS) {
            recordsTabIndicator = indicator;
            recordsTabLabel = textView;
        } else {
            settingsTabIndicator = indicator;
            settingsTabLabel = textView;
        }

        return area;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(42)));
        return divider;
    }

    private void renderActiveTab() {
        content.removeAllViews();
        refreshNavState();
        bottomNav.setVisibility(showingWorkoutSessionScreen || showingWorkoutSummaryScreen ? View.GONE : View.VISIBLE);

        if (showingWorkoutSummaryScreen) {
            renderWorkoutSummaryScreen();
            return;
        }

        if (showingWorkoutSessionScreen) {
            renderWorkoutSessionScreen();
            return;
        }

        if (activeTab == Tab.WORKOUT && showingRoutineAddScreen) {
            renderRoutineAddScreen();
            return;
        }

        if (activeTab == Tab.WORKOUT && showingRoutineDetailScreen) {
            renderRoutineDetailScreen();
            return;
        }

        if (activeTab == Tab.WORKOUT && showingWorkoutExerciseAddScreen) {
            renderWorkoutExerciseAddScreen();
            return;
        }

        if (activeTab == Tab.HOME) {
            renderHomeTab();
            return;
        }

        if (activeTab == Tab.WORKOUT) {
            renderWorkoutTab();
            return;
        }

        if (activeTab == Tab.RECORDS) {
            renderRecordsTab();
            return;
        }

        renderSettingsTab();
    }

    private void refreshNavState() {
        styleNavArea(homeTabArea, homeTabIndicator, homeTabLabel, activeTab == Tab.HOME);
        styleNavArea(workoutTabArea, workoutTabIndicator, workoutTabLabel, activeTab == Tab.WORKOUT);
        styleNavArea(recordsTabArea, recordsTabIndicator, recordsTabLabel, activeTab == Tab.RECORDS);
        styleNavArea(settingsTabArea, settingsTabIndicator, settingsTabLabel, activeTab == Tab.SETTINGS);
    }

    private void styleNavArea(LinearLayout area, View indicator, TextView label, boolean active) {
        area.setBackgroundColor(COLOR_SURFACE);
        indicator.setBackgroundColor(active ? COLOR_ACTIVE_TAB : COLOR_SURFACE);
        label.setTextColor(active ? COLOR_TEXT : COLOR_MUTED);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void renderHomeTab() {
        List<String> todaySessions = repository.sessionsForDate(today);
        String latestSessionId = repository.latestSessionId();
        List<String> latestDetails = latestSessionId == null ? Collections.emptyList() : repository.sessionDetails(latestSessionId);
        List<RoutineExerciseInstance> routineExercises = routineRepository.defaultRoutineExercises();
        boolean hasRoutine = !routineExercises.isEmpty();
        FitnessRepository.DayWorkoutMetrics todayMetrics = repository.dayWorkoutMetrics(today);

        label("MAIN");
        title("메인");
        judgmentCard(todaySessions, latestDetails, todayMetrics);

        section("루틴");
        if (hasRoutine) {
            routineStartCard(routineExercises, true);
        } else {
            emptyState("만들어진 루틴이 없습니다.", "루틴 추가");
        }

        section("빠른 접근");
        actionGrid(
                button("피트니스로 이동", false, v -> {
                    activeTab = Tab.WORKOUT;
                    renderActiveTab();
                }),
                button("진행 중 운동", false, v -> continueWorkoutIfAvailable()),
                button("오늘 체중", false, v -> {
                    activeTab = Tab.WORKOUT;
                    renderActiveTab();
                }),
                button("오늘 식사", false, v -> {
                    activeTab = Tab.WORKOUT;
                    renderActiveTab();
                })
        );

        section("오늘 운동");
        lines(todaySessions, "오늘 운동 기록이 없습니다.", "운동 시작");

        section("오늘 신체 기록");
        lines(repository.bodyMetricsForDate(today), "오늘 체중 기록이 없습니다.", "체중 기록");

        section("오늘 식단");
        lines(repository.mealsForDate(today), "오늘 식단 기록이 없습니다.", "식단 기록");
    }

    private void renderWorkoutTab() {
        List<RoutineExerciseInstance> routineExercises = routineRepository.defaultRoutineExercises();
        String latestSessionId = repository.latestSessionId();
        List<String> latestDetails = latestSessionId == null ? Collections.emptyList() : repository.sessionDetails(latestSessionId);
        boolean hasRoutine = !routineExercises.isEmpty();

        label("FITNESS");
        title("피트니스");

        section("오늘의 몸 상태");
        actionGrid(
                button("체중 기록", false, v -> showBodyMetricDialog()),
                button("식사 기록", false, v -> showMealDialog()),
                button("진행 중 운동", false, v -> continueWorkoutIfAvailable()),
                button("기록 보기", false, v -> {
                    activeTab = Tab.RECORDS;
                    renderActiveTab();
                })
        );
        lines(repository.bodyMetricsForDate(today), "오늘 체중 기록이 없습니다.", null);
        lines(repository.mealsForDate(today), "오늘 식사 기록이 없습니다.", null);

        section("운동 시작");
        content.addView(button("빈 운동 시작", false, v -> showSessionDialog()), fullWidthParams(dp(0)));

        section("루틴 관리");
        content.addView(button("루틴 추가", false, v -> showRoutineExercisePicker()), fullWidthParams(dp(0)));
        if (routineExercises.isEmpty()) {
            emptyState("만들어진 루틴이 없습니다.", "루틴 추가");
        } else {
            routineStartCard(routineExercises, true);
        }

        section("현재 운동");
        if (latestSessionId == null) {
            emptyState("운동을 시작하면 여기서 수정할 수 있습니다.", "운동 시작");
        } else {
            lines(latestDetails, "아직 종목이 없습니다.", "종목 추가");
            row(
                    button("종목 추가", false, v -> showWorkoutExercisePicker()),
                    button("세트 추가", false, v -> showSetDialog()),
                    dp(8)
            );
        }
    }

    private void renderRecordsTab() {
        label("RECORDS");
        title("기록 확인");

        CalendarView calendarView = new CalendarView(this);
        calendarView.setDate(toEpochMillis(selectedDate), false, true);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth).toString();
            renderActiveTab();
        });
        content.addView(calendarView, fullWidthParams(dp(8)));

        section("운동");
        renderSessionRecords(selectedDate);

        section("체중");
        lines(repository.bodyMetricsForDate(selectedDate), "선택한 날짜의 체중 기록이 없습니다.", null);

        section("식단");
        lines(repository.mealsForDate(selectedDate), "선택한 날짜의 식단 기록이 없습니다.", null);
    }

    private void renderSettingsTab() {
        label("SETTINGS");
        title("설정");

        LinearLayout statusCard = card();
        cardHeader(statusCard, "연결 상태", null);
        statusCard.addView(keyValue("Supabase", supabaseConfig.isConfigured() ? "configured" : "local-only"));
        statusCard.addView(keyValue("동기화", syncLabel));
        statusCard.addView(keyValue("사용자", repositoryUserLabel()));
        Button syncButton = button(isManualSyncing ? "수동 동기화 중" : "수동 동기화", false, v -> runManualSync());
        syncButton.setEnabled(!isManualSyncing);
        LinearLayout.LayoutParams syncParams = fullWidthParams(dp(0));
        syncParams.setMargins(0, dp(12), 0, 0);
        statusCard.addView(syncButton, syncParams);
        if (!syncDetail.isEmpty()) {
            TextView detail = text(syncDetail, 13, COLOR_MUTED, false);
            detail.setPadding(0, dp(8), 0, 0);
            statusCard.addView(detail);
        }
        content.addView(statusCard);

        LinearLayout configCard = card();
        cardHeader(configCard, "Supabase config", supabaseConfig.sourceLabel);
        EditText supabaseUrlInput = input("Supabase URL", supabaseConfig.supabaseUrl);
        EditText supabaseAnonInput = input("Anon key", supabaseConfig.supabaseAnonKey);
        supabaseAnonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText userIdInput = input("User ID", supabaseConfig.userId);
        configCard.addView(fieldLabel("Supabase URL"));
        configCard.addView(supabaseUrlInput, fullWidthParams(dp(8)));
        configCard.addView(fieldLabel("Anon key"));
        configCard.addView(supabaseAnonInput, fullWidthParams(dp(8)));
        configCard.addView(fieldLabel("User ID"));
        configCard.addView(userIdInput, fullWidthParams(dp(8)));
        Button saveButton = button("Save config", true, v -> saveSupabaseConfig(
                supabaseUrlInput,
                supabaseAnonInput,
                userIdInput
        ));
        configCard.addView(saveButton, fullWidthParams(dp(12)));
        content.addView(configCard);
    }

    private void saveSupabaseConfig(EditText urlInput, EditText anonInput, EditText userIdInput) {
        supabaseConfig = configStore.save(
                text(urlInput),
                text(anonInput),
                text(userIdInput)
        );
        repository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
        routineRepository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
        applySyncStatusFromConfig();
        toast("설정을 저장했습니다.");
        renderActiveTab();
    }

    private void runManualSync() {
        if (!supabaseConfig.isConfigured()) {
            toast("Supabase URL, anon key, user ID를 먼저 저장하세요.");
            return;
        }

        isManualSyncing = true;
        syncLabel = "syncing";
        syncDetail = "Supabase와 수동 동기화 중입니다.";
        renderActiveTab();

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
                    renderActiveTab();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "sync failed";
                    syncDetail = error.getMessage() == null ? "동기화에 실패했습니다." : error.getMessage();
                    toast("수동 동기화에 실패했습니다.");
                    renderActiveTab();
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

    private String repositoryUserLabel() {
        if (!supabaseConfig.userId.isEmpty()) {
            return supabaseConfig.userId;
        }
        return SupabaseConfig.DEFAULT_USER_ID;
    }

    private void renderWorkoutSessionScreen() {
        String recordId = activeWorkoutRecordId;
        List<String> details = recordId == null ? Collections.emptyList() : repository.sessionDetails(recordId);
        FitnessRepository.SessionMetrics metrics = recordId == null ? new FitnessRepository.SessionMetrics() : repository.sessionMetrics(recordId);

        label("WORKOUT SESSION");
        title("운동 수행");

        LinearLayout status = card();
        status.addView(text("집중 모드", 18, COLOR_TEXT, true));
        status.addView(text("총 볼륨 " + trimDouble(metrics.totalVolumeKg) + "kg · 완료 세트 " + metrics.setCount + "개", 14, COLOR_MUTED, false));
        content.addView(status);

        actionGrid(
                button("종목 추가", false, v -> showWorkoutExercisePicker()),
                button("세트 추가", false, v -> showSetDialog()),
                button("운동 완료", true, v -> finishActiveWorkout()),
                button("임시 저장 후 나가기", false, v -> leaveWorkoutSession())
        );

        section("운동 구성");
        lines(details, "아직 종목이 없습니다.", "종목 추가");
    }

    private void renderWorkoutSummaryScreen() {
        String recordId = activeWorkoutRecordId;
        List<String> details = recordId == null ? Collections.emptyList() : repository.sessionDetails(recordId);
        FitnessRepository.SessionMetrics metrics = recordId == null ? new FitnessRepository.SessionMetrics() : repository.sessionMetrics(recordId);

        label("SUMMARY");
        title("운동 요약");

        LinearLayout summary = card();
        summary.addView(keyValue("총 볼륨", trimDouble(metrics.totalVolumeKg) + "kg"));
        summary.addView(keyValue("완료 세트", metrics.setCount + "개"));
        content.addView(summary);

        actionGrid(
                button("기록 보기", true, v -> {
                    showingWorkoutSummaryScreen = false;
                    activeTab = Tab.RECORDS;
                    renderActiveTab();
                }),
                button("피트니스로 돌아가기", false, v -> {
                    showingWorkoutSummaryScreen = false;
                    activeTab = Tab.WORKOUT;
                    renderActiveTab();
                }),
                button("기록 수정", false, v -> {
                    showingWorkoutSummaryScreen = false;
                    showingWorkoutSessionScreen = true;
                    renderActiveTab();
                }),
                button("메인", false, v -> {
                    showingWorkoutSummaryScreen = false;
                    activeTab = Tab.HOME;
                    renderActiveTab();
                })
        );

        section("수행 내역");
        lines(details, "세부 운동 기록이 없습니다.", null);
    }

    private void finishActiveWorkout() {
        if (activeWorkoutRecordId == null) {
            toast("진행 중인 운동을 찾지 못했습니다.");
            return;
        }
        repository.finishSession(activeWorkoutRecordId);
        toast("운동을 완료했습니다.");
        showingWorkoutSessionScreen = false;
        showingWorkoutSummaryScreen = true;
        renderActiveTab();
    }

    private void leaveWorkoutSession() {
        showingWorkoutSessionScreen = false;
        activeTab = Tab.WORKOUT;
        renderActiveTab();
    }

    private void showSessionDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText title = input("운동 이름", "근력 운동");
        EditText startedAt = input("시작 시간 (선택)", "");
        EditText endedAt = input("종료 시간 (선택)", "");
        EditText memo = input("메모 (선택)", "");
        addAll(form, date, title, startedAt, endedAt, memo);
        new AlertDialog.Builder(this)
                .setTitle("운동 시작")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    openWorkoutSession(repository.createSession(text(date), text(title), "strength", text(memo), text(startedAt), text(endedAt)));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void startRoutineWorkout(List<RoutineExerciseInstance> routineExercises) {
        if (routineExercises == null || routineExercises.isEmpty()) {
            toast("만들어진 루틴이 없습니다.");
            return;
        }

        String recordId = repository.createSessionFromRoutine(today, "나만의 루틴", routineExercises);
        toast("루틴 운동을 시작했습니다.");
        openWorkoutSession(recordId);
    }

    private void openWorkoutSession(String recordId) {
        activeWorkoutRecordId = recordId;
        showingWorkoutSummaryScreen = false;
        showingWorkoutSessionScreen = true;
        showingRoutineAddScreen = false;
        renderActiveTab();
    }

    private void continueWorkoutIfAvailable() {
        String recordId = repository.latestInProgressSessionId();
        if (recordId == null) {
            toast("진행 중인 운동이 없습니다.");
            return;
        }
        openWorkoutSession(recordId);
    }

    private String currentWorkoutRecordId() {
        if (showingWorkoutSessionScreen && activeWorkoutRecordId != null) {
            return activeWorkoutRecordId;
        }
        return repository.latestSessionId();
    }

    private void showExerciseDialog() {
        String sessionId = currentWorkoutRecordId();
        if (sessionId == null) {
            toast("먼저 운동을 시작하세요.");
            return;
        }

        LinearLayout form = form();
        EditText name = input("운동 종목", "벤치프레스");
        EditText category = input("부위 (가슴/등/하체/이두/삼두/어깨/복근)", "가슴");
        EditText order = numberInput("순서", "1");
        EditText memo = input("메모 (선택)", "");
        addAll(form, name, category, order, memo);
        new AlertDialog.Builder(this)
                .setTitle("종목 추가")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addExercise(sessionId, text(name), text(category), parseInt(order, 1), text(memo));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showRoutineExercisePicker() {
        activeTab = Tab.WORKOUT;
        showingRoutineAddScreen = true;
        showingRoutineDetailScreen = false;
        showingWorkoutExerciseAddScreen = false;
        renderActiveTab();
    }

    private void showRoutineDetailScreen() {
        activeTab = Tab.WORKOUT;
        showingRoutineAddScreen = false;
        showingRoutineDetailScreen = true;
        showingWorkoutExerciseAddScreen = false;
        renderActiveTab();
    }

    private void showWorkoutExercisePicker() {
        String recordId = currentWorkoutRecordId();
        if (recordId == null) {
            toast("먼저 운동을 시작하세요.");
            return;
        }

        activeTab = Tab.WORKOUT;
        activeWorkoutRecordId = recordId;
        showingRoutineAddScreen = false;
        showingRoutineDetailScreen = false;
        showingWorkoutExerciseAddScreen = true;
        renderActiveTab();
    }

    private void renderRoutineDetailScreen() {
        List<RoutineExerciseInstance> routineExercises = routineRepository.defaultRoutineExercises();

        label("ROUTINE");
        title("나만의 루틴");
        content.addView(button("피트니스로 돌아가기", false, v -> {
            showingRoutineDetailScreen = false;
            renderActiveTab();
        }), fullWidthParams(dp(0)));

        LinearLayout summary = card();
        summary.addView(text("루틴", 18, COLOR_TEXT, true));
        summary.addView(text(routineExercises.size() + "개 운동 종목", 13, COLOR_MUTED, false));
        content.addView(summary);

        section("세부 운동 종목");
        content.addView(button("운동 종목 추가", false, v -> showRoutineExercisePicker()), fullWidthParams(dp(0)));
        if (routineExercises.isEmpty()) {
            emptyState("루틴에 추가된 운동 종목이 없습니다.", "운동 종목 추가");
            return;
        }

        for (RoutineExerciseInstance exercise : routineExercises) {
            routineExerciseCard(exercise);
        }
    }

    private void renderRoutineAddScreen() {
        BodyPart[] selectedBodyPart = new BodyPart[]{BodyPart.CHEST};
        EquipmentType[] selectedEquipment = new EquipmentType[]{null};
        List<String> selectedExerciseIds = new ArrayList<>();
        List<WeightExercise> selectedExercises = new ArrayList<>();
        List<Button> bodyButtons = new ArrayList<>();
        Runnable[] refresh = new Runnable[1];

        label("ROUTINE");
        title("루틴 추가");
        content.addView(button("피트니스로 돌아가기", false, v -> {
            showingRoutineAddScreen = false;
            renderActiveTab();
        }), fullWidthParams(dp(0)));

        TextView selectedCount = text("선택한 운동 0개", 14, COLOR_MUTED, false);
        selectedCount.setPadding(0, dp(12), 0, 0);
        content.addView(selectedCount);

        EditText searchInput = input("운동명, 영문명, 장비 검색", "");
        content.addView(searchInput, fullWidthParams(dp(10)));

        LinearLayout bodyRowTop = pickerRow();
        LinearLayout bodyRowBottom = pickerRow();
        BodyPart[] bodyParts = BodyPart.values();
        for (int index = 0; index < bodyParts.length; index++) {
            BodyPart bodyPart = bodyParts[index];
            Button filterButton = filterButton(bodyPart.labelKo());
            filterButton.setOnClickListener(v -> {
                selectedBodyPart[0] = bodyPart;
                refresh[0].run();
            });
            bodyButtons.add(filterButton);
            if (index < 3) {
                bodyRowTop.addView(filterButton, pickerCellParams(index == 0));
            } else {
                bodyRowBottom.addView(filterButton, pickerCellParams(index == 3));
            }
        }
        content.addView(bodyRowTop, fullWidthParams(dp(10)));
        content.addView(bodyRowBottom, fullWidthParams(dp(6)));

        Button equipmentButton = filterButton("장비: 전체");
        equipmentButton.setOnClickListener(v -> showEquipmentFilterDialog(selectedEquipment, equipmentButton, refresh[0]));
        content.addView(equipmentButton, fullWidthParams(dp(10)));

        Button addButton = button("선택한 운동 추가", true, v -> {
            if (selectedExercises.isEmpty()) {
                toast("추가할 운동을 선택하세요.");
                return;
            }

            for (WeightExercise exercise : selectedExercises) {
                routineRepository.addToDefaultRoutine(ExerciseMasterAdapter.toRoutineExercise(exercise));
            }
            toast(selectedExercises.size() + "개 운동을 루틴에 추가했습니다.");
            showingRoutineAddScreen = false;
            showingRoutineDetailScreen = true;
            renderActiveTab();
        });
        addButton.setEnabled(false);
        content.addView(addButton, fullWidthParams(dp(10)));

        LinearLayout listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);

        refresh[0] = () -> {
            for (int index = 0; index < bodyButtons.size(); index++) {
                styleFilterButton(bodyButtons.get(index), bodyParts[index] == selectedBodyPart[0]);
            }
            selectedCount.setText("선택한 운동 " + selectedExercises.size() + "개");
            addButton.setText(selectedExercises.isEmpty()
                    ? "선택한 운동 추가"
                    : "선택한 운동 " + selectedExercises.size() + "개 추가");
            addButton.setEnabled(!selectedExercises.isEmpty());
            renderRoutineAddList(
                    listArea,
                    filteredWeightExercises(text(searchInput), selectedBodyPart[0], selectedEquipment[0]),
                    selectedExerciseIds,
                    selectedExercises,
                    refresh[0]
            );
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        section("운동 목록");
        content.addView(listArea, fullWidthParams(dp(10)));
        refresh[0].run();
    }

    private void renderWorkoutExerciseAddScreen() {
        String recordId = activeWorkoutRecordId == null ? currentWorkoutRecordId() : activeWorkoutRecordId;
        BodyPart[] selectedBodyPart = new BodyPart[]{BodyPart.CHEST};
        EquipmentType[] selectedEquipment = new EquipmentType[]{null};
        List<String> selectedExerciseIds = new ArrayList<>();
        List<WeightExercise> selectedExercises = new ArrayList<>();
        List<Button> bodyButtons = new ArrayList<>();
        Runnable[] refresh = new Runnable[1];

        label("WORKOUT");
        title("운동 종목 추가");
        content.addView(button("운동으로 돌아가기", false, v -> {
            showingWorkoutExerciseAddScreen = false;
            if (activeWorkoutRecordId != null) {
                showingWorkoutSessionScreen = true;
            }
            renderActiveTab();
        }), fullWidthParams(dp(0)));

        TextView selectedCount = text("선택한 운동 0개", 14, COLOR_MUTED, false);
        selectedCount.setPadding(0, dp(12), 0, 0);
        content.addView(selectedCount);

        EditText searchInput = input("운동명, 영문명, 장비 검색", "");
        content.addView(searchInput, fullWidthParams(dp(10)));

        LinearLayout bodyRowTop = pickerRow();
        LinearLayout bodyRowBottom = pickerRow();
        BodyPart[] bodyParts = BodyPart.values();
        for (int index = 0; index < bodyParts.length; index++) {
            BodyPart bodyPart = bodyParts[index];
            Button filterButton = filterButton(bodyPart.labelKo());
            filterButton.setOnClickListener(v -> {
                selectedBodyPart[0] = bodyPart;
                refresh[0].run();
            });
            bodyButtons.add(filterButton);
            if (index < 3) {
                bodyRowTop.addView(filterButton, pickerCellParams(index == 0));
            } else {
                bodyRowBottom.addView(filterButton, pickerCellParams(index == 3));
            }
        }
        content.addView(bodyRowTop, fullWidthParams(dp(10)));
        content.addView(bodyRowBottom, fullWidthParams(dp(6)));

        Button equipmentButton = filterButton("장비: 전체");
        equipmentButton.setOnClickListener(v -> showEquipmentFilterDialog(selectedEquipment, equipmentButton, refresh[0]));
        content.addView(equipmentButton, fullWidthParams(dp(10)));

        Button addButton = button("선택한 종목 추가", true, v -> {
            if (recordId == null) {
                toast("먼저 운동을 시작하세요.");
                return;
            }
            if (selectedExercises.isEmpty()) {
                toast("추가할 운동을 선택하세요.");
                return;
            }

            for (WeightExercise exercise : selectedExercises) {
                repository.addExerciseFromMaster(recordId, ExerciseMasterAdapter.toRoutineExercise(exercise));
            }
            toast(selectedExercises.size() + "개 종목을 운동에 추가했습니다.");
            showingWorkoutExerciseAddScreen = false;
            showingWorkoutSessionScreen = activeWorkoutRecordId != null;
            renderActiveTab();
        });
        addButton.setEnabled(false);
        content.addView(addButton, fullWidthParams(dp(10)));

        LinearLayout listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);

        refresh[0] = () -> {
            for (int index = 0; index < bodyButtons.size(); index++) {
                styleFilterButton(bodyButtons.get(index), bodyParts[index] == selectedBodyPart[0]);
            }
            selectedCount.setText("선택한 운동 " + selectedExercises.size() + "개");
            addButton.setText(selectedExercises.isEmpty()
                    ? "선택한 종목 추가"
                    : "선택한 종목 " + selectedExercises.size() + "개 추가");
            addButton.setEnabled(!selectedExercises.isEmpty());
            renderRoutineAddList(
                    listArea,
                    filteredWeightExercises(text(searchInput), selectedBodyPart[0], selectedEquipment[0]),
                    selectedExerciseIds,
                    selectedExercises,
                    refresh[0]
            );
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        section("운동 목록");
        content.addView(listArea, fullWidthParams(dp(10)));
        refresh[0].run();
    }

    private void showEquipmentFilterDialog(EquipmentType[] selectedEquipment, Button equipmentButton, Runnable refresh) {
        String[] labels = new String[EquipmentType.values().length + 1];
        labels[0] = "전체";
        for (int index = 0; index < EquipmentType.values().length; index++) {
            labels[index + 1] = EquipmentType.values()[index].labelKo();
        }

        new AlertDialog.Builder(this)
                .setTitle("장비 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedEquipment[0] = which == 0 ? null : EquipmentType.values()[which - 1];
                    equipmentButton.setText("장비: " + (selectedEquipment[0] == null ? "전체" : selectedEquipment[0].labelKo()));
                    refresh.run();
                })
                .show();
    }

    private void renderRoutineAddList(
            LinearLayout listArea,
            List<WeightExercise> exercises,
            List<String> selectedExerciseIds,
            List<WeightExercise> selectedExercises,
            Runnable refresh
    ) {
        listArea.removeAllViews();
        if (exercises.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("조건에 맞는 운동이 없습니다.", 15, COLOR_MUTED, false));
            listArea.addView(empty);
            return;
        }

        for (WeightExercise exercise : exercises) {
            LinearLayout card = card();
            boolean selected = selectedExerciseIds.contains(exercise.id);
            card.setBackground(borderDrawable(COLOR_SURFACE, selected ? COLOR_PRIMARY : COLOR_BORDER, dp(8)));
            TextView name = text(exercise.displayName(), 17, COLOR_TEXT, true);
            TextView meta = text(exercise.equipmentNameKo + " · " + exercise.primarySubPartNameKo, 13, COLOR_MUTED, false);
            TextView detail = text(displayExerciseMechanic(exercise) + " · " + displayRecordType(exercise)
                    + (selected ? " · 선택됨" : ""), 13, COLOR_MUTED, false);
            meta.setPadding(0, dp(6), 0, 0);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(name);
            card.addView(meta);
            card.addView(detail);
            card.setClickable(true);
            card.setOnClickListener(v -> {
                if (selectedExerciseIds.contains(exercise.id)) {
                    removeSelectedExercise(exercise.id, selectedExerciseIds, selectedExercises);
                } else {
                    selectedExerciseIds.add(exercise.id);
                    selectedExercises.add(exercise);
                }
                refresh.run();
            });
            listArea.addView(card);
        }
    }

    private void removeSelectedExercise(String exerciseId, List<String> selectedExerciseIds, List<WeightExercise> selectedExercises) {
        selectedExerciseIds.remove(exerciseId);
        for (int index = selectedExercises.size() - 1; index >= 0; index--) {
            if (exerciseId.equals(selectedExercises.get(index).id)) {
                selectedExercises.remove(index);
                return;
            }
        }
    }

    private void showSetDialog() {
        String sessionId = currentWorkoutRecordId();
        List<FitnessRepository.SessionExerciseEntry> exercises = sessionId == null
                ? Collections.emptyList()
                : repository.sessionExerciseEntries(sessionId);
        if (sessionId == null || exercises.isEmpty()) {
            toast("운동과 종목을 먼저 추가하세요.");
            return;
        }

        LinearLayout form = form();
        int[] selectedExerciseIndex = new int[]{0};
        Button exerciseButton = button(exercises.get(0).label(), false, null);
        exerciseButton.setOnClickListener(v -> showSessionExerciseDialog(exercises, selectedExerciseIndex, exerciseButton));
        EditText setCount = numberInput("세트 수", "1");
        EditText weight = decimalInput("무게 kg", "60");
        EditText reps = numberInput("횟수", "10");
        TextView volumePreview = text("예상 총 볼륨 600kg", 13, COLOR_MUTED, false);
        CheckBox completed = new CheckBox(this);
        completed.setText("완료");
        completed.setTextColor(COLOR_TEXT);
        completed.setTextSize(16);
        completed.setMinHeight(dp(52));
        completed.setChecked(true);
        addAll(form, exerciseButton, setCount, weight, reps, volumePreview, completed);

        TextWatcher volumeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int nextSetCount = Math.max(1, parseInt(setCount, 1));
                double nextWeight = parseDouble(weight, 0);
                int nextReps = Math.max(0, parseInt(reps, 0));
                double nextVolume = nextSetCount * nextWeight * nextReps;
                volumePreview.setText("예상 총 볼륨 " + trimDouble(nextVolume) + "kg");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        setCount.addTextChangedListener(volumeWatcher);
        weight.addTextChangedListener(volumeWatcher);
        reps.addTextChangedListener(volumeWatcher);

        new AlertDialog.Builder(this)
                .setTitle("세트 추가")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    FitnessRepository.SessionExerciseEntry exercise = exercises.get(selectedExerciseIndex[0]);
                    if (exercise == null) {
                        toast("운동 종목을 찾지 못했습니다.");
                        return;
                    }
                    int nextSetCount = Math.max(1, parseInt(setCount, 1));
                    int nextSetIndex = nextSetIndex(exercise.id);
                    double nextWeight = parseDouble(weight, 0);
                    int nextReps = Math.max(0, parseInt(reps, 0));
                    for (int index = 0; index < nextSetCount; index++) {
                        repository.addSet(sessionId, exercise.id, nextSetIndex + index,
                                nextWeight, nextReps, completed.isChecked());
                    }
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSessionExerciseDialog(
            List<FitnessRepository.SessionExerciseEntry> exercises,
            int[] selectedExerciseIndex,
            Button exerciseButton
    ) {
        String[] labels = new String[exercises.size()];
        for (int index = 0; index < exercises.size(); index++) {
            labels[index] = exercises.get(index).label();
        }

        new AlertDialog.Builder(this)
                .setTitle("운동 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedExerciseIndex[0] = which;
                    exerciseButton.setText(labels[which]);
                })
                .show();
    }

    private void showBodyMetricDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText weight = decimalInput("체중 kg", "73.0");
        EditText memo = input("메모 (선택)", "");
        addAll(form, date, weight, memo);
        new AlertDialog.Builder(this)
                .setTitle("체중 기록")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addBodyMetric(text(date), parseDouble(weight, 0), text(memo));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showMealDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText type = input("식사 구분 (breakfast/lunch/dinner/snack)", "lunch");
        EditText menu = input("식단 내용", "닭가슴살 샐러드");
        EditText calories = numberInput("칼로리 kcal (선택)", "");
        EditText protein = decimalInput("단백질 g (선택)", "");
        addAll(form, date, type, menu, calories, protein);
        new AlertDialog.Builder(this)
                .setTitle("식단 기록")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addMeal(text(date), text(type), text(menu), optionalInt(calories), optionalDouble(protein));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private int nextSetIndex(String workoutExerciseId) {
        android.database.Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(set_index), 0) + 1 FROM workout_sets WHERE workout_exercise_id = ? AND deleted_at IS NULL",
                new String[]{workoutExerciseId});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        } finally {
            cursor.close();
        }
    }

    private void judgmentCard(List<String> todaySessions, List<String> latestDetails, FitnessRepository.DayWorkoutMetrics todayMetrics) {
        LinearLayout card = card();
        TextView status = text(todaySessions.isEmpty() ? "오늘 운동 미기록" : "오늘 운동 기록됨", 22, COLOR_TEXT, true);
        TextView summary = text(todaySessions.isEmpty()
                ? "지금 필요한 행동은 운동 세션 시작입니다."
                : todaySessions.size() + "개 세션이 기록되었습니다.", 15, COLOR_MUTED, false);
        TextView totals = text(todayMetrics.sessionCount == 0
                ? "총 볼륨 0kg · 총 시간 미기록"
                : "총 볼륨 " + trimDouble(todayMetrics.totalVolumeKg) + "kg · 총 시간 " + formatDuration(todayMetrics.totalDurationSeconds),
                13, COLOR_MUTED, false);
        TextView metric = text(latestDetails.isEmpty()
                ? "세트 기록 없음"
                : todayMetrics.totalSetCount + "개 세트 기록", 13, COLOR_MUTED, false);
        card.addView(status);
        card.addView(summary);
        card.addView(totals);
        card.addView(metric);
        content.addView(card);
    }

    private void lines(List<String> rows, String empty, String emptyAction) {
        if (rows.isEmpty()) {
            emptyState(empty, emptyAction);
            return;
        }

        for (String row : rows) {
            recordCard(row);
        }
    }

    private void recordCard(String rowText) {
        LinearLayout card = card();
        TextView row = text(rowText, 15, COLOR_TEXT, false);
        row.setLineSpacing(dp(2), 1f);
        card.addView(row);
        content.addView(card);
    }

    private void renderSessionRecords(String date) {
        List<FitnessRepository.SessionRecordEntry> sessions = repository.sessionEntriesForDate(date);
        if (sessions.isEmpty()) {
            emptyState("선택한 날짜의 운동 기록이 없습니다.", null);
            return;
        }

        for (FitnessRepository.SessionRecordEntry session : sessions) {
            LinearLayout card = card();
            TextView summary = text(session.summary, 15, COLOR_TEXT, true);
            summary.setLineSpacing(dp(2), 1f);
            card.addView(summary);

            FitnessRepository.SessionMetrics sessionMetrics = repository.sessionMetrics(session.id);
            TextView metrics = text(
                    "총 볼륨 " + trimDouble(sessionMetrics.totalVolumeKg) + "kg · 완료 세트 " + sessionMetrics.setCount + "개",
                    13,
                    COLOR_MUTED,
                    false
            );
            metrics.setPadding(0, dp(8), 0, 0);
            card.addView(metrics);

            List<String> details = repository.sessionDetails(session.id);
            if (details.isEmpty()) {
                TextView empty = text("세부 운동 기록이 없습니다.", 13, COLOR_MUTED, false);
                empty.setPadding(0, dp(8), 0, 0);
                card.addView(empty);
            } else {
                for (String detail : details) {
                    TextView line = text(detail, detail.startsWith("   ") ? 13 : 14, detail.startsWith("   ") ? COLOR_MUTED : COLOR_TEXT, false);
                    line.setPadding(0, dp(6), 0, 0);
                    line.setLineSpacing(dp(2), 1f);
                    card.addView(line);
                }
            }

            Button deleteButton = button("기록 삭제", false, v -> confirmDeleteSession(session.id));
            card.addView(deleteButton, fullWidthParams(dp(12)));
            content.addView(card);
        }
    }

    private void confirmDeleteSession(String recordId) {
        new AlertDialog.Builder(this)
                .setTitle("운동 기록 삭제")
                .setMessage("이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    repository.deleteSession(recordId);
                    if (recordId != null && recordId.equals(activeWorkoutRecordId)) {
                        activeWorkoutRecordId = null;
                        showingWorkoutSessionScreen = false;
                        showingWorkoutSummaryScreen = false;
                    }
                    toast("운동 기록을 삭제했습니다.");
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void routineStartCard(List<RoutineExerciseInstance> exercises, boolean showDetailAction) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> startRoutineWorkout(exercises));
        TextView name = text("나만의 루틴", 17, COLOR_TEXT, true);
        TextView meta = text(exercises.size() + "개 운동 종목", 13, COLOR_MUTED, false);
        TextView action = text("탭해서 운동 시작", 13, COLOR_MUTED, false);
        meta.setPadding(0, dp(6), 0, 0);
        action.setPadding(0, dp(4), 0, 0);
        card.addView(name);
        card.addView(meta);
        card.addView(action);
        if (showDetailAction) {
            Button detailButton = button("세부 보기", false, v -> showRoutineDetailScreen());
            card.addView(detailButton, fullWidthParams(dp(12)));
        }
        content.addView(card);
    }

    private void routineExerciseCard(RoutineExerciseInstance exercise) {
        LinearLayout card = card();
        TextView name = text(exercise.order + ". " + exercise.nameKo, 16, COLOR_TEXT, true);
        TextView meta = text(exercise.uiPart + " · " + exercise.primarySubPart + " · " + exercise.equipment, 13, COLOR_MUTED, false);
        TextView record = text("기록: " + exercise.recordType, 13, COLOR_MUTED, false);
        meta.setPadding(0, dp(6), 0, 0);
        record.setPadding(0, dp(4), 0, 0);
        card.addView(name);
        card.addView(meta);
        card.addView(record);
        content.addView(card);
    }

    private void emptyState(String message, String action) {
        LinearLayout card = card();
        card.addView(text(message, 15, COLOR_MUTED, false));
        if (action != null) {
            TextView hint = text(action + " 버튼을 사용하세요.", 13, COLOR_MUTED, false);
            hint.setPadding(0, dp(6), 0, 0);
            card.addView(hint);
        }
        content.addView(card);
    }

    private void cardHeader(LinearLayout card, String title, String meta) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(title, 18, COLOR_TEXT, true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (meta != null) {
            TextView metaView = text(meta, 12, COLOR_MUTED, false);
            header.addView(metaView);
        }

        card.addView(header);
    }

    private View keyValue(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView keyView = text(key, 14, COLOR_MUTED, false);
        TextView valueView = text(value, 14, COLOR_TEXT, true);
        valueView.setGravity(Gravity.END);

        row.addView(keyView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView);
        return row;
    }

    private void cardRow(String key, String value) {
        LinearLayout card = card();
        card.addView(keyValue(key, value));
        content.addView(card);
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 13, COLOR_MUTED, false);
        label.setPadding(0, dp(12), 0, dp(6));
        return label;
    }

    private Button button(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : COLOR_TEXT);
        button.setMinHeight(dp(54));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(borderDrawable(primary ? COLOR_PRIMARY : COLOR_SUBTLE,
                primary ? COLOR_PRIMARY : COLOR_BORDER, dp(8)));
        button.setOnClickListener(listener);
        return button;
    }

    private Button filterButton(String text) {
        Button button = button(text, false, null);
        button.setTextSize(13);
        button.setMinHeight(dp(44));
        return button;
    }

    private void styleFilterButton(Button button, boolean active) {
        button.setTextColor(active ? Color.WHITE : COLOR_TEXT);
        button.setBackground(borderDrawable(active ? COLOR_PRIMARY : COLOR_SUBTLE,
                active ? COLOR_PRIMARY : COLOR_BORDER, dp(8)));
    }

    private LinearLayout pickerRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams pickerCellParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(first ? 0 : dp(6), 0, 0, 0);
        return params;
    }

    private List<WeightExercise> filteredWeightExercises(String query, BodyPart bodyPart, EquipmentType equipmentType) {
        List<WeightExercise> source = exerciseMasterRepository.searchExercises(query);
        List<WeightExercise> results = new ArrayList<>();
        for (WeightExercise exercise : source) {
            if (bodyPart != null && exercise.bodyPart != bodyPart) {
                continue;
            }
            if (equipmentType != null && exercise.equipmentType != equipmentType) {
                continue;
            }
            results.add(exercise);
        }
        return results;
    }

    private String displayExerciseMechanic(WeightExercise exercise) {
        if (exercise.mechanicTypeNameKo != null && !exercise.mechanicTypeNameKo.isEmpty()) {
            return exercise.mechanicTypeNameKo;
        }
        return exercise.mechanicType == null || exercise.mechanicType.isEmpty() ? "유형 없음" : exercise.mechanicType;
    }

    private String displayRecordType(WeightExercise exercise) {
        if (exercise.recordTypeNameKo != null && !exercise.recordTypeNameKo.isEmpty()) {
            return exercise.recordTypeNameKo;
        }
        return exercise.recordType == null || exercise.recordType.isEmpty() ? "기록 방식 없음" : exercise.recordType;
    }

    private String displaySubParts(WeightExercise exercise) {
        List<String> parts = new ArrayList<>();
        if (exercise.primarySubPartNameKo != null && !exercise.primarySubPartNameKo.isEmpty()) {
            parts.add(exercise.primarySubPartNameKo);
        }
        for (String subPart : exercise.secondarySubPartNamesKo) {
            if (subPart != null && !subPart.isEmpty() && !parts.contains(subPart)) {
                parts.add(subPart);
            }
        }
        if (parts.isEmpty()) {
            return "세부 부위 없음";
        }
        return joinLabels(parts);
    }

    private String joinLabels(List<String> labels) {
        StringBuilder builder = new StringBuilder();
        for (String label : labels) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label);
        }
        return builder.toString();
    }

    private void primaryButton(String text, View.OnClickListener listener) {
        Button button = button(text, true, listener);
        content.addView(button, fullWidthParams(dp(14)));
    }

    private void actionGrid(Button first, Button second, Button third, Button fourth) {
        row(first, second, dp(6));
        row(third, fourth, dp(6));
    }

    private void row(View first, View second, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = fullWidthParams(topMargin);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.setMargins(dp(5), 0, 0, 0);
        row.addView(first, left);
        row.addView(second, right);
        content.addView(row, rowParams);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(borderDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        card.setLayoutParams(fullWidthParams(dp(8)));
        return card;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        return form;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setMinHeight(dp(54));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(borderDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        return input;
    }

    private EditText numberInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private EditText decimalInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return input;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private void label(String text) {
        TextView view = text(text, 12, COLOR_MUTED, true);
        view.setLetterSpacing(0.08f);
        content.addView(view);
    }

    private void title(String text) {
        TextView view = text(text, 30, COLOR_TEXT, true);
        view.setPadding(0, 0, 0, dp(16));
        content.addView(view);
    }

    private void section(String text) {
        TextView view = text(text, 18, COLOR_TEXT, true);
        view.setPadding(0, dp(20), 0, dp(10));
        content.addView(view);
    }

    private GradientDrawable borderDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private void addAll(LinearLayout form, View... views) {
        for (View view : views) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            form.addView(view, params);
        }
    }

    private String text(EditText input) {
        return input.getText().toString();
    }

    private int parseInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(text(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private double parseDouble(EditText input, double fallback) {
        try {
            return Double.parseDouble(text(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private Integer optionalInt(EditText input) {
        try {
            String value = text(input).trim();
            return value.isEmpty() ? null : Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Double optionalDouble(EditText input) {
        try {
            String value = text(input).trim();
            return value.isEmpty() ? null : Double.parseDouble(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String displayDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private long toEpochMillis(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String formatDuration(int durationSeconds) {
        if (durationSeconds <= 0) {
            return "미기록";
        }
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "시간 " + minutes + "분" : hours + "시간";
        }
        if (minutes > 0) {
            return minutes + "분";
        }
        return durationSeconds + "초";
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
