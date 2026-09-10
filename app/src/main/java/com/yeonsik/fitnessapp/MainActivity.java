package com.yeonsik.fitnessapp;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.lifecycle.ViewModelProvider;

import com.yeonsik.fitnessapp.app.AppContainer;
import com.yeonsik.fitnessapp.app.SavedStateViewModelFactory;
import com.yeonsik.fitnessapp.app.navigation.AppNavigationViewModel;
import com.yeonsik.fitnessapp.app.navigation.AppViewModels;
import com.yeonsik.fitnessapp.app.navigation.ComposeAppScreen;
import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioTrackingService;
import com.yeonsik.fitnessapp.config.AppSurfacePolicy;
import com.yeonsik.fitnessapp.config.MassUnitPreferences;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.feature.body.ui.BodyMetricsViewModel;
import com.yeonsik.fitnessapp.ui.FitnessUi;
import com.yeonsik.fitnessapp.ui.AppUiActions;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRestTimerState;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionViewModel;
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryViewModel;
import com.yeonsik.fitnessapp.feature.home.ui.HomeViewModel;
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentViewModel;
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerViewModel;
import com.yeonsik.fitnessapp.feature.supplement.ui.SupplementViewModel;
import com.yeonsik.fitnessapp.feature.meal.ui.MealViewModel;
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsEvent;
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsUiState;
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsViewModel;
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumMap;

/**
 * 얇은 진입점: 의존성 초기화, 현재 화면 상태, 하단 내비게이션, 화면 간 공유 액션만 담당한다.
 * 화면 렌더링과 입력 상태는 feature ViewModel 기반 Compose 화면이 담당한다.
 */
public final class MainActivity extends ComponentActivity implements AppUiActions {

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
    private static final String STATE_SCREEN = "runtime.screen";
    private static final String STATE_RECORD_ID = "runtime.record_id";
    private static final String STATE_EXERCISE_ID = "runtime.exercise_id";
    private static final String STATE_REPLACEMENT_ID = "runtime.replacement_id";
    private static final String STATE_MEAL_DATE = "runtime.meal_date";
    private static final String STATE_RECORDS_DATE = "runtime.records_date";
    private static final String STATE_ROUTINE_ID = "runtime.routine_id";
    private static final String STATE_NAVIGATION_HISTORY = "runtime.navigation_history";

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

    private AppContainer appContainer;
    private WorkoutSessionViewModel workoutSessionViewModel;
    private WorkoutExerciseDetailViewModel workoutExerciseDetailViewModel;
    private CardioSessionViewModel cardioSessionViewModel;
    private RoutineEntryViewModel routineEntryViewModel;
    private HomeViewModel homeViewModel;
    private DevelopmentViewModel developmentViewModel;
    private BodyMetricsViewModel bodyMetricsViewModel;
    private SettingsViewModel settingsViewModel;
    private SupplementViewModel supplementViewModel;
    private ExercisePickerViewModel exercisePickerViewModel;
    private MealViewModel mealViewModel;
    private MassUnitPreferences massUnitPreferences;

    private FitnessUi ui;
    private OnBackInvokedCallback backInvokedCallback;
    private String themeMode = THEME_LIGHT;
    private AppNavigationViewModel navigationViewModel;

    private ComposeView rootView;

    private boolean isManualSyncing = false;
    private Uri pendingRestoreUri;
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";
    private CardioActivityType pendingCardioActivityType;
    private String pendingCardioResumeRecordId;
    private boolean pendingCardioFinishRequested;
    private boolean waitingForLocationSettings;
    private final DataTransferCoordinator dataTransferCoordinator = new DataTransferCoordinator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        massUnitPreferences = new MassUnitPreferences(this);
        appContainer = new AppContainer(this);
        themeMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_LIGHT);
        String startupOwnerId = currentOwnerId();
        initializeFeatureViewModels();
        navigationViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new AppNavigationViewModel(handle)
                )
        ).get(AppNavigationViewModel.class);
        settingsViewModel.reconcileSharedWorkoutSummaries(startupOwnerId);
        ui = new FitnessUi(this, this::isDarkTheme);
        registerBackCallback();
        restoreNavigationState(savedInstanceState);

        setContentView(buildRootView());
        configureWindow();
        handleDebugSessionProvisioning(getIntent());
        handleCardioIntent(getIntent());
    }


    private void restoreNavigationState(Bundle state) {
        if (state == null) {
            navigationViewModel.updateToday(today());
            return;
        }
        ArrayList<String> savedHistory = state.getStringArrayList(STATE_NAVIGATION_HISTORY);
        workoutSessionViewModel.rememberActiveRecord(state.getString(STATE_RECORD_ID));
        workoutExerciseDetailViewModel.rememberActiveExercise(state.getString(STATE_EXERCISE_ID));
        exercisePickerViewModel.rememberReplacementExercise(state.getString(STATE_REPLACEMENT_ID));
        navigationViewModel.restore(
                state.getString(STATE_SCREEN, navigationViewModel.currentScreen().name()),
                savedHistory,
                today(),
                state.getString(STATE_MEAL_DATE, navigationViewModel.selectedMealDate()),
                state.getString(STATE_RECORDS_DATE, navigationViewModel.selectedRecordsDate()),
                state.getString(STATE_ROUTINE_ID, navigationViewModel.selectedRoutineId())
        );
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SCREEN, navigationViewModel.currentScreen().name());
        outState.putString(STATE_RECORD_ID, workoutSessionViewModel.activeRecordId());
        outState.putString(STATE_EXERCISE_ID, workoutExerciseDetailViewModel.activeExerciseId());
        outState.putString(STATE_REPLACEMENT_ID, exercisePickerViewModel.activeReplacementId());
        outState.putString(STATE_MEAL_DATE, navigationViewModel.selectedMealDate());
        outState.putString(STATE_RECORDS_DATE, navigationViewModel.selectedRecordsDate());
        outState.putString(STATE_ROUTINE_ID, navigationViewModel.selectedRoutineId());
        outState.putStringArrayList(STATE_NAVIGATION_HISTORY, navigationViewModel.savedScreenNames());
        super.onSaveInstanceState(outState);
    }

    private void initializeFeatureViewModels() {
        settingsViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new SettingsViewModel(
                                handle,
                                massUnitPreferences,
                                appContainer.getConfigStore(),
                                appContainer.getNutritionConfigStore(),
                                appContainer.getPriceTraceConfigStore(),
                                appContainer.getSupabaseAuthManager(),
                                appContainer.getNutritionAuthManager(),
                                appContainer.getPriceTraceAuthManager(),
                                appContainer.getSyncApplicationService(),
                                appContainer.getLocalDataTransferApplicationService(),
                                appContainer
                        )
                )
        ).get(SettingsViewModel.class);
        settingsViewModel.setThemeMode(themeMode);
        settingsViewModel.getEvents().observe(this, this::handleSettingsEvent);
        bodyMetricsViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new BodyMetricsViewModel(
                                handle,
                                appContainer.getBodyMetricsApplicationService()
                        )
                )
        ).get(BodyMetricsViewModel.class);
        workoutSessionViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new WorkoutSessionViewModel(
                                handle,
                                appContainer.getWorkoutRepository(),
                                appContainer.getCompleteWorkout(),
                                appContainer.getWorkoutSessionApplicationService(),
                                appContainer.getWorkoutWriteExecutor(),
                                false
                        )
                )
        ).get(WorkoutSessionViewModel.class);
        workoutExerciseDetailViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new WorkoutExerciseDetailViewModel(
                                handle,
                                appContainer.getWorkoutRepository(),
                                appContainer.getInitializeWorkoutExercise(),
                                appContainer.getWorkoutWriteExecutor(),
                                false
                        )
                )
        ).get(WorkoutExerciseDetailViewModel.class);
        cardioSessionViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new CardioSessionViewModel(
                                handle,
                                appContainer.getCardioRepositoryApi(),
                                appContainer.getCardioSessionApplicationService()
                        )
                )
        ).get(CardioSessionViewModel.class);
        routineEntryViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new RoutineEntryViewModel(
                                handle,
                                appContainer.getRoutineRepositoryApi()
                        )
                )
        ).get(RoutineEntryViewModel.class);
        homeViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new HomeViewModel(
                                handle,
                                appContainer.getHomeRepository()
                        )
                )
        ).get(HomeViewModel.class);
        developmentViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new DevelopmentViewModel(
                                handle,
                                appContainer.getDevelopmentReportApi(),
                                appContainer.getDevelopmentApplicationService()
                        )
                )
        ).get(DevelopmentViewModel.class);
        supplementViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new SupplementViewModel(
                                handle,
                                appContainer.getSupplementRepositoryApi()
                        )
                )
        ).get(SupplementViewModel.class);
        exercisePickerViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new ExercisePickerViewModel(
                                handle,
                                appContainer.getExerciseMasterRepositoryApi(),
                                appContainer.getRoutineRepositoryApi(),
                                appContainer.getWorkoutRepository()
                        )
                )
        ).get(ExercisePickerViewModel.class);
        mealViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new MealViewModel(
                                handle,
                                appContainer.getMealRecordRepositoryApi(),
                                appContainer.getNutritionCatalogRepositoryApi(),
                                appContainer.getNutritionIntegrationService()
                        )
                )
        ).get(MealViewModel.class);
    }

    private void handleSettingsEvent(SettingsEvent event) {
        if (event == null || !event.consume()) {
            return;
        }
        if (event instanceof SettingsEvent.BackupPreviewReady) {
            SettingsEvent.BackupPreviewReady previewReady =
                    (SettingsEvent.BackupPreviewReady) event;
            LocalDataTransferApplicationService.BackupPreview preview = previewReady.getPreview();
            if (pendingRestoreUri != null) {
                Uri restoreUri = pendingRestoreUri;
                ui.confirmSheet(
                        "백업 복원",
                        preview.totalRows + "개 항목을 현재 기록에 합칩니다. "
                                + "기존 기록은 유지하고 같은 항목은 건너뜁니다.",
                        null,
                        "병합 복원",
                        () -> startRestoreBackup(restoreUri)
                );
            }
        } else if (event instanceof SettingsEvent.Notice) {
            toast(((SettingsEvent.Notice) event).getMessage());
            settingsViewModel.refresh();
        } else if (event instanceof SettingsEvent.Failure) {
            toast(((SettingsEvent.Failure) event).getMessage());
            settingsViewModel.refresh();
        } else if (event instanceof SettingsEvent.ConfigSaved) {
            toast(((SettingsEvent.ConfigSaved) event).getMessage());
            settingsViewModel.refresh();
        } else if (event instanceof SettingsEvent.Authenticated) {
            toast(((SettingsEvent.Authenticated) event).getMessage());
            settingsViewModel.refresh();
        } else if (event instanceof SettingsEvent.SignedOut) {
            toast(((SettingsEvent.SignedOut) event).getMessage());
            settingsViewModel.refresh();
        } else if (event instanceof SettingsEvent.SyncCompleted) {
            toast("수동 동기화 결과를 반영했습니다.");
            settingsViewModel.refresh();
        }
    }

    private void startRestoreBackup(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IOException("선택한 백업 파일을 다시 읽을 수 없습니다.");
            }
            pendingRestoreUri = null;
            settingsViewModel.restoreBackup(
                    currentOwnerId(),
                    appContainer.getNutritionSupabaseConfig().effectiveUserId(),
                    input
            );
        } catch (Exception error) {
            toast("백업 파일을 다시 읽지 못했습니다.");
        }
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
                && canProvisionDebugSession(appContainer.getSupabaseConfig(), userId)) {
                try {
                    appContainer.applyAuthenticatedSharedConfig(appContainer.getConfigStore().saveSession(
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
                && canProvisionDebugSession(appContainer.getNutritionSupabaseConfig(), nutritionUserId)) {
                try {
                    appContainer.applyAuthenticatedNutritionConfig(appContainer.getNutritionConfigStore().saveSession(
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
                && canProvisionDebugSession(appContainer.getPriceTraceSupabaseConfig(), priceTraceUserId)) {
            try {
                appContainer.applyPriceTraceSessionConfig(appContainer.getPriceTraceConfigStore().saveSession(
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
            toast("빌드 세션을 적용했습니다.");
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
        if (navigationViewModel != null) {
            String knownDate = navigationViewModel.getUiState().getValue() == null
                    ? null
                    : navigationViewModel.getUiState().getValue().getToday();
            if (!currentDate.equals(knownDate)) {
                navigationViewModel.updateToday(currentDate);
            }
        }
        if (waitingForLocationSettings && locationServicesEnabled()) {
            waitingForLocationSettings = false;
            continuePendingCardioAction();
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
                if (pendingCardioResumeRecordId == null && cardioSessionViewModel != null) {
                    cardioSessionViewModel.clearPendingStart();
                }
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
            dispatchFleekImport(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_EXPORT) {
            dispatchBackupExport(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_RESTORE) {
            dispatchBackupPreview(uri);
        } else if (requestCode == REQUEST_RECORDS_CSV_EXPORT) {
            dispatchRecordsCsvExport(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_IMPORT) {
            dispatchWorkoutTransferImport(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_EXPORT) {
            dispatchWorkoutTransferExport(uri);
        }
    }

    private void dispatchFleekImport(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("선택한 CSV 파일을 읽지 못했습니다.");
            settingsViewModel.importFleek(currentOwnerId(), input);
        } catch (Exception error) {
            toast("CSV 파일을 읽지 못했습니다.");
        }
    }

    private void dispatchBackupExport(Uri uri) {
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, "wt");
            if (output == null) throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
            settingsViewModel.writeBackup(
                    currentOwnerId(),
                    appContainer.getNutritionSupabaseConfig().effectiveUserId(),
                    output
            );
        } catch (Exception error) {
            toast("백업 파일을 열지 못했습니다.");
        }
    }

    private void dispatchBackupPreview(Uri uri) {
        try {
            pendingRestoreUri = uri;
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("선택한 백업 파일을 읽을 수 없습니다.");
            settingsViewModel.previewBackup(
                    currentOwnerId(),
                    appContainer.getNutritionSupabaseConfig().effectiveUserId(),
                    input
            );
        } catch (Exception error) {
            toast("백업 파일을 읽지 못했습니다.");
        }
    }

    private void dispatchRecordsCsvExport(Uri uri) {
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, "wt");
            if (output == null) throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
            settingsViewModel.writeRecordsCsv(
                    currentOwnerId(),
                    appContainer.getNutritionSupabaseConfig().effectiveUserId(),
                    output
            );
        } catch (Exception error) {
            toast("CSV 파일을 열지 못했습니다.");
        }
    }

    private void dispatchWorkoutTransferImport(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("선택한 운동 전송 파일을 읽을 수 없습니다.");
            settingsViewModel.importWorkoutTransfer(currentOwnerId(), input);
        } catch (Exception error) {
            toast("운동 전송 파일을 읽지 못했습니다.");
        }
    }

    private void dispatchWorkoutTransferExport(Uri uri) {
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, "wt");
            if (output == null) throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
            settingsViewModel.writeWorkoutTransfer(currentOwnerId(), output);
        } catch (Exception error) {
            toast("운동 전송 파일을 열지 못했습니다.");
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        if (!isChangingConfigurations() && appContainer != null) {
            appContainer.shutdownWorkoutWriteExecutor();
        }
        super.onDestroy();
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
        if (settingsViewModel != null) {
            settingsViewModel.setThemeMode(mode);
        }
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_THEME_MODE, mode).apply();
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
        if (settingsViewModel != null) {
            settingsViewModel.setPreferredMassUnit(unit);
        }
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
        ComposeView root = new ComposeView(this);
        rootView = root;
        root.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE
        );
        ComposeAppScreen.install(
                root,
                this,
                navigationViewModel,
                new AppViewModels(
                        workoutSessionViewModel,
                        bodyMetricsViewModel,
                        workoutExerciseDetailViewModel,
                        cardioSessionViewModel,
                        routineEntryViewModel,
                        homeViewModel,
                        developmentViewModel,
                        supplementViewModel,
                        exercisePickerViewModel,
                        mealViewModel,
                        settingsViewModel
                )
        );
        return root;
    }

    public void startRestTimer(Integer restSeconds) {
        workoutSessionViewModel.startRestTimer(currentOwnerId(), restSeconds);
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

    // ── Compose action boundary ────────────────────────────────────────

    @Override
    public String currentOwnerId() {
        return appContainer.getSupabaseConfig().effectiveUserId();
    }

    public String today() {
        return LocalDate.now().toString();
    }

    public FitnessScreen currentScreen() {
        return navigationViewModel.currentScreen();
    }

    @Override
    public void navigate(FitnessScreen screen) {
        navigationViewModel.navigate(screen);
    }

    @Override
    public boolean back() {
        if (!navigationViewModel.back()) {
            return false;
        }
        return true;
    }

    @Override
    public void replace(FitnessScreen screen) {
        navigationViewModel.replace(screen);
    }

    public WorkoutSessionViewModel workoutSessionViewModel() {
        return workoutSessionViewModel;
    }

    public WorkoutExerciseDetailViewModel workoutExerciseDetailViewModel() {
        return workoutExerciseDetailViewModel;
    }

    public CardioSessionViewModel cardioSessionViewModel() {
        return cardioSessionViewModel;
    }

    public RoutineEntryViewModel routineEntryViewModel() {
        return routineEntryViewModel;
    }

    public HomeViewModel homeViewModel() {
        return homeViewModel;
    }

    public DevelopmentViewModel developmentViewModel() {
        return developmentViewModel;
    }

    public SettingsViewModel settingsViewModel() {
        return settingsViewModel;
    }

    public SupplementViewModel supplementViewModel() {
        return supplementViewModel;
    }

    public ExercisePickerViewModel exercisePickerViewModel() {
        return exercisePickerViewModel;
    }

    public MealViewModel mealViewModel() {
        return mealViewModel;
    }

    @Override
    public String selectedRoutineId() {
        return navigationViewModel.selectedRoutineId();
    }

    @Override
    public void openWorkoutExerciseDetail(String exerciseId) {
        workoutExerciseDetailViewModel.rememberActiveExercise(exerciseId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
    }

    @Override
    public void openWorkoutExerciseReplacementPicker(String exerciseId) {
        exercisePickerViewModel.rememberReplacementExercise(exerciseId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_ADD);
    }

    @Override
    public void refreshWorkoutExerciseDetail() {
        String recordId = workoutSessionViewModel.activeRecordId();
        if (recordId != null) {
            workoutExerciseDetailViewModel.enter(
                    new AccountScope(currentOwnerId()),
                    recordId,
                    workoutExerciseDetailViewModel.activeExerciseId()
            );
            workoutSessionViewModel.enter(new AccountScope(currentOwnerId()), recordId);
        }
    }

    @Override
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void selectRoutine(String routineId) {
        navigationViewModel.selectRoutine(routineId);
    }

    @Override
    public void openWorkoutSession(String recordId) {
        workoutSessionViewModel.rememberActiveRecord(recordId);
        workoutExerciseDetailViewModel.clearActiveExercise();
        navigate(FitnessScreen.WORKOUT_SESSION);
    }

    public void openWorkoutExercisePicker() {
        String recordId = currentWorkoutRecordId();
        if (recordId == null) {
            toast("먼저 운동을 시작하세요.");
            return;
        }
        exercisePickerViewModel.clearReplacementExercise();
        workoutSessionViewModel.rememberActiveRecord(recordId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_ADD);
    }

    public void finishActiveWorkout() {
        String recordId = workoutSessionViewModel.activeRecordId();
        if (recordId == null) {
            toast("진행 중인 운동을 찾지 못했습니다.");
            return;
        }
        workoutSessionViewModel.finish(new AccountScope(currentOwnerId()), recordId);
    }

    @Override
    public String currentWorkoutRecordId() {
        FitnessScreen screen = currentScreen();
        boolean onSessionScreen = screen == FitnessScreen.WORKOUT_SESSION
                || screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
        if (onSessionScreen && workoutSessionViewModel != null) {
            return workoutSessionViewModel.activeRecordId();
        }
        if ((screen == FitnessScreen.CARDIO_SESSION || screen == FitnessScreen.CARDIO_SUMMARY)
                && cardioSessionViewModel != null) {
            return cardioSessionViewModel.activeRecordId();
        }
        if (screen == FitnessScreen.WORKOUT_EXERCISE_ADD && workoutSessionViewModel != null) {
            return workoutSessionViewModel.activeRecordId();
        }
        return homeViewModel == null ? null : homeViewModel.latestInProgressSessionId();
    }

    @Override
    public String currentWorkoutReplacementExerciseId() {
        return exercisePickerViewModel.activeReplacementId();
    }

    public void confirmDeleteSession(String recordId) {
        workoutSessionViewModel.openDeleteConfirmation(
                new AccountScope(currentOwnerId()), recordId
        );
    }

    /** Compatibility entry point for existing instrumentation fixtures. */
    public void startEmptyWorkout() {
        workoutSessionViewModel.startEmpty(new AccountScope(currentOwnerId()), today());
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
        if (FitnessScreen.HOME.equals(currentScreen()) && !navigationViewModel.canBack()) {
            // Only the initial HOME entry is allowed to finish the Activity.
            finish();
            return;
        }
        // Keep the exit invariant even if an external caller replaced the initial entry.
        navigationViewModel.replace(FitnessScreen.HOME);
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        dispatchBack();
    }

    @Override
    public void startCardioTracking(String recordId) {
        if (recordId == null) {
            return;
        }
        cardioSessionViewModel.rememberActiveRecord(recordId);
        dispatchCardioService(CardioTrackingService.ACTION_START, recordId, true);
    }

    @Override
    public void resumeCardioTracking(String recordId) {
        if (recordId == null) {
            return;
        }
        cardioSessionViewModel.rememberActiveRecord(recordId);
        dispatchCardioService(CardioTrackingService.ACTION_RESUME, recordId, true);
    }

    @Override
    public void pauseCardioTracking(String recordId) {
        if (recordId != null) {
            dispatchCardioService(CardioTrackingService.ACTION_PAUSE, recordId, false);
        }
    }

    @Override
    public void stopCardioTracking() {
        stopService(new Intent(this, CardioTrackingService.class));
    }

    @Override
    public void requestCardioStart(CardioActivityType activityType) {
        if (activityType == null) {
            toast("유산소 유형을 선택하세요.");
            return;
        }
        pendingCardioActivityType = activityType;
        pendingCardioResumeRecordId = null;
        requestCardioPermissionsAndContinue();
    }

    @Override
    public void requestCardioResume(CardioActivityType activityType, String recordId) {
        if (activityType == null || recordId == null) {
            toast("재개할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        pendingCardioActivityType = activityType;
        pendingCardioResumeRecordId = recordId;
        requestCardioPermissionsAndContinue();
    }

    @Override
    public boolean consumePendingCardioFinishRequest() {
        boolean requested = pendingCardioFinishRequested;
        pendingCardioFinishRequested = false;
        return requested;
    }

    @Override
    public void clearActiveWorkout(String recordId) {
        if (recordId == null) {
            return;
        }
        workoutSessionViewModel.clearActiveRecordIfMatches(recordId);
        cardioSessionViewModel.clearActiveRecordIfMatches(recordId);
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
        AccountScope scope = new AccountScope(currentOwnerId());
        if (resumeRecordId != null) {
            cardioSessionViewModel.resume(scope, resumeRecordId);
        } else {
            cardioSessionViewModel.startAfterPermissions(scope);
        }
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
        if (intent == null || cardioSessionViewModel == null) {
            return;
        }
        String recordId = intent.getStringExtra(CardioTrackingService.EXTRA_RECORD_ID);
        if (recordId == null) {
            return;
        }
        boolean finishRequested = intent.getBooleanExtra(
                CardioTrackingService.EXTRA_FINISH_REQUESTED, false);
        intent.removeExtra(CardioTrackingService.EXTRA_RECORD_ID);
        intent.removeExtra(CardioTrackingService.EXTRA_FINISH_REQUESTED);
        pendingCardioFinishRequested = finishRequested;
        cardioSessionViewModel.open(new AccountScope(currentOwnerId()), recordId);
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
    public void openMealManagement() {
        openMealManagement(today(), FitnessScreen.WORKOUT);
    }

    @Override
    public void openMealManagement(String date) {
        openMealManagement(date, FitnessScreen.WORKOUT);
    }

    @Override
    public void openMealManagement(String date, FitnessScreen returnScreen) {
        navigationViewModel.selectMealDate(date == null ? today() : date);
        navigate(FitnessScreen.MEALS);
    }

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    @Override
    public void createLocalBackup() {
        dataTransferCoordinator.createLocalBackup();
    }

    @Override
    public void restoreLocalBackup() {
        dataTransferCoordinator.restoreLocalBackup();
    }

    @Override
    public void exportRecordsCsv() {
        dataTransferCoordinator.exportRecordsCsv();
    }

    @Override
    public void openWorkoutTransferImport() {
        dataTransferCoordinator.openWorkoutTransferImport();
    }

    @Override
    public void exportWorkoutTransfer() {
        dataTransferCoordinator.exportWorkoutTransfer();
    }

    private boolean isDataTransferInProgress() {
        SettingsUiState state = settingsState();
        return state != null && state.isDataTransferInProgress();
    }

    private String dataTransferDetail() {
        SettingsUiState state = settingsState();
        return state == null ? "" : state.getDataTransferDetail();
    }























    @Override
    public void openFleekDataImport() {
        dataTransferCoordinator.openFleekDataImport();
    }



    private boolean isDataImporting() {
        SettingsUiState state = settingsState();
        return state != null && state.isDataImporting();
    }

    private String dataImportDetail() {
        SettingsUiState state = settingsState();
        return state == null ? "" : state.getDataImportDetail();
    }

    private SettingsUiState settingsState() {
        return settingsViewModel == null ? null : settingsViewModel.getUiState().getValue();
    }

    /**
     * Coordinates file import/export work while MainActivity retains only platform callbacks and
     * Compose action forwarding.
     */
    private final class DataTransferCoordinator {
        private void createLocalBackup() {
            if (isDataTransferInProgress() || isDataImporting()) {
                toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
                return;
            }
            openCreateDocument(
                    "application/json",
                    "fitness-os-backup-" + today() + ".json",
                    REQUEST_LOCAL_BACKUP_EXPORT
            );
        }

        private void restoreLocalBackup() {
            if (isDataTransferInProgress() || isDataImporting()) {
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

        private void exportRecordsCsv() {
            if (isDataTransferInProgress() || isDataImporting()) {
                toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
                return;
            }
            openCreateDocument(
                    "text/csv",
                    "fitness-os-records-" + today() + ".csv",
                    REQUEST_RECORDS_CSV_EXPORT
            );
        }

        private void openWorkoutTransferImport() {
            if (isDataTransferInProgress() || isDataImporting()) {
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

        private void exportWorkoutTransfer() {
            if (isDataTransferInProgress() || isDataImporting()) {
                toast("다른 데이터 작업이 끝난 뒤 다시 시도하세요.");
                return;
            }
            openCreateDocument(
                    "application/json",
                    "yeonsik-workout-transfer-" + today() + ".json",
                    REQUEST_WORKOUT_TRANSFER_EXPORT
            );
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

        private void openFleekDataImport() {
            if (isDataImporting() || isDataTransferInProgress()) {
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

    }

}
