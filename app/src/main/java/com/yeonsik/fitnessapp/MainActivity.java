package com.yeonsik.fitnessapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.cardio.CardioTrackingService;
import com.yeonsik.fitnessapp.config.LegacyNutritionAccountStore;
import com.yeonsik.fitnessapp.config.LegacyNutritionOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FleekCsvImporter;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;
import com.yeonsik.fitnessapp.sync.SupabaseSyncManager;
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager;
import com.yeonsik.fitnessapp.ui.BaseScreen;
import com.yeonsik.fitnessapp.ui.CardioScreen;
import com.yeonsik.fitnessapp.ui.CardioSessionScreen;
import com.yeonsik.fitnessapp.ui.CardioSummaryScreen;
import com.yeonsik.fitnessapp.ui.FitnessUi;
import com.yeonsik.fitnessapp.ui.HomeScreen;
import com.yeonsik.fitnessapp.ui.MealManagementScreen;
import com.yeonsik.fitnessapp.ui.RecordsScreen;
import com.yeonsik.fitnessapp.ui.RoutineEditorScreen;
import com.yeonsik.fitnessapp.ui.ScreenHost;
import com.yeonsik.fitnessapp.ui.SettingsScreen;
import com.yeonsik.fitnessapp.ui.StrengthScreen;
import com.yeonsik.fitnessapp.ui.WorkoutExerciseDetailScreen;
import com.yeonsik.fitnessapp.ui.WorkoutScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSessionScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSummaryScreen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        MEALS,
        RECORDS,
        SETTINGS
    }

    private static final String UI_PREFS = "fitness_ui_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final int REQUEST_FLEEK_CSV_IMPORT = 4101;
    private static final int REQUEST_CARDIO_LOCATION = 4102;
    private static final int REQUEST_CARDIO_NOTIFICATIONS = 4103;
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String today = LocalDate.now().toString();
    private final WorkoutSessionState sessionState = new WorkoutSessionState();

    private FitnessRepository repository;
    private NutritionCatalogRepository nutritionCatalogRepository;
    private CardioRepository cardioRepository;
    private ExerciseMasterRepository exerciseMasterRepository;
    private RoutineRepository routineRepository;
    private SupabaseConfigStore configStore;
    private LegacyNutritionAccountStore legacyNutritionAccountStore;
    private LegacyNutritionAccountStore.Identity legacyNutritionIdentity;
    private SupabaseSyncManager syncManager;
    private SupabaseAuthManager authManager;
    private SupabaseConfig supabaseConfig;

    private FitnessUi ui;
    private Map<FitnessScreen, BaseScreen> screens;
    private FitnessScreen currentScreen = FitnessScreen.HOME;
    private String themeMode = THEME_LIGHT;

    private LinearLayout rootView;
    private ScrollView mainScrollView;
    private LinearLayout sessionTopBar;
    private LinearLayout sessionBottomBar;
    private LinearLayout restTimerBar;
    private TextView restCountdownView;
    private LinearLayout restProgressTrack;
    private long restEndsAtMillis;
    private int restTotalSeconds;
    private int lastPulsedSecond = -1;
    private FitnessScreen lastRenderedScreen;
    private LinearLayout content;
    private LinearLayout bottomNav;
    private View navDivider;
    private LinearLayout homeTabArea;
    private LinearLayout workoutTabArea;
    private LinearLayout mealsTabArea;
    private LinearLayout recordsTabArea;
    private LinearLayout settingsTabArea;
    private TextView homeTabLabel;
    private TextView workoutTabLabel;
    private TextView mealsTabLabel;
    private TextView recordsTabLabel;
    private TextView settingsTabLabel;

    private boolean isManualSyncing = false;
    private boolean isDataImporting = false;
    private String dataImportDetail = "";
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";
    private CardioActivityType pendingCardioActivityType;
    private String pendingCardioResumeRecordId;
    private boolean waitingForLocationSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new SupabaseConfigStore(this);
        supabaseConfig = configStore.load();
        legacyNutritionAccountStore = new LegacyNutritionAccountStore(this);
        legacyNutritionIdentity = legacyNutritionAccountStore.loadIdentity();
        authManager = new SupabaseAuthManager(configStore);
        FitnessDatabaseHelper databaseHelper = new FitnessDatabaseHelper(this);
        repository = new FitnessRepository(databaseHelper, supabaseConfig.effectiveUserId());
        nutritionCatalogRepository = new NutritionCatalogRepository(
                databaseHelper,
                SupabaseConfig.DEFAULT_USER_ID,
                supabaseConfig
        );
        if (supabaseConfig.isConfigured()) {
            nutritionCatalogRepository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
            migrateLegacyNutritionOwnerIfSafe(supabaseConfig);
        }
        cardioRepository = new CardioRepository(databaseHelper, repository);
        repository.reconcileSharedWorkoutSummaries();
        exerciseMasterRepository = new ExerciseMasterRepository(this);
        routineRepository = new RoutineRepository(databaseHelper, supabaseConfig.effectiveUserId());
        syncManager = new SupabaseSyncManager(databaseHelper);
        applySyncStatusFromConfig();

        themeMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_LIGHT);
        ui = new FitnessUi(this, this::isDarkTheme);
        screens = buildScreens();

        configureWindow();
        setContentView(buildRootView());
        render();
        handleCardioIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCardioIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForLocationSettings && locationServicesEnabled()) {
            waitingForLocationSettings = false;
            continuePendingCardioAction();
            return;
        }
        if (currentScreen == FitnessScreen.CARDIO_SESSION) {
            render();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CARDIO_LOCATION) {
            if (hasPreciseLocationPermission()) {
                continuePendingCardioAction();
            } else {
                clearPendingCardioAction();
                toast("GPS 거리 측정에는 정확한 위치 권한이 필요합니다.");
            }
            return;
        }
        if (requestCode == REQUEST_CARDIO_NOTIFICATIONS) {
            continuePendingCardioActionAfterNotificationPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FLEEK_CSV_IMPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            toast("선택한 CSV 파일을 열 수 없습니다.");
            return;
        }
        importFleekCsv(uri);
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
        map.put(FitnessScreen.STRENGTH, new StrengthScreen(this));
        map.put(FitnessScreen.CARDIO, new CardioScreen(this));
        map.put(FitnessScreen.RECORDS, new RecordsScreen(this));
        map.put(FitnessScreen.SETTINGS, new SettingsScreen(this));
        map.put(FitnessScreen.WORKOUT_SESSION, new WorkoutSessionScreen(this));
        map.put(FitnessScreen.WORKOUT_EXERCISE_DETAIL, new WorkoutExerciseDetailScreen(this));
        map.put(FitnessScreen.WORKOUT_SUMMARY, new WorkoutSummaryScreen(this));
        map.put(FitnessScreen.CARDIO_SESSION, new CardioSessionScreen(this));
        map.put(FitnessScreen.CARDIO_SUMMARY, new CardioSummaryScreen(this));
        map.put(FitnessScreen.MEALS, new MealManagementScreen(this));
        map.put(FitnessScreen.ROUTINE_ADD, routineEditor);
        map.put(FitnessScreen.ROUTINE_DETAIL, routineEditor);
        map.put(FitnessScreen.WORKOUT_EXERCISE_ADD, routineEditor);
        return map;
    }

    // ── 테마 ─────────────────────────────────────────────────────────

    /** 현재 유효 테마. system 모드는 OS의 다크 모드 설정을 따른다. */
    private boolean isDarkTheme() {
        if (THEME_DARK.equals(themeMode)) {
            return true;
        }
        if (THEME_SYSTEM.equals(themeMode)) {
            int nightMask = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    @Override
    public String themeMode() {
        return themeMode;
    }

    @Override
    public void setThemeMode(String mode) {
        themeMode = mode;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_THEME_MODE, mode).apply();
        render();
    }

    // ── 창 / 루트 뷰 ──────────────────────────────────────────────────

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(ui.pageBg());
        window.setNavigationBarColor(ui.pageBg());
        window.getDecorView().setSystemUiVisibility(
                isDarkTheme() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View buildRootView() {
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.pageBg());
        applySystemBarInsets(root);

        sessionTopBar = buildSessionTopBar();
        root.addView(sessionTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        mainScrollView = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(ui.pageBg());
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
        restTimerBar = buildRestTimerBar();
        root.addView(restTimerBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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

    /** Android 15+의 강제 edge-to-edge 환경에서 조작 UI가 시스템 바에 가려지지 않게 한다. */
    private void applySystemBarInsets(View root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(
                    initialLeft + safeInsets.left,
                    initialTop + safeInsets.top,
                    initialRight + safeInsets.right,
                    initialBottom + safeInsets.bottom
            );
            return windowInsets;
        });
    }

    private LinearLayout buildSessionTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4));
        ui.applyDepth(bar, 8);
        bar.setVisibility(View.GONE);
        return bar;
    }

    private LinearLayout buildSessionBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(10));
        ui.applyDepth(bar, 10);
        bar.setVisibility(View.GONE);
        return bar;
    }

    /** 세션 바는 테마에 따라 스타일이 달라지므로 render 시점에 다시 채운다. */
    private void populateSessionBars() {
        sessionTopBar.setBackgroundColor(ui.surface());
        sessionTopBar.removeAllViews();
        TextView back = ui.text("←", 22, FitnessUi.COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ui.borderDrawable(ui.surface(), ui.border(), ui.dp(999)));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> navigate(FitnessScreen.STRENGTH));
        ui.applyDepth(back, 4);
        ui.pressFeedback(back);
        sessionTopBar.addView(back, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));

        sessionBottomBar.setBackgroundColor(ui.surface());
        sessionBottomBar.removeAllViews();
        sessionBottomBar.addView(ui.buttonRow(
                ui.button("종목 추가", false, v -> openWorkoutExercisePicker()),
                ui.button("운동 완료", true, v -> finishActiveWorkout())
        ), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ── 휴식 타이머 ────────────────────────────────────────────────────

    /**
     * 세트 완료 시 자동 시작되는 하단 고정 휴식 타이머.
     * 현재 테마의 강조 표면(라이트=블랙 필, 다크=화이트 필) 위에 뜬다.
     */
    private LinearLayout buildRestTimerBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(6));
        wrapper.setVisibility(View.GONE);
        return wrapper;
    }

    /** 테마가 바뀔 수 있으므로 표시 시점마다 내용을 다시 만든다. */
    private void populateRestTimerBar() {
        restTimerBar.removeAllViews();

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(ui.dp(18), ui.dp(12), ui.dp(14), ui.dp(14));
        inner.setBackground(ui.vibrantBackground(2, ui.dp(18)));
        ui.applyDepth(inner, 10);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText("휴식");
        label.setTextSize(11);
        label.setTextColor(ui.onVibrantMuted());
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        restCountdownView = new TextView(this);
        restCountdownView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        restCountdownView.setTextSize(30);
        restCountdownView.setTextColor(ui.onVibrant());
        restCountdownView.setFontFeatureSettings("tnum");
        row.addView(restCountdownView);

        TextView skip = new TextView(this);
        skip.setText("건너뛰기");
        skip.setTextSize(13);
        skip.setTextColor(ui.onVibrantMuted());
        skip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        skip.setPadding(ui.dp(16), ui.dp(10), ui.dp(6), ui.dp(10));
        skip.setClickable(true);
        skip.setFocusable(true);
        skip.setOnClickListener(v -> stopRestTimer());
        row.addView(skip);
        inner.addView(row);

        restProgressTrack = new LinearLayout(this);
        restProgressTrack.setOrientation(LinearLayout.HORIZONTAL);
        restProgressTrack.setBackground(ui.borderDrawable(
                ui.trackOnVibrant(), ui.trackOnVibrant(), ui.dp(999)));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(4));
        trackParams.setMargins(0, ui.dp(10), 0, 0);
        inner.addView(restProgressTrack, trackParams);

        restTimerBar.addView(inner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void startRestTimer(Integer restSeconds) {
        int seconds = restSeconds == null || restSeconds <= 0 ? 90 : restSeconds;
        restTotalSeconds = seconds;
        restEndsAtMillis = System.currentTimeMillis() + seconds * 1000L;
        lastPulsedSecond = -1;
        populateRestTimerBar();
        updateRestTimerBar();
        if (restTimerBar.getVisibility() != View.VISIBLE && restTimerVisibleOnScreen()) {
            restTimerBar.setVisibility(View.VISIBLE);
            restTimerBar.setAlpha(0f);
            restTimerBar.setTranslationY(ui.dp(20));
            restTimerBar.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
        restTimerBar.removeCallbacks(restTick);
        restTimerBar.postDelayed(restTick, 250);
    }

    private void stopRestTimer() {
        restEndsAtMillis = 0;
        restTimerBar.removeCallbacks(restTick);
        restTimerBar.setVisibility(View.GONE);
    }

    private boolean restTimerVisibleOnScreen() {
        return currentScreen == FitnessScreen.WORKOUT_SESSION
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
    }

    private final Runnable restTick = new Runnable() {
        @Override
        public void run() {
            if (restEndsAtMillis <= 0) {
                return;
            }
            long remainingMillis = restEndsAtMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                stopRestTimer();
                toast("휴식 종료. 다음 세트를 시작하세요.");
                return;
            }
            updateRestTimerBar();
            restTimerBar.postDelayed(this, 250);
        }
    };

    private void updateRestTimerBar() {
        long remainingMillis = Math.max(0, restEndsAtMillis - System.currentTimeMillis());
        int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
        restCountdownView.setText(String.format(java.util.Locale.ROOT, "%d:%02d",
                remainingSeconds / 60, remainingSeconds % 60));

        // 마지막 10초: 초가 바뀔 때마다 크기 펄스로 긴박감을 준다 (색상 대신 크기).
        if (remainingSeconds <= 10 && remainingSeconds != lastPulsedSecond) {
            lastPulsedSecond = remainingSeconds;
            restCountdownView.setScaleX(1.1f);
            restCountdownView.setScaleY(1.1f);
            restCountdownView.animate().scaleX(1f).scaleY(1f).setDuration(240).start();
        }

        float ratio = restTotalSeconds <= 0 ? 0f
                : Math.max(0f, Math.min(1f, remainingMillis / (restTotalSeconds * 1000f)));
        restProgressTrack.removeAllViews();
        View fill = new View(this);
        fill.setBackground(ui.borderDrawable(ui.onVibrant(), ui.onVibrant(), ui.dp(999)));
        restProgressTrack.addView(fill, new LinearLayout.LayoutParams(0, ui.dp(4), ratio));
        View rest = new View(this);
        restProgressTrack.addView(rest, new LinearLayout.LayoutParams(0, ui.dp(4), 1f - ratio));
    }

    // ── 하단 내비게이션 ────────────────────────────────────────────────

    private View buildBottomNav() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(ui.surface());
        ui.applyDepth(wrapper, 12);

        navDivider = new View(this);
        navDivider.setBackgroundColor(ui.border());
        wrapper.addView(navDivider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(14));

        homeTabArea = navArea("메인", Tab.HOME);
        workoutTabArea = navArea("피트니스", Tab.WORKOUT);
        mealsTabArea = navArea("식단", Tab.MEALS);
        recordsTabArea = navArea("기록", Tab.RECORDS);
        settingsTabArea = navArea("설정", Tab.SETTINGS);

        nav.addView(homeTabArea, navParams());
        nav.addView(navGap());
        nav.addView(workoutTabArea, navParams());
        nav.addView(navGap());
        nav.addView(mealsTabArea, navParams());
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
        ui.pressFeedback(area);

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
        } else if (tab == Tab.MEALS) {
            mealsTabLabel = textView;
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
            case MEALS:
                return FitnessScreen.MEALS;
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
            case MEALS:
                return Tab.MEALS;
            case SETTINGS:
                return Tab.SETTINGS;
            default:
                return Tab.WORKOUT;
        }
    }

    private void refreshNavState() {
        Tab activeTab = tabOf(currentScreen);
        boolean workoutInProgress = repository != null
                && repository.latestInProgressSessionId() != null;
        boolean activationVisible = currentScreen != FitnessScreen.WORKOUT_SESSION
                && currentScreen != FitnessScreen.WORKOUT_EXERCISE_DETAIL
                && currentScreen != FitnessScreen.WORKOUT_SUMMARY
                && currentScreen != FitnessScreen.CARDIO_SESSION
                && currentScreen != FitnessScreen.CARDIO_SUMMARY
                && !(currentScreen == FitnessScreen.WORKOUT_EXERCISE_ADD
                && sessionState.activeRecordId() != null);
        bottomNav.setBackgroundColor(ui.surface());
        navDivider.setBackgroundColor(ui.border());
        styleNavArea(homeTabArea, homeTabLabel, activeTab == Tab.HOME, false);
        styleNavArea(workoutTabArea, workoutTabLabel, activeTab == Tab.WORKOUT,
                workoutInProgress && activationVisible);
        styleNavArea(mealsTabArea, mealsTabLabel, activeTab == Tab.MEALS, false);
        styleNavArea(recordsTabArea, recordsTabLabel, activeTab == Tab.RECORDS, false);
        styleNavArea(settingsTabArea, settingsTabLabel, activeTab == Tab.SETTINGS, false);
    }

    private void styleNavArea(LinearLayout area, TextView label, boolean active, boolean hologramActive) {
        String seed = "bottom-nav-" + label.getText();
        Drawable background = active
                ? ui.vibrantRippleDrawable(seed, ui.dp(999))
                : ui.flatSurfaceRippleDrawable(ui.dp(999));
        if (hologramActive) {
            ui.setHologramBackground(area, background, ui.dp(999));
        } else {
            ui.setComponentBackground(area, background);
        }
        ui.applyDepth(area, hologramActive ? 10 : active ? 7 : 3);
        label.setTextColor(active ? ui.onVibrant() : ui.inkMuted());
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ── 화면 디스패치 ─────────────────────────────────────────────────

    private void render() {
        sessionState.nextGeneration();
        content.removeAllViews();
        refreshNavState();
        boolean screenChanged = currentScreen != lastRenderedScreen;
        lastRenderedScreen = currentScreen;
        boolean sessionScreen = currentScreen == FitnessScreen.WORKOUT_SESSION;
        if (sessionScreen) {
            populateSessionBars();
        }
        sessionTopBar.setVisibility(sessionScreen ? View.VISIBLE : View.GONE);
        sessionBottomBar.setVisibility(sessionScreen ? View.VISIBLE : View.GONE);
        boolean restActive = restEndsAtMillis > System.currentTimeMillis() && restTimerVisibleOnScreen();
        restTimerBar.setVisibility(restActive ? View.VISIBLE : View.GONE);
        if (restActive) {
            populateRestTimerBar();
            updateRestTimerBar();
            restTimerBar.removeCallbacks(restTick);
            restTimerBar.postDelayed(restTick, 250);
        }
        boolean fullscreenPicker = currentScreen == FitnessScreen.WORKOUT_EXERCISE_ADD
                && sessionState.activeRecordId() != null;
        applyScreenChrome(isDarkTheme());
        bottomNav.setVisibility(sessionScreen
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL
                || currentScreen == FitnessScreen.WORKOUT_SUMMARY
                || currentScreen == FitnessScreen.CARDIO_SESSION
                || currentScreen == FitnessScreen.CARDIO_SUMMARY
                || fullscreenPicker
                ? View.GONE
                : View.VISIBLE);

        BaseScreen screen = screens.get(currentScreen);
        if (screen != null) {
            screen.render();
            if (screenChanged) {
                ui.screenEnter(content);
            }
        }
    }

    private void applyScreenChrome(boolean dark) {
        int background = ui.pageBg();
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
        window.getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        // 운동 수행 화면에서는 테마와 무관하게 화면이 꺼지지 않는다.
        boolean workoutActive = currentScreen == FitnessScreen.WORKOUT_SESSION
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
        if (workoutActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
    public NutritionCatalogRepository nutritionCatalogRepository() {
        return nutritionCatalogRepository;
    }

    @Override
    public CardioRepository cardioRepository() {
        return cardioRepository;
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
            navigate(FitnessScreen.STRENGTH);
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
        if (cardioRepository.isCardioSession(recordId)) {
            openCardioSession(recordId);
        } else {
            openWorkoutSession(recordId);
        }
    }

    @Override
    public void startRoutineWorkout(List<RoutineExerciseInstance> routineExercises) {
        if (routineExercises == null || routineExercises.isEmpty()) {
            toast("만들어진 루틴이 없습니다.");
            return;
        }
        if (continueExistingWorkoutIfPresent()) {
            return;
        }

        String recordId = repository.createSessionFromRoutine(today,
                routineRepository.activeRoutineName(), routineRepository.activeRoutineId(), routineExercises);
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
        ui.confirmSheet("운동 기록 삭제",
                "이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.",
                "삭제된 기록은 기록 탭에서 더 이상 보이지 않습니다.",
                "삭제", () -> {
                    boolean cardioSession = cardioRepository.isCardioSession(recordId);
                    if (cardioSession) {
                        cardioRepository.deleteLocalData(recordId);
                    }
                    repository.deleteSession(recordId);
                    sessionState.clearIfMatches(recordId);
                    toast("운동 기록을 삭제했습니다.");
                    if (currentScreen == FitnessScreen.RECORDS) {
                        render();
                    } else {
                        navigate(cardioSession ? FitnessScreen.CARDIO : FitnessScreen.STRENGTH);
                    }
                });
    }

    @Override
    public void startEmptyWorkout() {
        if (continueExistingWorkoutIfPresent()) {
            return;
        }
        openWorkoutSession(repository.createEmptySession(today));
    }

    @Override
    public void startCardioWorkout(CardioActivityType activityType) {
        if (activityType == null) {
            toast("유산소 유형을 선택하세요.");
            return;
        }
        if (continueExistingWorkoutIfPresent()) {
            return;
        }
        pendingCardioActivityType = activityType;
        pendingCardioResumeRecordId = null;
        requestCardioPermissionsAndContinue();
    }

    @Override
    public void openCardioSummary(String recordId) {
        if (!cardioRepository.isCardioSession(recordId)) {
            toast("이 기기의 GPS 세부 기록을 찾지 못했습니다.");
            return;
        }
        sessionState.setActiveRecordId(recordId);
        sessionState.setActiveExerciseId(null);
        navigate(FitnessScreen.CARDIO_SUMMARY);
    }

    @Override
    public void pauseCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null || !cardioRepository.pause(recordId)) {
            toast("일시정지할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        dispatchCardioService(CardioTrackingService.ACTION_PAUSE, recordId, false);
        toast("GPS 기록을 일시정지했습니다.");
        render();
    }

    @Override
    public void resumeCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null || !CardioRepository.STATUS_PAUSED.equals(snapshot.status)) {
            toast("재개할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        pendingCardioActivityType = snapshot.activityType;
        pendingCardioResumeRecordId = recordId;
        requestCardioPermissionsAndContinue();
    }

    @Override
    public void finishCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null) {
            toast("완료할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        if (CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
            openCardioSummary(recordId);
            return;
        }
        if (CardioRepository.STATUS_TRACKING.equals(snapshot.status)
                && cardioRepository.pause(recordId)) {
            dispatchCardioService(CardioTrackingService.ACTION_PAUSE, recordId, false);
            render();
        }
        showCardioHeartRateSheet(recordId, true);
    }

    @Override
    public void editCardioAverageHeartRate() {
        String recordId = sessionState.activeRecordId();
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null || !CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
            toast("수정할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        showCardioHeartRateSheet(recordId, false);
    }

    @Override
    public void cancelCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null) {
            toast("취소할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        ui.confirmSheet(
                "유산소 기록 취소",
                "현재 " + CardioMetrics.formatDistanceKilometers(snapshot.distanceMeters)
                        + "km 기록을 저장하지 않습니다.",
                "이 기기에 저장된 GPS 좌표도 함께 삭제됩니다.",
                "기록 취소",
                () -> {
                    stopService(new Intent(this, CardioTrackingService.class));
                    cardioRepository.cancel(recordId);
                    sessionState.clearIfMatches(recordId);
                    toast("유산소 기록을 취소했습니다.");
                    navigate(FitnessScreen.CARDIO);
                }
        );
    }

    private void openCardioSession(String recordId) {
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null) {
            toast("GPS 유산소 상태를 찾지 못했습니다.");
            return;
        }
        sessionState.setActiveRecordId(recordId);
        sessionState.setActiveExerciseId(null);
        if (CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
            navigate(FitnessScreen.CARDIO_SUMMARY);
            return;
        }
        if (CardioRepository.STATUS_TRACKING.equals(snapshot.status)) {
            dispatchCardioService(CardioTrackingService.ACTION_START, recordId, true);
        }
        navigate(FitnessScreen.CARDIO_SESSION);
    }

    private void showCardioHeartRateSheet(String recordId, boolean finishAfterSave) {
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot == null) {
            toast("유산소 기록을 찾지 못했습니다.");
            return;
        }

        LinearLayout form = ui.form();
        int elapsedSeconds = snapshot.elapsedSeconds(System.currentTimeMillis());
        TextView summary = ui.text(
                CardioMetrics.formatDistanceKilometers(snapshot.distanceMeters)
                        + "km · " + CardioMetrics.formatElapsed(elapsedSeconds),
                14,
                FitnessUi.COLOR_MUTED,
                false
        );
        EditText averageHeartRate = ui.numberInput(
                "평균 심박수 bpm (선택)",
                CardioMetrics.hasAverageHeartRate(snapshot.averageHeartRateBpm)
                        ? CardioMetrics.formatAverageHeartRate(snapshot.averageHeartRateBpm)
                        : ""
        );
        TextView hint = ui.text(
                "시계·밴드 등에서 확인한 평균값을 직접 입력하세요. 측정값이 없으면 비워둘 수 있습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        ui.addAll(form, summary, averageHeartRate, hint);

        ui.validatedSheet(
                finishAfterSave ? snapshot.activityType.labelKo() + " 완료" : "평균 심박수 수정",
                form,
                finishAfterSave ? "운동 완료" : "저장",
                () -> {
                    String rawValue = FitnessUi.inputText(averageHeartRate).trim();
                    Integer averageHeartRateBpm = FitnessUi.optionalInt(averageHeartRate);
                    if (!rawValue.isEmpty()
                            && (averageHeartRateBpm == null
                            || !CardioMetrics.isValidAverageHeartRate(averageHeartRateBpm))) {
                        averageHeartRate.setError("평균 심박수는 0보다 큰 정수로 입력하세요.");
                        return false;
                    }

                    CardioRepository.SessionSnapshot saved = finishAfterSave
                            ? cardioRepository.finish(recordId, averageHeartRateBpm)
                            : cardioRepository.updateAverageHeartRate(recordId, averageHeartRateBpm);
                    if (saved == null) {
                        toast("평균 심박수를 저장하지 못했습니다.");
                        return false;
                    }
                    if (finishAfterSave) {
                        stopService(new Intent(this, CardioTrackingService.class));
                        toast("유산소 운동을 완료했습니다.");
                        openCardioSummary(recordId);
                    } else {
                        toast("평균 심박수를 저장했습니다.");
                        render();
                    }
                    return true;
                }
        );
    }

    private boolean continueExistingWorkoutIfPresent() {
        String activeRecordId = repository.latestInProgressSessionId();
        if (activeRecordId == null) {
            return false;
        }
        toast("진행 중인 운동을 먼저 이어갑니다.");
        if (cardioRepository.isCardioSession(activeRecordId)) {
            openCardioSession(activeRecordId);
        } else {
            openWorkoutSession(activeRecordId);
        }
        return true;
    }

    private void requestCardioPermissionsAndContinue() {
        if (!hasPreciseLocationPermission()) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_CARDIO_LOCATION
            );
            return;
        }
        continuePendingCardioAction();
    }

    private void continuePendingCardioAction() {
        if (pendingCardioActivityType == null) {
            return;
        }
        if (!locationServicesEnabled()) {
            waitingForLocationSettings = true;
            toast("휴대폰 위치 서비스를 켜주세요.");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CARDIO_NOTIFICATIONS
            );
            return;
        }
        continuePendingCardioActionAfterNotificationPermission();
    }

    private void continuePendingCardioActionAfterNotificationPermission() {
        CardioActivityType activityType = pendingCardioActivityType;
        String resumeRecordId = pendingCardioResumeRecordId;
        clearPendingCardioAction();
        if (activityType == null || !hasPreciseLocationPermission()) {
            return;
        }

        if (resumeRecordId != null) {
            CardioRepository.SessionSnapshot snapshot = cardioRepository.session(resumeRecordId);
            if (snapshot == null) {
                toast("재개할 유산소 기록을 찾지 못했습니다.");
                return;
            }
            cardioRepository.resume(resumeRecordId);
            dispatchCardioService(CardioTrackingService.ACTION_RESUME, resumeRecordId, true);
            sessionState.setActiveRecordId(resumeRecordId);
            toast("GPS 기록을 재개했습니다.");
            navigate(FitnessScreen.CARDIO_SESSION);
            return;
        }

        String recordId = cardioRepository.startSession(activityType, today);
        sessionState.setActiveRecordId(recordId);
        sessionState.setActiveExerciseId(null);
        dispatchCardioService(CardioTrackingService.ACTION_START, recordId, true);
        toast(activityType.labelKo() + " 기록을 시작했습니다.");
        navigate(FitnessScreen.CARDIO_SESSION);
    }

    private void dispatchCardioService(String action, String recordId, boolean foregroundStart) {
        Intent intent = new Intent(this, CardioTrackingService.class)
                .setAction(action)
                .putExtra(CardioTrackingService.EXTRA_RECORD_ID, recordId);
        if (foregroundStart) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void handleCardioIntent(Intent intent) {
        if (intent == null || cardioRepository == null) {
            return;
        }
        String recordId = intent.getStringExtra(CardioTrackingService.EXTRA_RECORD_ID);
        if (recordId == null || !cardioRepository.isCardioSession(recordId)) {
            return;
        }
        boolean finishRequested = intent.getBooleanExtra(
                CardioTrackingService.EXTRA_FINISH_REQUESTED, false);
        intent.removeExtra(CardioTrackingService.EXTRA_RECORD_ID);
        intent.removeExtra(CardioTrackingService.EXTRA_FINISH_REQUESTED);
        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
        if (snapshot != null && CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
            openCardioSummary(recordId);
        } else {
            openCardioSession(recordId);
            if (finishRequested) {
                finishCardioWorkout();
            }
        }
    }

    private boolean hasPreciseLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean locationServicesEnabled() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return manager.isLocationEnabled();
        }
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void clearPendingCardioAction() {
        pendingCardioActivityType = null;
        pendingCardioResumeRecordId = null;
        waitingForLocationSettings = false;
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
        ui.sheet(existing == null ? "체중 기록" : "체중 수정", form,
                "저장", () -> {
                    String selectedDate = FitnessUi.inputText(dateInput);
                    if (existing == null) {
                        repository.addBodyMetric(selectedDate, FitnessUi.parseDouble(weight, 0), FitnessUi.inputText(memo));
                    } else {
                        repository.updateBodyMetric(existing.id, selectedDate,
                                FitnessUi.parseDouble(weight, existing.weightKg), FitnessUi.inputText(memo));
                    }
                    render();
                },
                existing == null ? null : "이 기록 삭제",
                existing == null ? null : () -> {
                    repository.deleteBodyMetric(existing.id);
                    render();
                });
    }

    @Override
    public void openMealManagement() {
        navigate(FitnessScreen.MEALS);
    }

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    @Override
    public void openFleekDataImport() {
        if (isDataImporting) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "application/vnd.ms-excel",
                "text/plain"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_FLEEK_CSV_IMPORT);
        } catch (Exception error) {
            toast("CSV 파일 선택기를 열지 못했습니다.");
        }
    }

    private void importFleekCsv(Uri uri) {
        if (isDataImporting) return;
        isDataImporting = true;
        dataImportDetail = "CSV를 읽고 운동 기록을 변환하는 중입니다.";
        render();
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalArgumentException("선택한 CSV 파일을 읽지 못했습니다.");
                }
                FleekCsvImporter.ImportPlan plan = FleekCsvImporter.parse(
                        new InputStreamReader(input, StandardCharsets.UTF_8),
                        exerciseMasterRepository.getAllWeightExercises()
                );
                FitnessRepository.FleekImportResult result = repository.importFleekData(plan);
                runOnUiThread(() -> {
                    isDataImporting = false;
                    dataImportDetail = result.summary();
                    toast(result.importedSessions > 0
                            ? "FLEEK 운동 기록을 가져왔습니다."
                            : "이미 가져온 기록이라 새로 저장된 세션이 없습니다.");
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    isDataImporting = false;
                    dataImportDetail = error.getMessage() == null
                            ? "FLEEK CSV 가져오기에 실패했습니다."
                            : error.getMessage();
                    toast("FLEEK CSV 가져오기에 실패했습니다.");
                    render();
                });
            }
        });
    }

    @Override
    public boolean isDataImporting() {
        return isDataImporting;
    }

    @Override
    public String dataImportDetail() {
        return dataImportDetail;
    }

    @Override
    public SupabaseConfig supabaseConfig() {
        return supabaseConfig;
    }

    @Override
    public boolean isSharedSupabaseConnectionManaged() {
        return configStore.isConnectionManaged();
    }

    @Override
    public void saveSupabaseConfig(String url, String anonKey) {
        try {
            applySessionConfig(configStore.saveConnection(url, anonKey));
            applySyncStatusFromConfig();
            toast("Personal OS 공통 DB 설정을 저장했습니다.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            toast(error.getMessage());
        }
        render();
    }

    @Override
    public void signInToSupabase(String email, String password) {
        if (!supabaseConfig.isConnectionConfigured()) {
            toast("Personal OS 공통 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        syncLabel = "authenticating";
        syncDetail = "Supabase 계정에 로그인하는 중입니다.";
        render();
        executor.execute(() -> {
            try {
                SupabaseConfig authenticated = authManager.signIn(
                        supabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    int migratedNutritionRows = applyAuthenticatedConfig(authenticated);
                    applySyncStatusFromConfig();
                    toast(migratedNutritionRows > 0
                            ? "로그인했습니다. 기존 영양 항목 " + migratedNutritionRows
                            + "개를 공통 계정으로 옮겼습니다."
                            : "Personal OS 공통 계정으로 로그인했습니다.");
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    syncLabel = "authentication failed";
                    syncDetail = error.getMessage() == null
                            ? "로그인에 실패했습니다."
                            : error.getMessage();
                    toast("로그인에 실패했습니다.");
                    render();
                });
            }
        });
    }

    @Override
    public void signUpToSupabase(String email, String password) {
        if (!supabaseConfig.isConnectionConfigured()) {
            toast("Personal OS 공통 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        syncLabel = "authenticating";
        syncDetail = "Supabase 계정을 만드는 중입니다.";
        render();
        executor.execute(() -> {
            try {
                SupabaseAuthManager.SignUpResult result = authManager.signUp(
                        supabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    if (result.emailConfirmationRequired) {
                        syncLabel = "confirmation required";
                        syncDetail = "가입 확인 메일을 확인한 뒤 로그인하세요.";
                        toast("가입 확인 메일을 보냈습니다.");
                    } else {
                        int migratedNutritionRows = applyAuthenticatedConfig(result.config);
                        applySyncStatusFromConfig();
                        toast(migratedNutritionRows > 0
                                ? "계정을 만들었습니다. 기존 영양 항목 "
                                + migratedNutritionRows + "개를 옮겼습니다."
                                : "Personal OS 공통 계정이 생성되고 로그인되었습니다.");
                    }
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    syncLabel = "authentication failed";
                    syncDetail = error.getMessage() == null
                            ? "계정 생성에 실패했습니다."
                            : error.getMessage();
                    toast("계정 생성에 실패했습니다.");
                    render();
                });
            }
        });
    }

    @Override
    public void signOutFromSupabase() {
        applySessionConfig(configStore.clearSession());
        applySyncStatusFromConfig();
        toast("로그아웃했습니다. 로컬 기록은 유지됩니다.");
        render();
    }

    @Override
    public void syncNutritionCatalog(NutritionCatalogRepository.SyncCallback callback) {
        executor.execute(() -> {
            try {
                SupabaseConfig activeConfig = supabaseConfig;
                if (activeConfig.isConfigured()) {
                    activeConfig = authManager.refresh(activeConfig);
                    applySessionConfig(activeConfig);
                }
                NutritionCatalogRepository.CatalogSyncResult result =
                        nutritionCatalogRepository.syncRemote();
                if (callback != null) {
                    callback.onComplete(result.pushedRows, result.pulledRows);
                }
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void runManualSync() {
        if (!supabaseConfig.isConfigured()) {
            toast("Supabase 연결 설정을 저장하고 계정에 로그인하세요.");
            return;
        }

        isManualSyncing = true;
        syncLabel = "syncing";
        syncDetail = "Supabase와 수동 동기화 중입니다.";
        render();

        executor.execute(() -> {
            try {
                SupabaseConfig refreshedConfig = authManager.refresh(supabaseConfig);
                applySessionConfig(refreshedConfig);
                SupabaseSyncManager.SyncResult result = syncManager.manualSync(refreshedConfig);
                NutritionCatalogRepository.CatalogSyncResult catalogResult =
                        nutritionCatalogRepository.syncRemote();
                lastSyncedAt = result.syncedAt;
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "synced";
                    syncDetail = "기록 push " + result.pushedRows + "건 · pull "
                            + result.pulledRows + "건 · 영양 push " + catalogResult.pushedRows
                            + "건 · pull " + catalogResult.pulledRows + "건";
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

    private int applyAuthenticatedConfig(SupabaseConfig config) {
        supabaseConfig = config;
        String userId = config.effectiveUserId();
        repository.normalizeLocalUserId(userId);
        routineRepository.normalizeLocalUserId(userId);
        nutritionCatalogRepository.normalizeLocalUserId(userId);
        int migratedNutritionRows = migrateLegacyNutritionOwnerIfSafe(config);
        nutritionCatalogRepository.setSupabaseConfig(config);
        return migratedNutritionRows;
    }

    private void applySessionConfig(SupabaseConfig config) {
        supabaseConfig = config;
        String userId = config.effectiveUserId();
        repository.setUserId(userId);
        routineRepository.setUserId(userId);
        nutritionCatalogRepository.setUserId(userId);
        nutritionCatalogRepository.setSupabaseConfig(config);
    }

    private int migrateLegacyNutritionOwnerIfSafe(SupabaseConfig sharedConfig) {
        if (legacyNutritionIdentity == null || !legacyNutritionIdentity.isPresent()) {
            return 0;
        }

        if (LegacyNutritionOwnerPolicy.canMigrate(legacyNutritionIdentity, sharedConfig)) {
            int migratedRows = nutritionCatalogRepository.migrateLegacyOwner(
                    legacyNutritionIdentity.userId,
                    sharedConfig.effectiveUserId()
            );
            clearLegacyNutritionIdentity();
            return migratedRows;
        }

        if (legacyNutritionIdentity.userId.isEmpty()
                || SupabaseConfig.DEFAULT_USER_ID.equals(legacyNutritionIdentity.userId)) {
            clearLegacyNutritionIdentity();
        }
        return 0;
    }

    private void clearLegacyNutritionIdentity() {
        legacyNutritionAccountStore.clear();
        legacyNutritionIdentity = new LegacyNutritionAccountStore.Identity("", "");
    }

    private void applySyncStatusFromConfig() {
        if (supabaseConfig.isConfigured()) {
            syncLabel = lastSyncedAt.isEmpty() ? "configured" : "synced";
            syncDetail = lastSyncedAt.isEmpty()
                    ? "Personal OS 공통 계정으로 연결되었습니다."
                    : "마지막 동기화 " + lastSyncedAt;
            return;
        }

        syncLabel = supabaseConfig.isConnectionConfigured() ? "login required" : "local-only";
        syncDetail = supabaseConfig.isConnectionConfigured()
                ? "공통 DB는 연결되었습니다. 이 앱에서 최초 1회 로그인하세요."
                : "Personal OS 공통 DB 설정이 없어 로컬 전용 모드입니다.";
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
        if (!supabaseConfig.email.isEmpty()) {
            return supabaseConfig.email;
        }
        return SupabaseConfig.DEFAULT_USER_ID;
    }
}
