package com.yeonsik.fitnessapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.cardio.CardioTrackingService;
import com.yeonsik.fitnessapp.config.AppSurfacePolicy;
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore;
import com.yeonsik.fitnessapp.config.MassUnitPreferences;
import com.yeonsik.fitnessapp.config.PriceTraceSupabaseConfigStore;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FleekCsvImporter;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.LocalDataBackupService;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.data.ProductReadV1Client;
import com.yeonsik.fitnessapp.data.RestaurantMenuReadV1Client;
import com.yeonsik.fitnessapp.data.WorkoutTransferService;
import com.yeonsik.fitnessapp.development.BodyProfile;
import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.development.DevelopmentInsight;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.FitnessNavigationHistory;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;
import com.yeonsik.fitnessapp.supplement.SupplementRepository;
import com.yeonsik.fitnessapp.sync.SupabaseSyncManager;
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager;
import com.yeonsik.fitnessapp.ui.BaseScreen;
import com.yeonsik.fitnessapp.ui.CardioScreen;
import com.yeonsik.fitnessapp.ui.CardioSessionScreen;
import com.yeonsik.fitnessapp.ui.CardioSummaryScreen;
import com.yeonsik.fitnessapp.ui.DevelopmentScreen;
import com.yeonsik.fitnessapp.ui.FitnessUi;
import com.yeonsik.fitnessapp.ui.HomeScreen;
import com.yeonsik.fitnessapp.ui.MealManagementScreen;
import com.yeonsik.fitnessapp.ui.RecordsScreen;
import com.yeonsik.fitnessapp.ui.RoutineEditorScreen;
import com.yeonsik.fitnessapp.ui.ScreenHost;
import com.yeonsik.fitnessapp.ui.SettingsScreen;
import com.yeonsik.fitnessapp.ui.StrengthScreen;
import com.yeonsik.fitnessapp.ui.SupplementScreen;
import com.yeonsik.fitnessapp.ui.WorkoutExerciseDetailScreen;
import com.yeonsik.fitnessapp.ui.WorkoutScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSessionScreen;
import com.yeonsik.fitnessapp.ui.WorkoutSummaryScreen;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Arrays;
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
        DEVELOPMENT,
        SETTINGS
    }

    private static final String UI_PREFS = "fitness_ui_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final DateTimeFormatter MANUAL_WORKOUT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");
    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final int REQUEST_FLEEK_CSV_IMPORT = 4101;
    private static final int REQUEST_CARDIO_LOCATION = 4102;
    private static final int REQUEST_CARDIO_NOTIFICATIONS = 4103;
    private static final int REQUEST_LOCAL_BACKUP_EXPORT = 4111;
    private static final int REQUEST_LOCAL_BACKUP_RESTORE = 4112;
    private static final int REQUEST_RECORDS_CSV_EXPORT = 4113;
    private static final int REQUEST_WORKOUT_TRANSFER_IMPORT = 4114;
    private static final int REQUEST_WORKOUT_TRANSFER_EXPORT = 4115;
    private static final String PRICE_TRACE_LOG_TAG = "PriceTraceSearch";
    public static final String DEBUG_PROVISION_SESSION_ACTION =
            "com.yeonsik.fitnessapp.DEBUG_PROVISION_SESSION";
    private static final String EXTRA_ACCESS_TOKEN = "access_token";
    private static final String EXTRA_REFRESH_TOKEN = "refresh_token";
    private static final String EXTRA_USER_ID = "user_id";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_NUTRITION_ACCESS_TOKEN = "nutrition_access_token";
    private static final String EXTRA_NUTRITION_REFRESH_TOKEN = "nutrition_refresh_token";
    private static final String EXTRA_NUTRITION_USER_ID = "nutrition_user_id";
    private static final String EXTRA_NUTRITION_EMAIL = "nutrition_email";
    private static final String EXTRA_PRICE_TRACE_ACCESS_TOKEN = "price_trace_access_token";
    private static final String EXTRA_PRICE_TRACE_REFRESH_TOKEN = "price_trace_refresh_token";
    private static final String EXTRA_PRICE_TRACE_USER_ID = "price_trace_user_id";
    private static final String EXTRA_PRICE_TRACE_EMAIL = "price_trace_email";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final WorkoutSessionState sessionState = new WorkoutSessionState();
    private String lastKnownDate = LocalDate.now().toString();

    private FitnessRepository repository;
    private FitnessDatabaseHelper databaseHelper;
    private NutritionCatalogRepository nutritionCatalogRepository;
    private CardioRepository cardioRepository;
    private ExerciseMasterRepository exerciseMasterRepository;
    private RoutineRepository routineRepository;
    private DevelopmentRepository developmentRepository;
    private SupplementRepository supplementRepository;
    private SupabaseConfigStore configStore;
    private NutritionSupabaseConfigStore nutritionConfigStore;
    private PriceTraceSupabaseConfigStore priceTraceConfigStore;
    private MassUnitPreferences massUnitPreferences;
    private SupabaseSyncManager syncManager;
    private SupabaseAuthManager authManager;
    private SupabaseAuthManager nutritionAuthManager;
    private SupabaseAuthManager priceTraceAuthManager;
    private SupabaseConfig supabaseConfig;
    private SupabaseConfig nutritionSupabaseConfig;
    private SupabaseConfig priceTraceSupabaseConfig;
    private ProductReadV1Client productReadClient;
    private RestaurantMenuReadV1Client restaurantMenuReadClient;

    private FitnessUi ui;
    private Map<FitnessScreen, BaseScreen> screens;
    private FitnessScreen currentScreen = FitnessScreen.HOME;
    private final FitnessNavigationHistory navigationHistory =
            new FitnessNavigationHistory(FitnessScreen.HOME);
    private OnBackInvokedCallback backInvokedCallback;
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
    private LinearLayout recordsTabArea;
    private LinearLayout developmentTabArea;
    private LinearLayout settingsTabArea;
    private TextView homeTabLabel;
    private TextView workoutTabLabel;
    private TextView recordsTabLabel;
    private TextView developmentTabLabel;
    private TextView settingsTabLabel;
    private View homeTabMarker;
    private View workoutTabMarker;
    private View recordsTabMarker;
    private View developmentTabMarker;
    private View settingsTabMarker;
    private View homeTabProgressMarker;
    private View workoutTabProgressMarker;
    private View recordsTabProgressMarker;
    private View developmentTabProgressMarker;
    private View settingsTabProgressMarker;

    private boolean isManualSyncing = false;
    private boolean isDataImporting = false;
    private String dataImportDetail = "";
    private boolean isDataTransferInProgress;
    private String dataTransferDetail = "";
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";
    private CardioActivityType pendingCardioActivityType;
    private String pendingCardioResumeRecordId;
    private boolean waitingForLocationSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        massUnitPreferences = new MassUnitPreferences(this);
        configStore = new SupabaseConfigStore(this);
        supabaseConfig = configStore.load();
        nutritionConfigStore = new NutritionSupabaseConfigStore(this);
        nutritionSupabaseConfig = nutritionConfigStore.load();
        priceTraceConfigStore = new PriceTraceSupabaseConfigStore(this);
        priceTraceSupabaseConfig = priceTraceConfigStore.load();
        productReadClient = new ProductReadV1Client(priceTraceSupabaseConfig);
        restaurantMenuReadClient = new RestaurantMenuReadV1Client(priceTraceSupabaseConfig);
        authManager = new SupabaseAuthManager(configStore);
        nutritionAuthManager = new SupabaseAuthManager(nutritionConfigStore);
        priceTraceAuthManager = new SupabaseAuthManager(priceTraceConfigStore);
        databaseHelper = new FitnessDatabaseHelper(this);
        repository = new FitnessRepository(databaseHelper, supabaseConfig.effectiveUserId());
        nutritionCatalogRepository = new NutritionCatalogRepository(
                databaseHelper,
                nutritionSupabaseConfig.effectiveUserId(),
                nutritionSupabaseConfig
        );
        cardioRepository = new CardioRepository(databaseHelper, repository);
        repository.reconcileSharedWorkoutSummaries();
        exerciseMasterRepository = new ExerciseMasterRepository(this);
        routineRepository = new RoutineRepository(databaseHelper, supabaseConfig.effectiveUserId());
        developmentRepository = new DevelopmentRepository(databaseHelper, supabaseConfig.effectiveUserId());
        supplementRepository = new SupplementRepository(databaseHelper, supabaseConfig.effectiveUserId());
        syncManager = new SupabaseSyncManager(databaseHelper);
        applySyncStatusFromConfig();

        themeMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_LIGHT);
        ui = new FitnessUi(this, this::isDarkTheme);
        screens = buildScreens();
        registerBackCallback();

        setContentView(buildRootView());
        configureWindow();
        render();
        handleDebugSessionProvisioning(getIntent());
        handleCardioIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDebugSessionProvisioning(intent);
        handleCardioIntent(intent);
    }

    /**
     * Debug-only host provisioning accepts an already-issued session, never a password.
     * The companion build/install script obtains the session outside the APK and this
     * path deliberately refuses to replace an existing session or run in release builds.
     */
    private void handleDebugSessionProvisioning(Intent intent) {
        if (!BuildConfig.DEBUG
                || !AppSurfacePolicy.allowsDebugSessionProvisioning()
                || intent == null
                || !DEBUG_PROVISION_SESSION_ACTION.equals(intent.getAction())) {
            return;
        }
        Log.i(PRICE_TRACE_LOG_TAG, "debug session provisioning intent received");

        boolean provisioned = false;
        String accessToken = normalizeIntentExtra(intent, EXTRA_ACCESS_TOKEN);
        String refreshToken = normalizeIntentExtra(intent, EXTRA_REFRESH_TOKEN);
        String userId = normalizeIntentExtra(intent, EXTRA_USER_ID);
        String email = normalizeIntentExtra(intent, EXTRA_EMAIL);
        if (!accessToken.isEmpty()
                && !refreshToken.isEmpty()
                && canProvisionDebugSession(supabaseConfig, userId)) {
                try {
                    applyAuthenticatedSharedConfig(configStore.saveSession(
                            userId,
                            email,
                            accessToken,
                            refreshToken
                    ));
                    provisioned = true;
                    Log.i(PRICE_TRACE_LOG_TAG, "shared debug session persisted");
                } catch (RuntimeException error) {
                    Log.w(PRICE_TRACE_LOG_TAG, "shared debug session provisioning failed", error);
                }
        }

        String nutritionAccessToken = normalizeIntentExtra(intent, EXTRA_NUTRITION_ACCESS_TOKEN);
        String nutritionRefreshToken = normalizeIntentExtra(intent, EXTRA_NUTRITION_REFRESH_TOKEN);
        String nutritionUserId = normalizeIntentExtra(intent, EXTRA_NUTRITION_USER_ID);
        String nutritionEmail = normalizeIntentExtra(intent, EXTRA_NUTRITION_EMAIL);
        if (!nutritionAccessToken.isEmpty()
                && !nutritionRefreshToken.isEmpty()
                && canProvisionDebugSession(nutritionSupabaseConfig, nutritionUserId)) {
                try {
                    applyAuthenticatedNutritionConfig(nutritionConfigStore.saveSession(
                            nutritionUserId,
                            nutritionEmail,
                            nutritionAccessToken,
                            nutritionRefreshToken
                    ));
                    provisioned = true;
                    Log.i(PRICE_TRACE_LOG_TAG, "nutrition debug session persisted");
                } catch (RuntimeException error) {
                    Log.w(PRICE_TRACE_LOG_TAG, "nutrition debug session provisioning failed", error);
                }
        }

        String priceTraceAccessToken = normalizeIntentExtra(intent, EXTRA_PRICE_TRACE_ACCESS_TOKEN);
        String priceTraceRefreshToken = normalizeIntentExtra(intent, EXTRA_PRICE_TRACE_REFRESH_TOKEN);
        String priceTraceUserId = normalizeIntentExtra(intent, EXTRA_PRICE_TRACE_USER_ID);
        String priceTraceEmail = normalizeIntentExtra(intent, EXTRA_PRICE_TRACE_EMAIL);
        if (!priceTraceAccessToken.isEmpty()
                && !priceTraceRefreshToken.isEmpty()
                && canProvisionDebugSession(priceTraceSupabaseConfig, priceTraceUserId)) {
            try {
                applyPriceTraceSessionConfig(priceTraceConfigStore.saveSession(
                        priceTraceUserId,
                        priceTraceEmail,
                        priceTraceAccessToken,
                        priceTraceRefreshToken
                ));
                provisioned = true;
                Log.i(PRICE_TRACE_LOG_TAG, "PriceTrace debug session persisted");
            } catch (RuntimeException error) {
                Log.w(PRICE_TRACE_LOG_TAG, "PriceTrace debug session provisioning failed", error);
            }
        }

        if (provisioned) {
            applySyncStatusFromConfig();
            toast("빌드 세션을 적용했습니다.");
            render();
        }
    }

    private static String normalizeIntentExtra(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private static boolean canProvisionDebugSession(SupabaseConfig current, String userId) {
        return !userId.isEmpty()
                && (!current.isConfigured() || current.userId.equals(userId));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentDate = today();
        if (!currentDate.equals(lastKnownDate)) {
            BaseScreen records = screens.get(FitnessScreen.RECORDS);
            if (records instanceof RecordsScreen) {
                ((RecordsScreen) records).onDateChanged(lastKnownDate, currentDate);
            }
            BaseScreen meals = screens.get(FitnessScreen.MEALS);
            if (meals instanceof MealManagementScreen) {
                ((MealManagementScreen) meals).onDateChanged(lastKnownDate, currentDate);
            }
            lastKnownDate = currentDate;
            render();
        }
        if (waitingForLocationSettings && locationServicesEnabled()) {
            waitingForLocationSettings = false;
            continuePendingCardioAction();
        }
        if (currentScreen == FitnessScreen.CARDIO_SESSION) {
            render();
        }
        BaseScreen activeScreen = screens.get(currentScreen);
        if (activeScreen != null) {
            activeScreen.onResume();
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
        boolean dataFileRequest = requestCode == REQUEST_FLEEK_CSV_IMPORT
                || requestCode == REQUEST_LOCAL_BACKUP_EXPORT
                || requestCode == REQUEST_LOCAL_BACKUP_RESTORE
                || requestCode == REQUEST_RECORDS_CSV_EXPORT
                || requestCode == REQUEST_WORKOUT_TRANSFER_IMPORT
                || requestCode == REQUEST_WORKOUT_TRANSFER_EXPORT;
        if (!dataFileRequest || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            toast("선택한 파일을 열 수 없습니다.");
            return;
        }
        if (requestCode == REQUEST_FLEEK_CSV_IMPORT) {
            importFleekCsv(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_EXPORT) {
            writeLocalBackup(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_RESTORE) {
            previewLocalBackup(uri);
        } else if (requestCode == REQUEST_RECORDS_CSV_EXPORT) {
            writeRecordsCsv(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_IMPORT) {
            importWorkoutTransfer(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_EXPORT) {
            writeWorkoutTransfer(uri);
        }
    }

    @Override
    protected void onPause() {
        BaseScreen activeScreen = screens.get(currentScreen);
        if (activeScreen != null) {
            activeScreen.onPause();
        }
        super.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        BaseScreen activeScreen = screens.get(currentScreen);
        if (activeScreen != null) {
            activeScreen.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        BaseScreen activeScreen = screens.get(currentScreen);
        if (activeScreen != null) {
            activeScreen.onDestroy();
        }
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
        map.put(FitnessScreen.DEVELOPMENT, new DevelopmentScreen(this));
        map.put(FitnessScreen.SETTINGS, new SettingsScreen(this));
        map.put(FitnessScreen.WORKOUT_SESSION, new WorkoutSessionScreen(this));
        map.put(FitnessScreen.WORKOUT_EXERCISE_DETAIL, new WorkoutExerciseDetailScreen(this));
        map.put(FitnessScreen.WORKOUT_SUMMARY, new WorkoutSummaryScreen(this));
        map.put(FitnessScreen.CARDIO_SESSION, new CardioSessionScreen(this));
        map.put(FitnessScreen.CARDIO_SUMMARY, new CardioSummaryScreen(this));
        map.put(FitnessScreen.MEALS, new MealManagementScreen(this));
        map.put(FitnessScreen.SUPPLEMENTS, new SupplementScreen(this));
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

    @Override
    public MassUnit preferredMassUnit() {
        return massUnitPreferences == null
                ? MassUnit.KG
                : massUnitPreferences.preferredMassUnit();
    }

    @Override
    public void setPreferredMassUnit(MassUnit unit) {
        if (massUnitPreferences != null) {
            massUnitPreferences.setPreferredMassUnit(unit);
        }
        render();
    }

    // ── 창 / 루트 뷰 ──────────────────────────────────────────────────

    private void configureWindow() {
        applySystemBarAppearance(isDarkTheme(), ui.pageBg(), ui.pageBg());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && rootView != null) {
            // Apply again after the decor view is attached. This keeps the
            // launch theme authoritative for the preview while ensuring that
            // runtime appearance uses WindowInsetsController.
            rootView.post(() -> applySystemBarAppearance(
                    isDarkTheme(),
                    ui.pageBg(),
                    ui.pageBg()
            ));
        }
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
        ui.applyPageContentPadding(content);
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
        root.post(root::requestApplyInsets);
    }

    private LinearLayout buildSessionTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4));
        ui.applyDepth(bar, FitnessUi.DEPTH_SURFACE_DP);
        bar.setVisibility(View.GONE);
        return bar;
    }

    private LinearLayout buildSessionBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(10));
        ui.applyDepth(bar, FitnessUi.DEPTH_SURFACE_DP);
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
        back.setContentDescription("운동 세션에서 나가기");
        back.setOnClickListener(v -> {
            if (!back()) {
                replace(FitnessScreen.STRENGTH);
            }
        });
        ui.applyDepth(back, FitnessUi.DEPTH_FLAT_DP);
        ui.pressFeedback(back);
        sessionTopBar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        sessionBottomBar.setBackgroundColor(ui.surface());
        sessionBottomBar.removeAllViews();
        sessionBottomBar.addView(ui.buttonRow(
                ui.secondaryButton("종목 추가", v -> openWorkoutExercisePicker()),
                ui.primaryButton("운동 완료", v -> finishActiveWorkout())
        ), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ── 휴식 타이머 ────────────────────────────────────────────────────

    /**
     * 세트 완료 시 자동 시작되는 하단 고정 휴식 타이머.
     * 현재 테마의 tonal blue surface 위에 뜬다.
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
        inner.setBackground(ui.tonalRippleDrawable(ui.dp(FitnessUi.CARD_RADIUS_DP)));
        ui.applyDepth(inner, FitnessUi.DEPTH_SURFACE_DP);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText("휴식");
        label.setTextSize(11);
        label.setTextColor(ui.tonalInk());
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        restCountdownView = new TextView(this);
        restCountdownView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        restCountdownView.setTextSize(30);
        restCountdownView.setTextColor(ui.tonalInk());
        restCountdownView.setFontFeatureSettings("tnum");
        row.addView(restCountdownView);

        TextView skip = new TextView(this);
        skip.setText("건너뛰기");
        skip.setTextSize(13);
        skip.setTextColor(ui.tonalInk());
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
                ui.trackOnAccent(), ui.trackOnAccent(), ui.dp(FitnessUi.CHIP_RADIUS_DP)));
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
        fill.setBackground(ui.borderDrawable(ui.tonalInk(), ui.tonalInk(),
                ui.dp(FitnessUi.CHIP_RADIUS_DP)));
        restProgressTrack.addView(fill, new LinearLayout.LayoutParams(0, ui.dp(4), ratio));
        View rest = new View(this);
        restProgressTrack.addView(rest, new LinearLayout.LayoutParams(0, ui.dp(4), 1f - ratio));
    }

    // ── 하단 내비게이션 ────────────────────────────────────────────────

    private View buildBottomNav() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(ui.surface());
        ui.applyDepth(wrapper, FitnessUi.DEPTH_SURFACE_DP);

        navDivider = new View(this);
        navDivider.setBackgroundColor(ui.border());
        wrapper.addView(navDivider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(
                ui.dp(FitnessUi.NAV_BAR_HORIZONTAL_PADDING_DP),
                ui.dp(FitnessUi.NAV_BAR_TOP_PADDING_DP),
                ui.dp(FitnessUi.NAV_BAR_HORIZONTAL_PADDING_DP),
                ui.dp(FitnessUi.NAV_BAR_BOTTOM_PADDING_DP)
        );

        homeTabArea = navArea("메인", Tab.HOME);
        workoutTabArea = navArea("피트니스", Tab.WORKOUT);
        recordsTabArea = navArea("기록", Tab.RECORDS);
        settingsTabArea = navArea("설정", Tab.SETTINGS);

        developmentTabArea = navArea("발전", Tab.DEVELOPMENT);

        nav.addView(homeTabArea, navParams());
        nav.addView(navGap());
        nav.addView(workoutTabArea, navParams());
        nav.addView(navGap());
        nav.addView(recordsTabArea, navParams());
        nav.addView(navGap());
        nav.addView(developmentTabArea, navParams());
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
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                ui.dp(FitnessUi.NAV_ITEM_GAP_DP), ui.dp(1)));
        return gap;
    }

    private LinearLayout navArea(String label, Tab tab) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER);
        area.setMinimumHeight(ui.dp(FitnessUi.NAV_ITEM_MIN_HEIGHT_DP));
        area.setClickable(true);
        area.setFocusable(true);
        area.setOnClickListener(v -> replace(rootScreenOf(tab)));
        ui.pressFeedback(area);

        FrameLayout markerSlot = new FrameLayout(this);
        markerSlot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View activeMarker = new View(this);
        activeMarker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        activeMarker.setVisibility(View.INVISIBLE);
        markerSlot.addView(activeMarker, new FrameLayout.LayoutParams(
                ui.dp(FitnessUi.NAV_ACTIVE_MARKER_WIDTH_DP),
                ui.dp(FitnessUi.NAV_ACTIVE_MARKER_HEIGHT_DP),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        ));
        View progressMarker = new View(this);
        progressMarker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progressMarker.setVisibility(View.INVISIBLE);
        markerSlot.addView(progressMarker, new FrameLayout.LayoutParams(
                ui.dp(FitnessUi.NAV_PROGRESS_MARKER_SIZE_DP),
                ui.dp(FitnessUi.NAV_PROGRESS_MARKER_SIZE_DP),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        ));
        area.addView(markerSlot, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(FitnessUi.NAV_MARKER_SLOT_HEIGHT_DP)
        ));

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(12);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, ui.dp(2), 0, ui.dp(6));
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        area.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (tab == Tab.HOME) {
            homeTabLabel = textView;
            homeTabMarker = activeMarker;
            homeTabProgressMarker = progressMarker;
        } else if (tab == Tab.WORKOUT) {
            workoutTabLabel = textView;
            workoutTabMarker = activeMarker;
            workoutTabProgressMarker = progressMarker;
        } else if (tab == Tab.RECORDS) {
            recordsTabLabel = textView;
            recordsTabMarker = activeMarker;
            recordsTabProgressMarker = progressMarker;
        } else if (tab == Tab.DEVELOPMENT) {
            developmentTabLabel = textView;
            developmentTabMarker = activeMarker;
            developmentTabProgressMarker = progressMarker;
        } else {
            settingsTabLabel = textView;
            settingsTabMarker = activeMarker;
            settingsTabProgressMarker = progressMarker;
        }

        return area;
    }

    private FitnessScreen rootScreenOf(Tab tab) {
        switch (tab) {
            case HOME:
                return FitnessScreen.HOME;
            case RECORDS:
                return FitnessScreen.RECORDS;
            case DEVELOPMENT:
                return FitnessScreen.DEVELOPMENT;
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
            case DEVELOPMENT:
                return Tab.DEVELOPMENT;
            case MEALS:
                return Tab.WORKOUT;
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
        bottomNav.setBackgroundColor(ui.surface());
        navDivider.setBackgroundColor(ui.border());
        boolean navigationVisible = isBottomNavigationVisible(currentScreen);
        styleNavArea(homeTabArea, homeTabLabel, homeTabMarker, homeTabProgressMarker,
                activeTab == Tab.HOME, false);
        styleNavArea(workoutTabArea, workoutTabLabel, workoutTabMarker, workoutTabProgressMarker,
                activeTab == Tab.WORKOUT,
                workoutInProgress && navigationVisible);
        styleNavArea(recordsTabArea, recordsTabLabel, recordsTabMarker, recordsTabProgressMarker,
                activeTab == Tab.RECORDS, false);
        styleNavArea(developmentTabArea, developmentTabLabel, developmentTabMarker,
                developmentTabProgressMarker,
                activeTab == Tab.DEVELOPMENT, false);
        styleNavArea(settingsTabArea, settingsTabLabel, settingsTabMarker,
                settingsTabProgressMarker,
                activeTab == Tab.SETTINGS, false);
    }

    private void styleNavArea(
            LinearLayout area,
            TextView label,
            View activeMarker,
            View progressMarker,
            boolean active,
            boolean inProgress
    ) {
        area.setSelected(active);
        String contentDescription = label.getText().toString();
        if (active) {
            contentDescription += ", 선택됨";
        }
        if (inProgress) {
            contentDescription += ", 운동 진행 중";
        }
        area.setContentDescription(contentDescription);

        // Keep the whole nav surface quiet. Selection is conveyed by the
        // label and active bar; progress gets only a dot on the workout tab.
        ui.setComponentBackground(area,
                ui.flatSurfaceRippleDrawable(ui.dp(FitnessUi.NAV_ITEM_RADIUS_DP)));
        ui.applyDepth(area, FitnessUi.DEPTH_FLAT_DP);
        label.setTextColor(active ? ui.selectedInk() : ui.inkMuted());
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        styleNavMarkers(activeMarker, progressMarker, active, inProgress);
    }

    private void styleNavMarkers(
            View activeMarker,
            View progressMarker,
            boolean active,
            boolean inProgress
    ) {
        if (activeMarker == null || progressMarker == null
                || !(activeMarker.getLayoutParams() instanceof FrameLayout.LayoutParams)
                || !(progressMarker.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams activeParams =
                (FrameLayout.LayoutParams) activeMarker.getLayoutParams();
        activeParams.width = ui.dp(FitnessUi.NAV_ACTIVE_MARKER_WIDTH_DP);
        activeParams.height = ui.dp(FitnessUi.NAV_ACTIVE_MARKER_HEIGHT_DP);
        if (active) {
            activeMarker.setBackground(ui.borderDrawable(
                    ui.pastelBlue(), Color.TRANSPARENT, ui.dp(FitnessUi.CHIP_RADIUS_DP)));
            activeMarker.setVisibility(View.VISIBLE);
        } else {
            activeMarker.setBackgroundColor(Color.TRANSPARENT);
            activeMarker.setVisibility(View.INVISIBLE);
        }
        activeMarker.setLayoutParams(activeParams);

        FrameLayout.LayoutParams progressParams =
                (FrameLayout.LayoutParams) progressMarker.getLayoutParams();
        progressParams.width = ui.dp(FitnessUi.NAV_PROGRESS_MARKER_SIZE_DP);
        progressParams.height = ui.dp(FitnessUi.NAV_PROGRESS_MARKER_SIZE_DP);
        if (inProgress) {
            progressMarker.setBackground(ui.borderDrawable(
                    ui.pastelBlue(), Color.TRANSPARENT, ui.dp(FitnessUi.CHIP_RADIUS_DP)));
            progressMarker.setVisibility(View.VISIBLE);
        } else {
            progressMarker.setBackgroundColor(Color.TRANSPARENT);
            progressMarker.setVisibility(View.INVISIBLE);
        }
        progressMarker.setLayoutParams(progressParams);
    }

    private boolean isBottomNavigationVisible(FitnessScreen screen) {
        if (screen == null) {
            return true;
        }
        switch (screen) {
            case WORKOUT_SESSION:
            case WORKOUT_EXERCISE_DETAIL:
            case WORKOUT_SUMMARY:
            case CARDIO_SESSION:
            case CARDIO_SUMMARY:
                return false;
            case WORKOUT_EXERCISE_ADD:
                return sessionState.activeRecordId() == null;
            default:
                return true;
        }
    }

    // ── 화면 디스패치 ─────────────────────────────────────────────────

    private void render() {
        sessionState.nextGeneration();
        boolean screenChanged = currentScreen != lastRenderedScreen;
        if (screenChanged && lastRenderedScreen != null) {
            BaseScreen previousScreen = screens.get(lastRenderedScreen);
            if (previousScreen != null) {
                previousScreen.onHidden();
            }
        }
        content.removeAllViews();
        refreshNavState();
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
        applyScreenChrome(isDarkTheme());
        bottomNav.setVisibility(isBottomNavigationVisible(currentScreen)
                ? View.VISIBLE : View.GONE);

        BaseScreen screen = screens.get(currentScreen);
        if (screen != null) {
            screen.render();
            screen.onVisible();
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
        applySystemBarAppearance(dark, background, background);
        // 운동 수행 화면에서는 테마와 무관하게 화면이 꺼지지 않는다.
        boolean workoutActive = currentScreen == FitnessScreen.WORKOUT_SESSION
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
        Window window = getWindow();
        if (workoutActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void applySystemBarAppearance(
            boolean dark,
            int statusBarColor,
            int navigationBarColor
    ) {
        Window window = getWindow();
        // Android 15+ enforces edge-to-edge for this target SDK. The page/root
        // surface should show through the bars instead of relying on bar color
        // APIs that are ignored or transformed by the platform.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setStatusBarColor(statusBarColor);
            window.setNavigationBarColor(navigationBarColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(navigationBarColor);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int lightSystemBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(
                        dark ? 0 : lightSystemBars,
                        lightSystemBars
                );
                return;
            }
        }

        // API 26~29 fallback. The null-controller branch keeps the appearance
        // safe if a pre-draw window has no controller yet on a newer API.
        int systemUiVisibility = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUiVisibility);
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
    public SupplementRepository supplementRepository() {
        return supplementRepository;
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
        return LocalDate.now().toString();
    }

    @Override
    public FitnessScreen currentScreen() {
        return currentScreen;
    }

    @Override
    public void navigate(FitnessScreen screen) {
        navigationHistory.push(screen);
        currentScreen = screen;
        render();
    }

    @Override
    public boolean back() {
        FitnessScreen previous = navigationHistory.back();
        if (previous == null) {
            return false;
        }
        currentScreen = previous;
        render();
        return true;
    }

    @Override
    public void replace(FitnessScreen screen) {
        navigationHistory.replace(screen);
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
        if (recordId == null
                || !recordId.equals(sessionState.activeRecordId())
                || sessionState.sessionInputMassUnit() == null) {
            sessionState.startSession(preferredMassUnit());
        }
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
        sessionState.clearExerciseReplacement();
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
            replace(FitnessScreen.STRENGTH);
            return;
        }
        repository.finishSession(recordId);
        toast("운동을 완료했습니다.");
        replace(FitnessScreen.WORKOUT_SUMMARY);
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

        String recordId = repository.createSessionFromRoutine(today(),
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
                        replace(cardioSession ? FitnessScreen.CARDIO : FitnessScreen.STRENGTH);
                    }
                });
    }

    @Override
    public void startEmptyWorkout() {
        if (continueExistingWorkoutIfPresent()) {
            return;
        }
        openWorkoutSession(repository.createEmptySession(today()));
    }

    @Override
    public void showPastWorkoutDialog() {
        if (continueExistingWorkoutIfPresent()) {
            return;
        }

        String activeRoutineId = routineRepository.activeRoutineId();
        List<RoutineRepository.RoutineSummary> routines = routineRepository.routines();
        RoutineRepository.RoutineSummary[] selectedRoutine = {null};
        for (RoutineRepository.RoutineSummary routine : routines) {
            if (routine.id.equals(activeRoutineId)) {
                selectedRoutine[0] = routine;
                break;
            }
        }

        LinearLayout form = ui.form();
        EditText dateInput = ui.input("날짜 (YYYY-MM-DD)", LocalDate.now().minusDays(1).toString());
        EditText startTimeInput = ui.input("시작 시각 (HH:mm)", "18:00");
        EditText durationInput = ui.numberInput("운동 시간 (분)", "60");
        Button routineButton = ui.button(manualWorkoutRoutineLabel(selectedRoutine[0]), false, null);
        routineButton.setOnClickListener(v -> {
            String[] labels = new String[routines.size() + 1];
            labels[0] = "루틴 없이 운동";
            int checked = 0;
            for (int index = 0; index < routines.size(); index++) {
                RoutineRepository.RoutineSummary routine = routines.get(index);
                labels[index + 1] = manualWorkoutRoutineLabel(routine);
                if (selectedRoutine[0] != null && routine.id.equals(selectedRoutine[0].id)) {
                    checked = index + 1;
                }
            }
            ui.choiceSheet("운동 루틴", Arrays.asList(labels), checked, which -> {
                        selectedRoutine[0] = which == 0 ? null : routines.get(which - 1);
                        routineButton.setText(manualWorkoutRoutineLabel(selectedRoutine[0]));
                    });
        });
        ui.addAll(
                form,
                ui.labeledFieldColumn("운동 날짜", dateInput),
                ui.labeledFieldColumn("시작 시각", startTimeInput),
                ui.labeledFieldColumn("운동 시간", durationInput),
                ui.labeledFieldColumn("운동 루틴", routineButton)
        );

        ui.validatedSheet("지난 운동 수동 등록", form, "세트 입력으로 이동", () -> {
            try {
                LocalDate selectedDate = LocalDate.parse(FitnessUi.inputText(dateInput).trim());
                LocalTime selectedTime = LocalTime.parse(
                        FitnessUi.inputText(startTimeInput).trim(),
                        MANUAL_WORKOUT_TIME_FORMAT
                );
                Integer durationMinutes = FitnessUi.optionalInt(durationInput);
                if (durationMinutes == null || durationMinutes <= 0 || durationMinutes > 1440) {
                    throw new IllegalArgumentException("운동 시간은 1~1440분으로 입력하세요.");
                }

                OffsetDateTime startedAt = selectedDate.atTime(selectedTime).atOffset(KOREA_OFFSET);
                OffsetDateTime endedAt = startedAt.plusMinutes(durationMinutes);
                if (endedAt.isAfter(OffsetDateTime.now(KOREA_OFFSET))) {
                    throw new IllegalArgumentException("종료 시각이 현재보다 늦을 수 없습니다.");
                }

                RoutineRepository.RoutineSummary routine = selectedRoutine[0];
                List<RoutineExerciseInstance> exercises = routine == null
                        ? java.util.Collections.emptyList()
                        : routineRepository.routineExercises(routine.id);
                String recordId = repository.createManualPastSessionFromRoutine(
                        selectedDate.toString(),
                        routine == null ? "루틴 없이 운동" : routine.name,
                        routine == null ? null : routine.id,
                        exercises,
                        startedAt.toString(),
                        endedAt.toString()
                );
                toast("세트와 횟수를 입력한 뒤 운동 완료를 누르세요.");
                openWorkoutSession(recordId);
                return true;
            } catch (DateTimeParseException error) {
                toast("날짜는 YYYY-MM-DD, 시작 시각은 HH:mm 형식으로 입력하세요.");
                return false;
            } catch (IllegalArgumentException error) {
                toast(error.getMessage());
                return false;
            }
        });
    }

    private static String manualWorkoutRoutineLabel(RoutineRepository.RoutineSummary routine) {
        return routine == null
                ? "루틴 없이 운동"
                : routine.name + " · " + routine.exerciseCount + "종목";
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
        if (currentScreen == FitnessScreen.CARDIO_SESSION) {
            replace(FitnessScreen.CARDIO_SUMMARY);
        } else {
            navigate(FitnessScreen.CARDIO_SUMMARY);
        }
    }

    @Override
    public void loadCardioRoute(String recordId, CardioRouteCallback callback) {
        if (callback == null || isFinishing() || isDestroyed()) {
            return;
        }
        executor.execute(() -> {
            try {
                CardioRouteProjection projection = cardioRepository.routeProjection(recordId);
                dispatchCardioRouteCallback(() -> callback.onComplete(projection));
            } catch (Exception error) {
                dispatchCardioRouteCallback(() -> callback.onError(error));
            }
        });
    }

    private void dispatchCardioRouteCallback(Runnable callback) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            callback.run();
        });
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        backInvokedCallback = this::dispatchBack;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backInvokedCallback
        );
    }

    private void dispatchBack() {
        // Only system Back dismisses an app dialog before consulting screen history.
        if (ui != null && ui.dismissActiveDialog()) {
            return;
        }
        if (back()) {
            return;
        }
        if (FitnessScreen.HOME.equals(currentScreen) && !navigationHistory.canBack()) {
            // Only the initial HOME entry is allowed to finish the Activity.
            finish();
            return;
        }
        // Keep the exit invariant even if an external caller replaced the initial entry.
        navigationHistory.replace(FitnessScreen.HOME);
        currentScreen = FitnessScreen.HOME;
        render();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        dispatchBack();
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
                    replace(FitnessScreen.CARDIO);
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

        String recordId = cardioRepository.startSession(activityType, today());
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
        showBodyMetricDialog(today(), null);
    }

    @Override
    public void showBodyMetricDialog(String date, String recordId) {
        FitnessRepository.BodyMetricEntry existing = recordId == null
                ? repository.bodyMetricForDate(date)
                : repository.bodyMetricEntryById(recordId);
        MassUnit inputUnit = preferredMassUnit();
        LinearLayout form = ui.form();
        EditText dateInput = ui.input("날짜 (YYYY-MM-DD)", date);
        EditText weight = ui.decimalInput(
                "체중 " + inputUnit.symbol(),
                existing == null ? "" : MassFormatter.formatInput(existing.weightKg, inputUnit)
        );
        EditText memo = ui.input("메모 (선택)", "");
        if (existing != null) {
            memo.setText(existing.memo);
        }
        ui.addAll(form, dateInput, weight, memo);
        ui.validatedSheet(existing == null ? "체중 기록" : "체중 수정", form,
                "저장", () -> {
                    try {
                        String selectedDate = FitnessUi.inputText(dateInput);
                        Double selectedWeight = FitnessUi.optionalDouble(weight);
                        if (selectedWeight == null) {
                            throw new IllegalArgumentException("체중을 입력하세요.");
                        }
                        double selectedWeightKg = MassUnit.toKg(selectedWeight, inputUnit);
                        if (existing == null) {
                            repository.addBodyMetric(
                                    selectedDate,
                                    selectedWeightKg,
                                    FitnessUi.inputText(memo)
                            );
                        } else {
                            repository.updateBodyMetric(
                                    existing.id,
                                    selectedDate,
                                    selectedWeightKg,
                                    FitnessUi.inputText(memo)
                            );
                        }
                        render();
                        return true;
                    } catch (IllegalArgumentException error) {
                        toast(error.getMessage());
                        return false;
                    }
                },
                existing == null ? null : "이 기록 삭제",
                existing == null ? null : () -> {
                    repository.deleteBodyMetric(existing.id);
                    render();
                });
    }

    @Override
    public void openMealManagement() {
        openMealManagement(today(), FitnessScreen.WORKOUT);
    }

    @Override
    public void openMealManagement(String date) {
        openMealManagement(date, FitnessScreen.WORKOUT);
    }

    @Override
    public void openMealManagement(String date, FitnessScreen returnScreen) {
        BaseScreen meals = screens.get(FitnessScreen.MEALS);
        if (meals instanceof MealManagementScreen) {
            ((MealManagementScreen) meals).selectDate(date);
            ((MealManagementScreen) meals).setReturnScreen(returnScreen);
        }
        navigate(FitnessScreen.MEALS);
    }

    @Override
    public void openSettingsConnections() {
        if (!isDeveloperSurfaceAllowed()) {
            navigate(FitnessScreen.SETTINGS);
            return;
        }
        BaseScreen settings = screens.get(FitnessScreen.SETTINGS);
        if (settings instanceof SettingsScreen) {
            ((SettingsScreen) settings).showAdvancedConnections();
        }
        navigate(FitnessScreen.SETTINGS);
    }

    @Override
    public boolean isDeveloperSurfaceAllowed() {
        return AppSurfacePolicy.allowsDeveloperSurface();
    }

    @Override
    public DevelopmentRepository developmentRepository() {
        return developmentRepository;
    }

    @Override
    public void showDevelopmentBodyProfileDialog() {
        BodyProfile currentProfile = developmentRepository.bodyProfile();
        FitnessRepository.BodyMetricEntry todayWeight = repository.bodyMetricForDate(today());
        LinearLayout form = ui.form();
        EditText heightInput = ui.numberInput(
                "키 cm",
                currentProfile.heightCm == null ? "" : String.valueOf(currentProfile.heightCm)
        );
        MassUnit inputUnit = preferredMassUnit();
        EditText weightInput = ui.decimalInput(
                "오늘 체중 " + inputUnit.symbol(),
                todayWeight == null
                        ? ""
                        : MassFormatter.formatInput(todayWeight.weightKg, inputUnit)
        );
        ui.addAll(form, heightInput, weightInput);
        ui.validatedSheet("바디 정보 수정", form, "저장", () -> {
            try {
                String heightText = FitnessUi.inputText(heightInput).trim();
                String weightText = FitnessUi.inputText(weightInput).trim();
                if (heightText.isEmpty() && weightText.isEmpty()) {
                    throw new IllegalArgumentException("키 또는 오늘 체중을 하나 이상 입력해 주세요.");
                }
                BodyProfile nextProfile = null;
                Double nextWeightKg = null;
                if (!heightText.isEmpty()) {
                    int heightCm = Integer.parseInt(heightText);
                    nextProfile = new BodyProfile(heightCm, "", "");
                }
                if (!weightText.isEmpty()) {
                    double weightKg = MassUnit.toKg(
                            Double.parseDouble(weightText),
                            inputUnit
                    );
                    if (!Double.isFinite(weightKg) || weightKg < 20d || weightKg > 400d) {
                        throw new IllegalArgumentException("체중은 20~400kg 범위로 입력해 주세요.");
                    }
                    nextWeightKg = weightKg;
                }
                if (nextProfile != null) {
                    developmentRepository.saveBodyProfile(nextProfile);
                }
                if (nextWeightKg != null) {
                    repository.addBodyMetric(
                            today(),
                            nextWeightKg,
                            todayWeight == null ? "" : todayWeight.memo
                    );
                }
                render();
                return true;
            } catch (NumberFormatException error) {
                toast("숫자 형식이 올바르지 않습니다.");
                return false;
            } catch (IllegalArgumentException error) {
                toast(error.getMessage());
                return false;
            }
        });
    }

    @Override
    public void showDevelopmentGoalDialog() {
        DevelopmentGoal currentGoal = developmentRepository.developmentGoal();
        String[] objectiveCodes = DevelopmentGoal.OBJECTIVES.toArray(new String[0]);
        String[] objectiveLabels = new String[objectiveCodes.length];
        for (int index = 0; index < objectiveCodes.length; index++) {
            objectiveLabels[index] = DevelopmentGoal.objectiveLabelKo(objectiveCodes[index]);
        }
        String[] focusCodes = DevelopmentGoal.FOCUS_BODY_PARTS.toArray(new String[0]);
        String[] focusLabels = new String[focusCodes.length];
        for (int index = 0; index < focusCodes.length; index++) {
            focusLabels[index] = DevelopmentGoal.bodyPartLabelKo(focusCodes[index]);
        }

        final String[] selectedObjective = {
                currentGoal.isConfigured() ? currentGoal.objective : DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN
        };
        final String[] selectedFocus = {
                currentGoal.isConfigured() ? currentGoal.focusBodyPart : DevelopmentGoal.BODY_PART_CHEST
        };
        LinearLayout form = ui.form();
        Button objectivePicker = ui.button(
                "목표 · " + DevelopmentGoal.objectiveLabelKo(selectedObjective[0]),
                false,
                null
        );
        objectivePicker.setAllCaps(false);
        objectivePicker.setOnClickListener(v -> ui.choiceSheet(
                "발전 목표 선택",
                Arrays.asList(objectiveLabels),
                indexOf(objectiveCodes, selectedObjective[0]),
                which -> {
                            selectedObjective[0] = objectiveCodes[which];
                            objectivePicker.setText("목표 · " + objectiveLabels[which]);
                        }
                ));
        EditText weeklySessionsInput = ui.numberInput(
                "주간 운동 목표 1~7회",
                currentGoal.weeklySessionsTarget == null
                        ? "3"
                        : String.valueOf(currentGoal.weeklySessionsTarget)
        );
        Button focusPicker = ui.button(
                "집중 부위 · " + DevelopmentGoal.bodyPartLabelKo(selectedFocus[0]),
                false,
                null
        );
        focusPicker.setAllCaps(false);
        focusPicker.setOnClickListener(v -> ui.choiceSheet(
                "집중 부위 선택",
                Arrays.asList(focusLabels),
                indexOf(focusCodes, selectedFocus[0]),
                which -> {
                            selectedFocus[0] = focusCodes[which];
                            focusPicker.setText("집중 부위 · " + focusLabels[which]);
                        }
                ));
        ui.addAll(form, objectivePicker, weeklySessionsInput, focusPicker);
        ui.validatedSheet("발전 목표 수정", form, "저장", () -> {
            try {
                int weeklySessions = Integer.parseInt(FitnessUi.inputText(weeklySessionsInput).trim());
                boolean unchanged = currentGoal.isConfigured()
                        && currentGoal.objective.equals(selectedObjective[0])
                        && currentGoal.weeklySessionsTarget == weeklySessions
                        && currentGoal.focusBodyPart.equals(selectedFocus[0]);
                String effectiveFrom = unchanged ? currentGoal.effectiveFrom : today();
                developmentRepository.saveDevelopmentGoal(new DevelopmentGoal(
                        selectedObjective[0],
                        weeklySessions,
                        selectedFocus[0],
                        effectiveFrom,
                        "",
                        ""
                ));
                render();
                return true;
            } catch (NumberFormatException error) {
                toast("주간 세션은 숫자로 입력해 주세요.");
                return false;
            } catch (IllegalArgumentException error) {
                toast(error.getMessage());
                return false;
            }
        });
    }

    @Override
    public void openDevelopmentInsightAction(DevelopmentInsight insight) {
        if (insight == null) {
            return;
        }
        if ("planning".equals(insight.category)) {
            showDevelopmentGoalDialog();
        } else if ("consistency".equals(insight.category) || "focus".equals(insight.category)) {
            navigate(FitnessScreen.WORKOUT);
        } else if ("recovery".equals(insight.category) || "nutrition_logging".equals(insight.category)) {
            openMealManagement(today(), FitnessScreen.DEVELOPMENT);
        } else if ("coverage".equals(insight.category) && insight.title.contains("체중")) {
            showDevelopmentBodyProfileDialog();
        } else if ("coverage".equals(insight.category)) {
            navigate(FitnessScreen.RECORDS);
        } else {
            toast("연결된 다음 행동이 아직 없습니다.");
        }
    }

    private static int indexOf(String[] values, String target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(target)) {
                return index;
            }
        }
        return -1;
    }

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    @Override
    public void createLocalBackup() {
        if (isDataTransferInProgress || isDataImporting) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
        openCreateDocument(
                "application/json",
                "fitness-os-backup-" + today() + ".json",
                REQUEST_LOCAL_BACKUP_EXPORT
        );
    }

    @Override
    public void restoreLocalBackup() {
        if (isDataTransferInProgress || isDataImporting) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_LOCAL_BACKUP_RESTORE);
        } catch (Exception error) {
            toast("백업 파일 선택기를 열지 못했습니다.");
        }
    }

    @Override
    public void exportRecordsCsv() {
        if (isDataTransferInProgress || isDataImporting) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
        openCreateDocument(
                "text/csv",
                "fitness-os-records-" + today() + ".csv",
                REQUEST_RECORDS_CSV_EXPORT
        );
    }

    @Override
    public void openWorkoutTransferImport() {
        if (isDataTransferInProgress || isDataImporting) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_WORKOUT_TRANSFER_IMPORT);
        } catch (Exception error) {
            toast("운동 전송 JSON 선택기를 열지 못했습니다.");
        }
    }

    @Override
    public void exportWorkoutTransfer() {
        if (isDataTransferInProgress || isDataImporting) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
        openCreateDocument(
                "application/json",
                "yeonsik-workout-transfer-" + today() + ".json",
                REQUEST_WORKOUT_TRANSFER_EXPORT
        );
    }

    @Override
    public boolean isDataTransferInProgress() {
        return isDataTransferInProgress;
    }

    @Override
    public String dataTransferDetail() {
        return dataTransferDetail;
    }

    private void openCreateDocument(String mimeType, String fileName, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception error) {
            toast("파일 저장 위치를 열지 못했습니다.");
        }
    }

    private LocalDataBackupService localDataBackupService() {
        return new LocalDataBackupService(
                databaseHelper,
                repository.currentUserId(),
                nutritionSupabaseConfig.effectiveUserId()
        );
    }

    private void writeLocalBackup(Uri uri) {
        beginDataTransfer("백업 파일을 만드는 중입니다.");
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
                }
                localDataBackupService().writeBackup(output);
                finishDataTransfer("전체 백업을 저장했습니다.", null);
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(error, "백업을 저장하지 못했습니다."));
            }
        });
    }

    private void previewLocalBackup(Uri uri) {
        beginDataTransfer("백업 파일을 확인하는 중입니다.");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("선택한 백업 파일을 읽을 수 없습니다.");
                }
                LocalDataBackupService.BackupPreview preview =
                        localDataBackupService().previewBackup(input);
                runOnUiThread(() -> {
                    isDataTransferInProgress = false;
                    dataTransferDetail = preview.getTotalRows() + "개 항목 확인됨";
                    render();
                    ui.confirmSheet(
                            "백업 복원",
                            preview.getTotalRows() + "개 항목을 현재 기록에 합칩니다. "
                                    + "기존 기록은 유지하고 같은 항목은 건너뜁니다.",
                            null,
                            "병합 복원",
                            () -> restoreLocalBackup(uri)
                    );
                });
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(error, "백업 파일을 확인하지 못했습니다."));
            }
        });
    }

    private void restoreLocalBackup(Uri uri) {
        beginDataTransfer("백업을 복원하는 중입니다.");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("선택한 백업 파일을 다시 읽을 수 없습니다.");
                }
                LocalDataBackupService.RestoreResult result =
                        localDataBackupService().restoreBackup(input);
                repository.reconcileSharedWorkoutSummaries();
                finishDataTransfer(
                        result.getImportedRows() + "개 복원 · "
                                + result.getSkippedRows() + "개 중복 건너뜀",
                        null
                );
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(error, "백업을 복원하지 못했습니다."));
            }
        });
    }

    private void writeRecordsCsv(Uri uri) {
        beginDataTransfer("기록 요약 CSV를 만드는 중입니다.");
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
                }
                localDataBackupService().writeRecordsSummaryCsv(output);
                finishDataTransfer("기록 요약 CSV를 저장했습니다.", null);
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(error, "CSV를 저장하지 못했습니다."));
            }
        });
    }

    private void writeWorkoutTransfer(Uri uri) {
        beginDataTransfer("운동 전송 JSON을 만드는 중입니다.");
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
                }
                new WorkoutTransferService(repository).writeJson(output);
                finishDataTransfer("Workout Transfer v2 JSON을 저장했습니다.", null);
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(
                        error,
                        "운동 전송 JSON을 저장하지 못했습니다."
                ));
            }
        });
    }

    private void importWorkoutTransfer(Uri uri) {
        beginDataTransfer("운동 전송 JSON을 읽고 기록을 합치는 중입니다.");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("선택한 운동 전송 파일을 읽을 수 없습니다.");
                }
                FitnessRepository.WorkoutTransferImportResult result =
                        new WorkoutTransferService(repository).importJson(input);
                repository.reconcileSharedWorkoutSummaries();
                finishDataTransfer(result.summary(), null);
            } catch (Exception error) {
                finishDataTransfer(null, dataTransferError(
                        error,
                        "운동 전송 JSON을 가져오지 못했습니다."
                ));
            }
        });
    }

    private void beginDataTransfer(String detail) {
        isDataTransferInProgress = true;
        dataTransferDetail = detail;
        render();
    }

    private void finishDataTransfer(String success, String failure) {
        runOnUiThread(() -> {
            isDataTransferInProgress = false;
            dataTransferDetail = failure == null ? success : failure;
            render();
            toast(dataTransferDetail);
        });
    }

    private static String dataTransferError(Exception error, String fallback) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return fallback;
        }
        return message.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣].*") ? message : fallback;
    }

    @Override
    public void openFleekDataImport() {
        if (isDataImporting || isDataTransferInProgress) {
            toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
            return;
        }
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
            applySharedSessionConfig(configStore.saveConnection(url, anonKey));
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
                    completeSharedAuthentication(
                            authenticated,
                            "Personal OS 공통 계정으로 로그인했습니다."
                    );
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
                        render();
                    } else {
                        completeSharedAuthentication(
                                result.config,
                                "Personal OS 공통 계정이 생성되고 로그인되었습니다."
                        );
                    }
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
        applySharedSessionConfig(configStore.clearSession());
        applySyncStatusFromConfig();
        toast("공통 계정에서 로그아웃했습니다. 영양 DB 세션은 유지됩니다.");
        render();
    }

    @Override
    public SupabaseConfig nutritionSupabaseConfig() {
        return nutritionSupabaseConfig;
    }

    @Override
    public boolean isNutritionSupabaseConnectionManaged() {
        return nutritionConfigStore.isConnectionManaged();
    }

    @Override
    public void saveNutritionSupabaseConfig(String url, String anonKey) {
        try {
            applyNutritionSessionConfig(nutritionConfigStore.saveConnection(url, anonKey));
            applySyncStatusFromConfig();
            toast("영양 전용 DB 설정을 저장했습니다.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            toast(error.getMessage());
        }
        render();
    }

    @Override
    public void signInToNutritionSupabase(String email, String password) {
        if (!nutritionSupabaseConfig.isConnectionConfigured()) {
            toast("영양 전용 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        syncLabel = "authenticating";
        syncDetail = "영양 DB 계정에 로그인하는 중입니다.";
        render();
        executor.execute(() -> {
            try {
                SupabaseConfig authenticated = nutritionAuthManager.signIn(
                        nutritionSupabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    completeNutritionAuthentication(
                            authenticated,
                            "영양 DB 계정으로 로그인했습니다."
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    syncLabel = "authentication failed";
                    syncDetail = error.getMessage() == null
                            ? "영양 DB 로그인에 실패했습니다."
                            : error.getMessage();
                    toast("영양 DB 로그인에 실패했습니다.");
                    render();
                });
            }
        });
    }

    @Override
    public void signUpToNutritionSupabase(String email, String password) {
        if (!nutritionSupabaseConfig.isConnectionConfigured()) {
            toast("영양 전용 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        syncLabel = "authenticating";
        syncDetail = "영양 DB 계정을 만드는 중입니다.";
        render();
        executor.execute(() -> {
            try {
                SupabaseAuthManager.SignUpResult result = nutritionAuthManager.signUp(
                        nutritionSupabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    if (result.emailConfirmationRequired) {
                        syncLabel = "confirmation required";
                        syncDetail = "영양 DB 가입 확인 메일을 확인한 뒤 로그인하세요.";
                        toast("영양 DB 가입 확인 메일을 보냈습니다.");
                        render();
                    } else {
                        completeNutritionAuthentication(
                                result.config,
                                "영양 DB 계정이 생성되고 로그인되었습니다."
                        );
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    syncLabel = "authentication failed";
                    syncDetail = error.getMessage() == null
                            ? "영양 DB 계정 생성에 실패했습니다."
                            : error.getMessage();
                    toast("영양 DB 계정 생성에 실패했습니다.");
                    render();
                });
            }
        });
    }

    @Override
    public void signOutFromNutritionSupabase() {
        applyNutritionSessionConfig(nutritionConfigStore.clearSession());
        applySyncStatusFromConfig();
        toast("영양 DB 계정에서 로그아웃했습니다. 공통 계정 세션은 유지됩니다.");
        render();
    }

    @Override
    public SupabaseConfig priceTraceSupabaseConfig() {
        return priceTraceSupabaseConfig;
    }

    @Override
    public boolean isPriceTraceSupabaseConnectionManaged() {
        return priceTraceConfigStore.isConnectionManaged();
    }

    @Override
    public void savePriceTraceSupabaseConfig(String url, String anonKey) {
        try {
            priceTraceSupabaseConfig = priceTraceConfigStore.saveConnection(url, anonKey);
            productReadClient.setConfig(priceTraceSupabaseConfig);
            restaurantMenuReadClient.setConfig(priceTraceSupabaseConfig);
            toast("PriceTrace DB 설정을 저장했습니다.");
        } catch (IllegalArgumentException | IllegalStateException error) {
            toast(error.getMessage());
        }
        render();
    }

    @Override
    public void signInToPriceTraceSupabase(String email, String password) {
        if (!priceTraceSupabaseConfig.isConnectionConfigured()) {
            toast("PriceTrace DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        executor.execute(() -> {
            try {
                SupabaseConfig authenticated = priceTraceAuthManager.signIn(
                        priceTraceSupabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    applyPriceTraceSessionConfig(authenticated);
                    toast("PriceTrace 계정으로 로그인했습니다.");
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast(error.getMessage() == null
                        ? "PriceTrace 로그인에 실패했습니다."
                        : error.getMessage()));
            }
        });
    }

    @Override
    public void signUpToPriceTraceSupabase(String email, String password) {
        if (!priceTraceSupabaseConfig.isConnectionConfigured()) {
            toast("PriceTrace DB 설정이 없습니다. 연결 설정을 먼저 확인하세요.");
            return;
        }
        executor.execute(() -> {
            try {
                SupabaseAuthManager.SignUpResult result = priceTraceAuthManager.signUp(
                        priceTraceSupabaseConfig,
                        email,
                        password
                );
                runOnUiThread(() -> {
                    if (result.emailConfirmationRequired) {
                        toast("PriceTrace 가입 확인 메일을 확인한 뒤 로그인하세요.");
                    } else {
                        applyPriceTraceSessionConfig(result.config);
                        toast("PriceTrace 계정이 생성되고 로그인되었습니다.");
                    }
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast(error.getMessage() == null
                        ? "PriceTrace 계정 생성에 실패했습니다."
                        : error.getMessage()));
            }
        });
    }

    @Override
    public void signOutFromPriceTraceSupabase() {
        applyPriceTraceSessionConfig(priceTraceConfigStore.clearSession());
        toast("PriceTrace 계정에서 로그아웃했습니다.");
        render();
    }

    @Override
    public void searchPriceTraceProducts(String query, ProductSearchCallback callback) {
        executor.execute(() -> {
            try {
                List<ProductReadV1> products = productReadClient.searchProducts(query);
                nutritionCatalogRepository.cachePriceTraceProducts(products);
                if (callback != null) {
                    callback.onComplete(products);
                }
            } catch (Exception error) {
                Log.w(
                        PRICE_TRACE_LOG_TAG,
                        "product-read.v1 search failed: " + error.getClass().getSimpleName(),
                        error
                );
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void loadPriceTraceProduct(String catalogProductId, ProductLoadCallback callback) {
        executor.execute(() -> {
            try {
                ProductReadV1 product = productReadClient.findProduct(catalogProductId);
                if (product != null) {
                    nutritionCatalogRepository.cachePriceTraceProducts(
                            java.util.Collections.singletonList(product)
                    );
                }
                if (callback != null) {
                    callback.onComplete(product);
                }
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void searchPriceTraceRestaurants(String query, RestaurantSearchCallback callback) {
        executor.execute(() -> {
            try {
                List<RestaurantMenuReadV1Client.RestaurantSummary> restaurants =
                        restaurantMenuReadClient.searchRestaurants(query);
                if (callback != null) {
                    callback.onComplete(restaurants);
                }
            } catch (Exception error) {
                Log.w(
                        PRICE_TRACE_LOG_TAG,
                        "restaurant-directory.v1 search failed: "
                                + error.getClass().getSimpleName(),
                        error
                );
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void loadPriceTraceRestaurant(String restaurantId, RestaurantLoadCallback callback) {
        executor.execute(() -> {
            try {
                RestaurantMenuReadV1Client.RestaurantDetail restaurant =
                        restaurantMenuReadClient.loadRestaurant(restaurantId);
                if (callback != null) {
                    callback.onComplete(restaurant);
                }
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void loadPublicProductNutrition(
            String catalogProductId,
            PublicNutritionCallback callback
    ) {
        executor.execute(() -> {
            try {
                NutritionCatalogRepository.PublicProductNutrition nutrition =
                        nutritionCatalogRepository.fetchPublicProductNutrition(catalogProductId);
                if (callback != null) {
                    callback.onComplete(nutrition);
                }
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void syncNutritionCatalog(NutritionCatalogRepository.SyncCallback callback) {
        executor.execute(() -> {
            try {
                SupabaseConfig activeConfig = nutritionSupabaseConfig;
                if (activeConfig.isConfigured()) {
                    activeConfig = nutritionAuthManager.refresh(activeConfig);
                    applyNutritionSessionConfig(activeConfig);
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
    public void setNutritionFoodPublication(
            String nutritionFoodId,
            String catalogProductId,
            boolean publish,
            NutritionCatalogRepository.PublicationCallback callback
    ) {
        executor.execute(() -> {
            try {
                SupabaseConfig activeConfig = nutritionSupabaseConfig;
                if (!activeConfig.isConfigured()) {
                    throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
                }
                activeConfig = nutritionAuthManager.refresh(activeConfig);
                applyNutritionSessionConfig(activeConfig);
                NutritionCatalogRepository.PublicationState state =
                        nutritionCatalogRepository.setProductNutritionPublication(
                                nutritionFoodId,
                                catalogProductId,
                                publish
                        );
                if (callback != null) {
                    callback.onComplete(state);
                }
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    @Override
    public void setDiningOutMenuPublication(
            String nutritionFoodId,
            boolean publish,
            NutritionCatalogRepository.PublicationCallback callback
    ) {
        executor.execute(() -> {
            try {
                SupabaseConfig activeNutritionConfig = nutritionSupabaseConfig;
                if (!activeNutritionConfig.isConfigured()) {
                    throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
                }
                activeNutritionConfig = nutritionAuthManager.refresh(activeNutritionConfig);
                applyNutritionSessionConfig(activeNutritionConfig);
                SupabaseConfig activePriceTraceConfig = priceTraceSupabaseConfig;
                if (publish) {
                    if (!activePriceTraceConfig.isConfigured()) {
                        throw new IllegalStateException("PT 관리자 계정 로그인이 필요합니다.");
                    }
                    activePriceTraceConfig = priceTraceAuthManager.refresh(activePriceTraceConfig);
                    applyPriceTraceSessionConfig(activePriceTraceConfig);
                }
                NutritionCatalogRepository.PublicationState state =
                        nutritionCatalogRepository.publishDiningOutMenuToPriceTrace(
                                nutritionFoodId,
                                publish,
                                activePriceTraceConfig
                        );
                if (callback != null) {
                    callback.onComplete(state);
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
        syncDetail = "공통 DB와 영양 DB를 각각 동기화하는 중입니다.";
        render();

        executor.execute(() -> {
            try {
                SupabaseConfig refreshedConfig = authManager.refresh(supabaseConfig);
                applySharedSessionConfig(refreshedConfig);
                SupabaseSyncManager.SyncResult result = syncManager.manualSync(refreshedConfig);

                NutritionCatalogRepository.CatalogSyncResult catalogResult =
                        new NutritionCatalogRepository.CatalogSyncResult(0, 0);
                boolean nutritionFailed = false;
                String nutritionStatus;
                if (!nutritionSupabaseConfig.isConnectionConfigured()) {
                    nutritionStatus = "영양 DB 연결 없음";
                } else {
                    try {
                        SupabaseConfig activeNutritionConfig = nutritionSupabaseConfig;
                        if (activeNutritionConfig.isConfigured()) {
                            activeNutritionConfig = nutritionAuthManager.refresh(activeNutritionConfig);
                            applyNutritionSessionConfig(activeNutritionConfig);
                        }
                        catalogResult = nutritionCatalogRepository.syncRemote();
                        nutritionStatus = "영양 DB push " + catalogResult.pushedRows
                                + "건 · pull " + catalogResult.pulledRows + "건";
                    } catch (Exception nutritionError) {
                        nutritionFailed = true;
                        nutritionStatus = "영양 DB 실패: "
                                + (nutritionError.getMessage() == null
                                ? "원격 동기화 오류"
                                : nutritionError.getMessage());
                    }
                }
                lastSyncedAt = result.syncedAt;
                final boolean completedWithNutritionFailure = nutritionFailed;
                final String completedNutritionStatus = nutritionStatus;
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = completedWithNutritionFailure ? "partial" : "synced";
                    syncDetail = "공통 DB push " + result.pushedRows + "건 · pull "
                            + result.pulledRows + "건 · " + completedNutritionStatus;
                    toast(completedWithNutritionFailure
                            ? "공통 DB 동기화는 완료했지만 영양 DB 동기화는 실패했습니다."
                            : "두 DB의 수동 동기화를 완료했습니다.");
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

    private void applyAuthenticatedSharedConfig(SupabaseConfig config) {
        supabaseConfig = config;
        String userId = config.effectiveUserId();
        repository.normalizeLocalUserId(userId);
        routineRepository.setUserId(userId);
        developmentRepository.normalizeLocalUserId(userId);
        supplementRepository.normalizeLocalUserId(userId);
    }

    private void completeSharedAuthentication(SupabaseConfig config, String successMessage) {
        try {
            applyAuthenticatedSharedConfig(config);
            applySyncStatusFromConfig();
            toast(successMessage);
        } catch (RuntimeException error) {
            applySharedSessionConfig(configStore.clearSession());
            syncLabel = "local ownership failed";
            syncDetail = error.getMessage() == null
                    ? "로컬 기록의 계정 귀속에 실패해 로그인을 취소했습니다."
                    : error.getMessage();
            toast("로컬 기록을 안전하게 연결하지 못해 로그인을 취소했습니다.");
        }
        render();
    }

    private void applySharedSessionConfig(SupabaseConfig config) {
        supabaseConfig = config;
        String userId = config.effectiveUserId();
        repository.setUserId(userId);
        routineRepository.setUserId(userId);
        developmentRepository.setUserId(userId);
        supplementRepository.setUserId(userId);
    }

    private void applyAuthenticatedNutritionConfig(SupabaseConfig config) {
        nutritionSupabaseConfig = config;
        String userId = config.effectiveUserId();
        nutritionCatalogRepository.normalizeLocalUserId(userId);
        nutritionCatalogRepository.setSupabaseConfig(config);
    }

    private void completeNutritionAuthentication(SupabaseConfig config, String successMessage) {
        try {
            applyAuthenticatedNutritionConfig(config);
            applySyncStatusFromConfig();
            toast(successMessage);
        } catch (RuntimeException error) {
            applyNutritionSessionConfig(nutritionConfigStore.clearSession());
            syncLabel = "nutrition ownership failed";
            syncDetail = error.getMessage() == null
                    ? "로컬 영양 데이터를 계정에 연결하지 못했습니다."
                    : error.getMessage();
            toast("영양 데이터를 안전하게 연결하지 못해 로그인을 취소했습니다.");
        }
        render();
    }

    private void applyNutritionSessionConfig(SupabaseConfig config) {
        nutritionSupabaseConfig = config;
        nutritionCatalogRepository.setUserId(config.effectiveUserId());
        nutritionCatalogRepository.setSupabaseConfig(config);
    }

    private void applyPriceTraceSessionConfig(SupabaseConfig config) {
        priceTraceSupabaseConfig = config;
        productReadClient.setConfig(config);
        restaurantMenuReadClient.setConfig(config);
    }

    private void applySyncStatusFromConfig() {
        if (supabaseConfig.isConfigured()) {
            syncLabel = lastSyncedAt.isEmpty() ? "configured" : "synced";
            syncDetail = lastSyncedAt.isEmpty()
                    ? "공통 DB 계정 연결됨 · " + nutritionConnectionSummary()
                    : "마지막 동기화 " + lastSyncedAt;
            return;
        }

        syncLabel = supabaseConfig.isConnectionConfigured() ? "login required" : "local-only";
        syncDetail = supabaseConfig.isConnectionConfigured()
                ? "공통 DB는 연결되었습니다. 공통 계정 로그인이 필요합니다. · "
                + nutritionConnectionSummary()
                : "Personal OS 공통 DB 설정이 없습니다. · " + nutritionConnectionSummary();
    }

    private String nutritionConnectionSummary() {
        if (nutritionSupabaseConfig.isConfigured()) {
            return "영양 DB 계정 연결됨";
        }
        if (nutritionSupabaseConfig.isConnectionConfigured()) {
            return "영양 DB 공개 카탈로그 연결됨";
        }
        return "영양 DB 연결 없음";
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
