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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.lifecycle.ViewModelProvider;

import com.yeonsik.fitnessapp.app.AppContainer;
import com.yeonsik.fitnessapp.app.SavedStateViewModelFactory;
import com.yeonsik.fitnessapp.app.navigation.ComposeAppScreen;
import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioTrackingService;
import com.yeonsik.fitnessapp.config.AppSurfacePolicy;
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore;
import com.yeonsik.fitnessapp.config.MassUnitPreferences;
import com.yeonsik.fitnessapp.config.PriceTraceSupabaseConfigStore;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.development.BodyProfile;
import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.development.DevelopmentInsight;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.FitnessNavigationHistory;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager;
import com.yeonsik.fitnessapp.feature.body.application.BodyMetricsApplicationService;
import com.yeonsik.fitnessapp.feature.body.ui.BodyMetricsEditorUiState;
import com.yeonsik.fitnessapp.feature.body.ui.BodyMetricsViewModel;
import com.yeonsik.fitnessapp.feature.development.application.DevelopmentApplicationService;
import com.yeonsik.fitnessapp.feature.workout.application.WorkoutSessionApplicationService;
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService;
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService;
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService;
import com.yeonsik.fitnessapp.ui.FitnessUi;
import com.yeonsik.fitnessapp.ui.ScreenHost;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailUiState;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionUiState;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRestTimerState;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionAction;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionActionEvent;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionActionOutcome;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionUiState;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionViewModel;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionAction;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionActionEvent;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionActionOutcome;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary;
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState;
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryViewModel;
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState;
import com.yeonsik.fitnessapp.feature.home.ui.HomeViewModel;
import com.yeonsik.fitnessapp.feature.home.ui.ComposeHomeScreen;
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentViewModel;
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentProfileEditorUiState;
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentGoalEditorUiState;
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerUiState;
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerViewModel;
import com.yeonsik.fitnessapp.feature.supplement.ui.SupplementViewModel;
import com.yeonsik.fitnessapp.feature.meal.ui.MealViewModel;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 얇은 진입점: 의존성 초기화, 현재 화면 상태, 하단 내비게이션, 화면 간 공유 액션만 담당한다.
 * 화면 렌더링과 입력 상태는 feature ViewModel 기반 Compose 화면이 담당한다.
 */
public final class MainActivity extends ComponentActivity implements ScreenHost {

    private enum Tab {
        HOME,
        WORKOUT,
        RECORDS,
        DEVELOPMENT,
        SETTINGS
    }
    private static final class ComposeEntry {
        final ComposeView view;
        final String configurationKey;

        ComposeEntry(ComposeView view, String configurationKey) {
            this.view = view;
            this.configurationKey = configurationKey;
        }
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
    private static final String STATE_SCREEN = "runtime.screen";
    private static final String STATE_RECORD_ID = "runtime.record_id";
    private static final String STATE_EXERCISE_ID = "runtime.exercise_id";
    private static final String STATE_REPLACEMENT_ID = "runtime.replacement_id";
    private static final String STATE_INPUT_UNIT = "runtime.input_unit";
    private static final String STATE_MEAL_DATE = "runtime.meal_date";
    private static final String STATE_ROUTINE_ID = "runtime.routine_id";
    private static final String STATE_NAVIGATION_HISTORY = "runtime.navigation_history";
    private static final int COMPOSE_VIEW_ID_BASE = 0x6f100000;

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
    private String selectedMealDate = lastKnownDate;
    private FitnessScreen mealReturnScreen = FitnessScreen.WORKOUT;
    private String selectedRoutineId;

    private AppContainer appContainer;
    private WorkoutSessionApplicationService workoutSessionApplicationService;
    private NutritionIntegrationService nutritionIntegrationService;
    private SyncApplicationService syncApplicationService;
    private LocalDataTransferApplicationService localDataTransferApplicationService;
    private WorkoutSessionViewModel workoutSessionViewModel;
    private WorkoutExerciseDetailViewModel workoutExerciseDetailViewModel;
    private CardioSessionViewModel cardioSessionViewModel;
    private RoutineEntryViewModel routineEntryViewModel;
    private HomeViewModel homeViewModel;
    private DevelopmentViewModel developmentViewModel;
    private BodyMetricsViewModel bodyMetricsViewModel;
    private SupplementViewModel supplementViewModel;
    private ExercisePickerViewModel exercisePickerViewModel;
    private MealViewModel mealViewModel;
    private SupabaseConfigStore configStore;
    private NutritionSupabaseConfigStore nutritionConfigStore;
    private PriceTraceSupabaseConfigStore priceTraceConfigStore;
    private MassUnitPreferences massUnitPreferences;
    private SupabaseAuthManager authManager;
    private SupabaseAuthManager nutritionAuthManager;
    private SupabaseAuthManager priceTraceAuthManager;
    private SupabaseConfig supabaseConfig;
    private SupabaseConfig nutritionSupabaseConfig;
    private SupabaseConfig priceTraceSupabaseConfig;

    private FitnessUi ui;
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
    // Cached only for the retained legacy View chrome. Ownership lives in WorkoutSessionViewModel.
    private long restEndsAtMillis;
    private int restTotalSeconds;
    private int lastPulsedSecond = -1;
    private FitnessScreen lastRenderedScreen;
    private final EnumMap<FitnessScreen, ComposeEntry> composeEntries =
            new EnumMap<>(FitnessScreen.class);
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
    private String knownInProgressRecordId;
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
        configStore = appContainer.getConfigStore();
        nutritionConfigStore = appContainer.getNutritionConfigStore();
        priceTraceConfigStore = appContainer.getPriceTraceConfigStore();
        supabaseConfig = appContainer.getSupabaseConfig();
        nutritionSupabaseConfig = appContainer.getNutritionSupabaseConfig();
        priceTraceSupabaseConfig = appContainer.getPriceTraceSupabaseConfig();
        authManager = appContainer.getSupabaseAuthManager();
        nutritionAuthManager = appContainer.getNutritionAuthManager();
        priceTraceAuthManager = appContainer.getPriceTraceAuthManager();
        workoutSessionApplicationService = appContainer.getWorkoutSessionApplicationService();
        nutritionIntegrationService = appContainer.getNutritionIntegrationService();
        syncApplicationService = appContainer.getSyncApplicationService();
        localDataTransferApplicationService =
                appContainer.getLocalDataTransferApplicationService();
        String startupOwnerId = currentOwnerId();
        initializeFeatureViewModels();
        executor.execute(() -> localDataTransferApplicationService
                .reconcileSharedWorkoutSummaries(startupOwnerId));
        applySyncStatusFromConfig();

        themeMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_LIGHT);
        ui = new FitnessUi(this, this::isDarkTheme);
        registerBackCallback();
        restoreNavigationState(savedInstanceState);

        setContentView(buildRootView());
        configureWindow();
        render();
        handleDebugSessionProvisioning(getIntent());
        handleCardioIntent(getIntent());
    }


    private void restoreNavigationState(Bundle state) {
        if (state == null) return;
        try {
            currentScreen = FitnessScreen.valueOf(
                    state.getString(STATE_SCREEN, FitnessScreen.HOME.name()));
        } catch (IllegalArgumentException ignored) {
            currentScreen = FitnessScreen.HOME;
        }
        ArrayList<String> savedHistory = state.getStringArrayList(STATE_NAVIGATION_HISTORY);
        try {
            navigationHistory.restoreScreenNames(savedHistory);
            currentScreen = navigationHistory.current();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            navigationHistory.restoreCurrent(currentScreen);
        }
        sessionState.setActiveRecordId(state.getString(STATE_RECORD_ID));
        sessionState.setActiveExerciseId(state.getString(STATE_EXERCISE_ID));
        sessionState.setReplacementExerciseId(state.getString(STATE_REPLACEMENT_ID));
        MassUnit inputUnit = MassUnit.parse(state.getString(STATE_INPUT_UNIT));
        if (inputUnit != null) sessionState.setSessionInputMassUnit(inputUnit);
        selectedMealDate = state.getString(STATE_MEAL_DATE, lastKnownDate);
        selectedRoutineId = state.getString(STATE_ROUTINE_ID);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SCREEN, currentScreen.name());
        outState.putString(STATE_RECORD_ID, sessionState.activeRecordId());
        outState.putString(STATE_EXERCISE_ID, sessionState.activeExerciseId());
        outState.putString(STATE_REPLACEMENT_ID, sessionState.replacementExerciseId());
        MassUnit inputUnit = sessionState.sessionInputMassUnit();
        outState.putString(STATE_INPUT_UNIT, inputUnit == null ? null : inputUnit.id());
        outState.putString(STATE_MEAL_DATE, selectedMealDate);
        outState.putString(STATE_ROUTINE_ID, selectedRoutineId);
        outState.putStringArrayList(STATE_NAVIGATION_HISTORY, navigationHistory.savedScreenNames());
        super.onSaveInstanceState(outState);
    }

    private void initializeFeatureViewModels() {
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
        bodyMetricsViewModel.getEditorState().observe(this, state -> {
            if (state instanceof BodyMetricsEditorUiState.Ready) {
                BodyMetricsEditorUiState.Ready ready = (BodyMetricsEditorUiState.Ready) state;
                if (ready.getOwnerId().equals(currentOwnerId())) {
                    showBodyMetricDialogForm(ready.getOwnerId(), ready.getEditor());
                }
            } else if (state instanceof BodyMetricsEditorUiState.Saved
                    || state instanceof BodyMetricsEditorUiState.Deleted) {
                render();
            } else if (state instanceof BodyMetricsEditorUiState.Error) {
                BodyMetricsEditorUiState.Error error = (BodyMetricsEditorUiState.Error) state;
                if (error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
        workoutSessionViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new WorkoutSessionViewModel(
                                handle,
                                appContainer.getWorkoutRepository(),
                                appContainer.getCompleteWorkout(),
                                appContainer.getWorkoutSessionApplicationService()
                        )
                )
        ).get(WorkoutSessionViewModel.class);
        workoutSessionViewModel.getUiState().observe(this, state -> {
            if (currentScreen != FitnessScreen.WORKOUT_SESSION) {
                return;
            }
            if (state instanceof WorkoutSessionUiState.Ready) {
                WorkoutSessionUiState.Ready ready = (WorkoutSessionUiState.Ready) state;
                if (ready.getOwnerId().equals(currentOwnerId())
                        && ready.getSession().getRecordId().equals(sessionState.activeRecordId())) {
                    rerender();
                }
            } else if (state instanceof WorkoutSessionUiState.Missing) {
                WorkoutSessionUiState.Missing missing = (WorkoutSessionUiState.Missing) state;
                if (missing.getOwnerId().equals(currentOwnerId())
                        && missing.getRecordId().equals(sessionState.activeRecordId())) {
                    sessionState.clearIfMatches(missing.getRecordId());
                    replace(FitnessScreen.STRENGTH);
                }
            } else if (state instanceof WorkoutSessionUiState.Completed) {
                WorkoutSessionUiState.Completed completed = (WorkoutSessionUiState.Completed) state;
                if (completed.getOwnerId().equals(currentOwnerId())
                        && completed.getRecordId().equals(sessionState.activeRecordId())) {
                    knownInProgressRecordId = null;
                    toast("운동을 완료했습니다.");
                    replace(FitnessScreen.WORKOUT_SUMMARY);
                }
            } else if (state instanceof WorkoutSessionUiState.DiscardedEmptySession) {
                WorkoutSessionUiState.DiscardedEmptySession discarded =
                        (WorkoutSessionUiState.DiscardedEmptySession) state;
                if (discarded.getOwnerId().equals(currentOwnerId())
                        && discarded.getRecordId().equals(sessionState.activeRecordId())) {
                    knownInProgressRecordId = null;
                    sessionState.clearIfMatches(discarded.getRecordId());
                    toast("수행한 세트가 없어 운동을 저장하지 않았습니다.");
                    replace(FitnessScreen.STRENGTH);
                }
            } else if (state instanceof WorkoutSessionUiState.Error) {
                WorkoutSessionUiState.Error error = (WorkoutSessionUiState.Error) state;
                if (error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
        workoutSessionViewModel.getActionState().observe(this, this::handleWorkoutSessionAction);
        workoutSessionViewModel.getRestTimerState().observe(this, state -> {
            if (state instanceof WorkoutRestTimerState.Active
                    && currentOwnerId().equals(((WorkoutRestTimerState.Active) state).getOwnerId())) {
                WorkoutRestTimerState.Active active = (WorkoutRestTimerState.Active) state;
                restEndsAtMillis = active.getEndsAtMillis();
                restTotalSeconds = active.getTotalSeconds();
                if (restTimerBar != null && restTimerVisibleOnScreen()) {
                    populateRestTimerBar();
                    restTimerBar.setVisibility(View.VISIBLE);
                    updateRestTimerBar();
                    restTimerBar.removeCallbacks(restTick);
                    restTimerBar.postDelayed(restTick, 250);
                }
            } else {
                restEndsAtMillis = 0L;
                restTotalSeconds = 0;
                if (restTimerBar != null) {
                    restTimerBar.removeCallbacks(restTick);
                    restTimerBar.setVisibility(View.GONE);
                }
            }
        });
        workoutExerciseDetailViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new WorkoutExerciseDetailViewModel(
                                handle,
                                appContainer.getWorkoutRepository(),
                                appContainer.getInitializeWorkoutExercise()
                        )
                )
        ).get(WorkoutExerciseDetailViewModel.class);
        workoutExerciseDetailViewModel.getUiState().observe(this, state -> {
            if (state instanceof WorkoutExerciseDetailUiState.Ready) {
                WorkoutExerciseDetailUiState.Ready ready =
                        (WorkoutExerciseDetailUiState.Ready) state;
                if (currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL
                        && ready.getOwnerId().equals(currentOwnerId())
                        && ready.getDetail().getRecordId().equals(sessionState.activeRecordId())) {
                    sessionState.setActiveExerciseId(ready.getDetail().getActiveExercise().id);
                    // Compose observes UiState. Replacing its View here discards unsaved inputs.
                }
            }
        });
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
        cardioSessionViewModel.getUiState().observe(this, state -> {
            if (state instanceof CardioSessionUiState.Ready) {
                CardioSessionUiState.Ready ready = (CardioSessionUiState.Ready) state;
                if ((currentScreen == FitnessScreen.CARDIO_SESSION
                        || currentScreen == FitnessScreen.CARDIO_SUMMARY)
                        && ready.getOwnerId().equals(currentOwnerId())
                        && ready.getSession().getRecordId().equals(sessionState.activeRecordId())) {
                    if (currentScreen == FitnessScreen.CARDIO_SUMMARY) {
                        rerender();
                    }
                }
            } else if (state instanceof CardioSessionUiState.Missing) {
                CardioSessionUiState.Missing missing = (CardioSessionUiState.Missing) state;
                if (currentScreen == FitnessScreen.CARDIO_SESSION
                        && missing.getOwnerId().equals(currentOwnerId())
                        && missing.getRecordId().equals(sessionState.activeRecordId())) {
                    replace(FitnessScreen.CARDIO);
                }
            }
        });
        cardioSessionViewModel.getActionState().observe(this, this::handleCardioSessionAction);
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
        routineEntryViewModel.getUiState().observe(this, state -> {
            if (state instanceof RoutineEntryUiState.Ready) {
                RoutineEntryUiState.Ready ready = (RoutineEntryUiState.Ready) state;
                if ((currentScreen == FitnessScreen.STRENGTH || currentScreen == FitnessScreen.HOME)
                        && ready.getOwnerId().equals(currentOwnerId())) {
                    if (ready.getNotice() != null) {
                        homeViewModel.enter(new AccountScope(currentOwnerId()), today());
                    }
                    if (currentScreen != FitnessScreen.HOME) {
                        rerender();
                    }
                }
            } else if (state instanceof RoutineEntryUiState.Error) {
                RoutineEntryUiState.Error error = (RoutineEntryUiState.Error) state;
                if ((currentScreen == FitnessScreen.STRENGTH || currentScreen == FitnessScreen.HOME)
                        && error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
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
        homeViewModel.getUiState().observe(this, state -> {
            if (state instanceof HomeUiState.Ready) {
                HomeUiState.Ready ready = (HomeUiState.Ready) state;
                if (currentScreen == FitnessScreen.HOME
                        && ready.getSnapshot().getOwnerId().equals(currentOwnerId())
                        && ready.getSnapshot().getToday().equals(today())) {
                    // Compose observes this state itself. Rebuilding the whole
                    // view tree here would interrupt any pending accessibility
                    // focus without adding a new side effect.
                }
            } else if (state instanceof HomeUiState.Error) {
                HomeUiState.Error error = (HomeUiState.Error) state;
                if (currentScreen == FitnessScreen.HOME
                        && error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
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
        developmentViewModel.getProfileEditorState().observe(this, state -> {
            if (state instanceof DevelopmentProfileEditorUiState.Ready) {
                DevelopmentProfileEditorUiState.Ready ready =
                        (DevelopmentProfileEditorUiState.Ready) state;
                if (ready.getOwnerId().equals(currentOwnerId())) {
                    showDevelopmentBodyProfileDialogForm(
                            ready.getOwnerId(),
                            ready.getDate(),
                            ready.getEditor()
                    );
                }
            } else if (state instanceof DevelopmentProfileEditorUiState.Saved) {
                render();
            } else if (state instanceof DevelopmentProfileEditorUiState.Error) {
                DevelopmentProfileEditorUiState.Error error =
                        (DevelopmentProfileEditorUiState.Error) state;
                if (error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
        developmentViewModel.getGoalEditorState().observe(this, state -> {
            if (state instanceof DevelopmentGoalEditorUiState.Ready) {
                DevelopmentGoalEditorUiState.Ready ready =
                        (DevelopmentGoalEditorUiState.Ready) state;
                if (ready.getOwnerId().equals(currentOwnerId())) {
                    showDevelopmentGoalDialogForm(ready.getOwnerId(), ready.getGoal());
                }
            } else if (state instanceof DevelopmentGoalEditorUiState.Saved) {
                render();
            } else if (state instanceof DevelopmentGoalEditorUiState.Error) {
                DevelopmentGoalEditorUiState.Error error =
                        (DevelopmentGoalEditorUiState.Error) state;
                if (error.getOwnerId().equals(currentOwnerId())) {
                    toast(error.getMessage());
                }
            }
        });
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
        exercisePickerViewModel.getUiState().observe(this, state -> {
            if (!(state instanceof ExercisePickerUiState.Saved)) {
                return;
            }
            ExercisePickerUiState.Saved saved = (ExercisePickerUiState.Saved) state;
            // LiveData replays Saved after recreation. Only the picker may consume its navigation result.
            if (!saved.getOwnerId().equals(currentOwnerId()) || currentScreen != saved.getMode()) {
                return;
            }
            if (saved.getMode() == FitnessScreen.ROUTINE_ADD) {
                homeViewModel.enter(new AccountScope(currentOwnerId()), today());
            } else {
                String recordId = sessionState.activeRecordId();
                if (recordId != null) {
                    workoutSessionViewModel.enter(new AccountScope(currentOwnerId()), recordId);
                    workoutExerciseDetailViewModel.enter(
                            new AccountScope(currentOwnerId()),
                            recordId,
                            sessionState.activeExerciseId()
                    );
                }
                sessionState.clearExerciseReplacement();
            }
            back();
        });
        mealViewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory<>(
                        this,
                        null,
                        handle -> new MealViewModel(
                                handle,
                                appContainer.getMealRecordRepositoryApi(),
                                appContainer.getNutritionCatalogRepositoryApi()
                        )
                )
        ).get(MealViewModel.class);
    }

    /**
     * Applies one-shot workout action results. The ViewModel owns the database work; this
     * Activity only performs platform navigation, confirmation feedback, and legacy chrome
     * updates until U3 moves those concerns into the Compose root.
     */
    private void handleWorkoutSessionAction(WorkoutSessionActionEvent event) {
        if (event == null
                || !currentOwnerId().equals(event.getOwnerId())
                || !event.consume()) {
            return;
        }
        WorkoutSessionActionOutcome outcome = event.getOutcome();
        if (outcome == WorkoutSessionActionOutcome.FAILURE
                || outcome == WorkoutSessionActionOutcome.NONE) {
            toast(event.getMessage() == null
                    ? "운동 작업을 완료하지 못했습니다."
                    : event.getMessage());
            return;
        }

        String recordId = event.getRecordId();
        if (recordId == null) {
            toast("운동 기록을 찾지 못했습니다.");
            return;
        }

        if (outcome == WorkoutSessionActionOutcome.OPEN_EXISTING) {
            knownInProgressRecordId = recordId;
            if (event.getMessage() != null) {
                toast(event.getMessage());
            }
            if (event.getAction() == WorkoutSessionAction.OPEN_RECORD) {
                if (event.getCardioSession()) {
                    openCardioSummary(recordId);
                } else {
                    openWorkoutSession(recordId);
                }
            } else if (event.getCardioSession()) {
                openCardioSession(recordId);
            } else {
                openWorkoutSession(recordId);
            }
            return;
        }

        if (outcome == WorkoutSessionActionOutcome.CREATED) {
            knownInProgressRecordId = recordId;
            if (event.getAction() == WorkoutSessionAction.START_ROUTINE) {
                toast("루틴 운동을 시작했습니다.");
            } else if (event.getAction() == WorkoutSessionAction.START_MANUAL_PAST) {
                toast("세트와 횟수를 입력한 뒤 운동 완료를 누르세요.");
            }
            openWorkoutSession(recordId);
            return;
        }

        if (outcome == WorkoutSessionActionOutcome.DELETED) {
            if (recordId.equals(knownInProgressRecordId)) {
                knownInProgressRecordId = null;
            }
            sessionState.clearIfMatches(recordId);
            toast("운동 기록을 삭제했습니다.");
            if (currentScreen == FitnessScreen.RECORDS) {
                render();
            } else {
                replace(event.getCardioSession()
                        ? FitnessScreen.CARDIO
                        : FitnessScreen.STRENGTH);
            }
        }
    }

    /** Applies one-shot cardio results while keeping GPS permission/service work platform-owned. */
    private void handleCardioSessionAction(CardioSessionActionEvent event) {
        if (event == null
                || !currentOwnerId().equals(event.getOwnerId())
                || !event.consume()) {
            return;
        }
        CardioSessionActionOutcome outcome = event.getOutcome();
        if (outcome == CardioSessionActionOutcome.FAILURE
                || outcome == CardioSessionActionOutcome.NOT_FOUND) {
            toast(event.getMessage() == null
                    ? "유산소 작업을 완료하지 못했습니다."
                    : event.getMessage());
            return;
        }

        String recordId = event.getRecordId();
        CardioSessionSnapshot session = event.getSession();
        switch (event.getAction()) {
            case OPEN:
                openCardioSessionLoaded(session);
                if (pendingCardioFinishRequested) {
                    pendingCardioFinishRequested = false;
                    if (session != null
                            && !CardioSessionSnapshot.STATUS_COMPLETED.equals(session.getStatus())) {
                        finishCardioWorkout();
                    }
                }
                return;
            case START:
                if (session == null || recordId == null) {
                    toast("유산소 기록을 시작하지 못했습니다.");
                    return;
                }
                knownInProgressRecordId = recordId;
                sessionState.setActiveRecordId(recordId);
                sessionState.setActiveExerciseId(null);
                dispatchCardioService(CardioTrackingService.ACTION_START, recordId, true);
                toast(cardioActivityType(session).labelKo() + " 기록을 시작했습니다.");
                navigate(FitnessScreen.CARDIO_SESSION);
                return;
            case PREPARE_RESUME:
                if (session == null || recordId == null) {
                    toast("재개할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                pendingCardioActivityType = cardioActivityType(session);
                pendingCardioResumeRecordId = recordId;
                requestCardioPermissionsAndContinue();
                return;
            case RESUME:
                if (recordId == null) {
                    toast("재개할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                knownInProgressRecordId = recordId;
                sessionState.setActiveRecordId(recordId);
                sessionState.setActiveExerciseId(null);
                dispatchCardioService(CardioTrackingService.ACTION_RESUME, recordId, true);
                toast("GPS 기록을 재개했습니다.");
                navigate(FitnessScreen.CARDIO_SESSION);
                return;
            case PAUSE:
                if (recordId == null) {
                    toast("일시정지할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                dispatchCardioService(CardioTrackingService.ACTION_PAUSE, recordId, false);
                toast("GPS 기록을 일시정지했습니다.");
                cardioSessionViewModel.refresh(new AccountScope(currentOwnerId()), recordId);
                return;
            case PREPARE_FINISH:
                if (outcome == CardioSessionActionOutcome.COMPLETED) {
                    openCardioSessionLoaded(session);
                    return;
                }
                if (recordId == null || session == null) {
                    toast("완료할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                if (event.getPausedByFinish()) {
                    dispatchCardioService(CardioTrackingService.ACTION_PAUSE, recordId, false);
                    render();
                }
                showCardioHeartRateSheet(recordId, true, session);
                return;
            case FINISH:
                if (recordId == null || session == null) {
                    toast("평균 심박수를 저장하지 못했습니다.");
                    return;
                }
                knownInProgressRecordId = null;
                stopService(new Intent(this, CardioTrackingService.class));
                toast("유산소 운동을 완료했습니다.");
                sessionState.setActiveRecordId(recordId);
                sessionState.setActiveExerciseId(null);
                if (currentScreen == FitnessScreen.CARDIO_SESSION) {
                    replace(FitnessScreen.CARDIO_SUMMARY);
                } else {
                    navigate(FitnessScreen.CARDIO_SUMMARY);
                }
                return;
            case PREPARE_HEART_RATE_EDIT:
                if (recordId == null || session == null) {
                    toast("수정할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                showCardioHeartRateSheet(recordId, false, session);
                return;
            case UPDATE_HEART_RATE:
                if (recordId == null || session == null) {
                    toast("평균 심박수를 저장하지 못했습니다.");
                    return;
                }
                toast("평균 심박수를 저장했습니다.");
                cardioSessionViewModel.refresh(new AccountScope(currentOwnerId()), recordId);
                return;
            case PREPARE_CANCEL:
                if (recordId == null || session == null) {
                    toast("취소할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                ui.confirmSheet(
                        "유산소 기록 취소",
                        "현재 " + CardioMetrics.formatDistanceKilometers(
                                session.getDistanceMeters()
                        ) + "km 기록을 저장하지 않습니다.",
                        "이 기기에 저장된 GPS 좌표도 함께 삭제됩니다.",
                        "기록 취소",
                        () -> {
                            stopService(new Intent(this, CardioTrackingService.class));
                            cardioSessionViewModel.cancel(
                                    new AccountScope(currentOwnerId()), recordId
                            );
                        }
                );
                return;
            case CANCEL:
                if (recordId == null) {
                    toast("취소할 유산소 기록을 찾지 못했습니다.");
                    return;
                }
                stopService(new Intent(this, CardioTrackingService.class));
                if (recordId.equals(knownInProgressRecordId)) {
                    knownInProgressRecordId = null;
                }
                sessionState.clearIfMatches(recordId);
                toast("유산소 기록을 취소했습니다.");
                replace(FitnessScreen.CARDIO);
                return;
            case LOAD_ROUTE:
                // The route callback adapter is migrated in the next cardio UI boundary step.
                return;
            default:
                return;
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
            lastKnownDate = currentDate;
            if (FitnessScreen.MEALS != currentScreen) {
                selectedMealDate = currentDate;
            }
            render();
        }
        if (waitingForLocationSettings && locationServicesEnabled()) {
            waitingForLocationSettings = false;
            continuePendingCardioAction();
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
            dataTransferCoordinator.importFleekCsv(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_EXPORT) {
            dataTransferCoordinator.writeLocalBackup(uri);
        } else if (requestCode == REQUEST_LOCAL_BACKUP_RESTORE) {
            dataTransferCoordinator.previewLocalBackup(uri);
        } else if (requestCode == REQUEST_RECORDS_CSV_EXPORT) {
            dataTransferCoordinator.writeRecordsCsv(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_IMPORT) {
            dataTransferCoordinator.importWorkoutTransfer(uri);
        } else if (requestCode == REQUEST_WORKOUT_TRANSFER_EXPORT) {
            dataTransferCoordinator.writeWorkoutTransfer(uri);
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        executor.shutdownNow();
        for (ComposeEntry entry : composeEntries.values()) {
            entry.view.disposeComposition();
        }
        composeEntries.clear();
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
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
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
        lastPulsedSecond = -1;
        workoutSessionViewModel.startRestTimer(currentOwnerId(), restSeconds);
    }

    private void stopRestTimer() {
        workoutSessionViewModel.stopRestTimer();
        restEndsAtMillis = 0;
        restTotalSeconds = 0;
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
        // Let longer labels take their measured width at accessibility font sizes.
        int width = getResources().getConfiguration().fontScale >= 1.3f
                ? LinearLayout.LayoutParams.WRAP_CONTENT : 0;
        return new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
        area.setMinimumWidth(ui.dp(FitnessUi.NAV_ITEM_MIN_HEIGHT_DP));
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
        boolean workoutInProgress = knownInProgressRecordId != null
                || (homeViewModel != null && homeViewModel.latestInProgressSessionId() != null);
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
        content.removeAllViews();
        if (screenChanged) {
            // The outer View owns scrolling for Compose destinations as well.
            mainScrollView.scrollTo(0, 0);
            prepareScreenEntry(currentScreen);
        }
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

        if (currentScreen == FitnessScreen.HOME) {
            renderComposeHome();
        } else {
            renderComposeDestination();
        }
    }

    private void renderComposeHome() {
        content.addView(composeViewFor(FitnessScreen.HOME, today()), ui.fullWidthParams(0));
    }

    private void renderComposeDestination() {
        String screenDate = currentScreen == FitnessScreen.MEALS ? selectedMealDate : today();
        content.addView(composeViewFor(currentScreen, screenDate), ui.fullWidthParams(0));
    }

    private ComposeView composeViewFor(FitnessScreen screen, String screenDate) {
        String configurationKey = currentOwnerId() + "|" + screenDate + "|"
                + preferredMassUnit().name() + "|" + isDarkTheme();
        ComposeEntry existing = composeEntries.get(screen);
        if (existing != null && existing.configurationKey.equals(configurationKey)) {
            return existing.view;
        }
        if (existing != null) {
            existing.view.disposeComposition();
        }

        ComposeView composeView = new ComposeView(this);
        composeView.setId(COMPOSE_VIEW_ID_BASE + screen.ordinal());
        composeView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE
        );
        if (screen == FitnessScreen.HOME) {
            ComposeHomeScreen.install(
                    composeView,
                    this,
                    currentOwnerId(),
                    screenDate,
                    preferredMassUnit(),
                    isDarkTheme()
            );
        } else {
            ComposeAppScreen.install(
                    composeView,
                    this,
                    screen,
                    currentOwnerId(),
                    screenDate,
                    preferredMassUnit(),
                    isDarkTheme()
            );
        }
        composeEntries.put(screen, new ComposeEntry(composeView, configurationKey));
        return composeView;
    }

    private void prepareScreenEntry(FitnessScreen screen) {
        AccountScope scope = new AccountScope(currentOwnerId());
        switch (screen) {
            case HOME:
            case STRENGTH:
                routineEntryViewModel.enter(scope);
                homeViewModel.enter(scope, today());
                return;
            case WORKOUT:
            case RECORDS:
                homeViewModel.enter(scope, today());
                return;
            case MEALS:
                homeViewModel.enter(scope, selectedMealDate);
                mealViewModel.enter(scope, selectedMealDate);
                return;
            case ROUTINE_DETAIL:
                homeViewModel.enter(scope, today());
                return;
            case DEVELOPMENT:
                developmentViewModel.enter(scope, today());
                return;
            case SUPPLEMENTS:
                supplementViewModel.enter(scope, today());
                return;
            case WORKOUT_SESSION:
            case WORKOUT_SUMMARY:
                workoutSessionViewModel.enter(scope, sessionState.activeRecordId());
                return;
            case WORKOUT_EXERCISE_DETAIL:
                if (sessionState.activeRecordId() != null) {
                    workoutExerciseDetailViewModel.enter(
                            scope,
                            sessionState.activeRecordId(),
                            sessionState.activeExerciseId()
                    );
                }
                return;
            case CARDIO_SESSION:
            case CARDIO_SUMMARY:
                cardioSessionViewModel.enter(scope, sessionState.activeRecordId());
                return;
            case ROUTINE_ADD:
            case WORKOUT_EXERCISE_ADD:
                exercisePickerViewModel.enter(
                        scope,
                        screen,
                        sessionState.activeRecordId(),
                        sessionState.replacementExerciseId(),
                        selectedRoutineId
                );
                return;
            default:
                return;
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
    public String currentOwnerId() {
        return supabaseConfig.effectiveUserId();
    }

    public String today() {
        return LocalDate.now().toString();
    }

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
    public WorkoutSessionViewModel workoutSessionViewModel() {
        return workoutSessionViewModel;
    }

    @Override
    public WorkoutExerciseDetailViewModel workoutExerciseDetailViewModel() {
        return workoutExerciseDetailViewModel;
    }

    @Override
    public CardioSessionViewModel cardioSessionViewModel() {
        return cardioSessionViewModel;
    }

    @Override
    public RoutineEntryViewModel routineEntryViewModel() {
        return routineEntryViewModel;
    }

    @Override
    public HomeViewModel homeViewModel() {
        return homeViewModel;
    }

    @Override
    public DevelopmentViewModel developmentViewModel() {
        return developmentViewModel;
    }

    @Override
    public SupplementViewModel supplementViewModel() {
        return supplementViewModel;
    }

    @Override
    public ExercisePickerViewModel exercisePickerViewModel() {
        return exercisePickerViewModel;
    }

    @Override
    public MealViewModel mealViewModel() {
        return mealViewModel;
    }

    @Override
    public String selectedRoutineId() {
        return selectedRoutineId;
    }

    @Override
    public void openWorkoutExerciseDetail(String exerciseId) {
        sessionState.setActiveExerciseId(exerciseId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
    }

    @Override
    public void openWorkoutExerciseReplacementPicker(String exerciseId) {
        sessionState.setReplacementExerciseId(exerciseId);
        navigate(FitnessScreen.WORKOUT_EXERCISE_ADD);
    }

    @Override
    public void refreshWorkoutExerciseDetail() {
        String recordId = sessionState.activeRecordId();
        if (recordId != null) {
            workoutExerciseDetailViewModel.enter(
                    new AccountScope(currentOwnerId()),
                    recordId,
                    sessionState.activeExerciseId()
            );
            workoutSessionViewModel.enter(new AccountScope(currentOwnerId()), recordId);
        }
    }

    @Override
    public void refreshCardioSession() {
        String recordId = sessionState.activeRecordId();
        if (recordId != null) {
            cardioSessionViewModel.refresh(new AccountScope(currentOwnerId()), recordId);
        }
    }


    @Override
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void selectRoutine(String routineId) {
        selectedRoutineId = routineId;
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
    public void openRecord(String recordId) {
        if (recordId == null) {
            return;
        }
        workoutSessionViewModel.openRecord(new AccountScope(currentOwnerId()), recordId);
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
        workoutSessionViewModel.finish(new AccountScope(currentOwnerId()), recordId);
    }

    @Override
    public void continueWorkoutIfAvailable() {
        workoutSessionViewModel.continueIfAvailable(new AccountScope(currentOwnerId()));
    }

    @Override
    public void startRoutineWorkout(
            List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance> routineExercises
    ) {
        if (routineExercises == null || routineExercises.isEmpty()) {
            toast("만들어진 루틴이 없습니다.");
            return;
        }
        String ownerId = currentOwnerId();
        String date = today();
        String title = routineNameForStart();
        String routineId = selectedRoutineId;
        workoutSessionViewModel.startRoutine(
                new AccountScope(ownerId),
                date,
                title,
                routineId,
                routineExercises
        );
    }

    private String routineNameForStart() {
        RoutineEntryUiState state = routineEntryViewModel.getUiState().getValue();
        if (state instanceof RoutineEntryUiState.Ready) {
            RoutineEntryUiState.Ready ready = (RoutineEntryUiState.Ready) state;
            String routineId = selectedRoutineId == null ? ready.getActiveRoutineId() : selectedRoutineId;
            for (RoutineSummary routine : ready.getRoutines()) {
                if (routine.id.equals(routineId)) {
                    return routine.name;
                }
            }
        }
        return "운동";
    }

    @Override
    public String currentWorkoutRecordId() {
        boolean onSessionScreen = currentScreen == FitnessScreen.WORKOUT_SESSION
                || currentScreen == FitnessScreen.WORKOUT_EXERCISE_DETAIL;
        if (onSessionScreen && sessionState.activeRecordId() != null) {
            return sessionState.activeRecordId();
        }
        if (knownInProgressRecordId != null) {
            return knownInProgressRecordId;
        }
        return homeViewModel == null ? null : homeViewModel.latestInProgressSessionId();
    }

    @Override
    public void confirmDeleteSession(String recordId) {
        ui.confirmSheet("운동 기록 삭제",
                "이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.",
                "삭제된 기록은 기록 탭에서 더 이상 보이지 않습니다.",
                "삭제", () -> {
                    String ownerId = currentOwnerId();
                    workoutSessionViewModel.delete(new AccountScope(ownerId), recordId);
                });
    }

    @Override
    public void startEmptyWorkout() {
        workoutSessionViewModel.startEmpty(new AccountScope(currentOwnerId()), today());
    }

    @Override
    public void showPastWorkoutDialog() {
        continueExistingWorkoutIfPresent(this::showPastWorkoutForm);
    }

    private void showPastWorkoutForm() {

        RoutineEntryUiState routineState = routineEntryViewModel.getUiState().getValue();
        String activeRoutineId = selectedRoutineId;
        if (activeRoutineId == null && routineState instanceof RoutineEntryUiState.Ready) {
            activeRoutineId = ((RoutineEntryUiState.Ready) routineState).getActiveRoutineId();
        }
        List<RoutineSummary> routines = homeViewModel.routines();
        RoutineSummary[] selectedRoutine = {null};
        for (RoutineSummary routine : routines) {
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
                RoutineSummary routine = routines.get(index);
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

                RoutineSummary routine = selectedRoutine[0];
                List<RoutineExerciseInstance> exercises = routine == null
                        ? java.util.Collections.emptyList()
                        : homeViewModel.routineExercises(routine.id);
                String ownerId = currentOwnerId();
                String date = selectedDate.toString();
                String title = routine == null ? "루틴 없이 운동" : routine.name;
                String routineId = routine == null ? null : routine.id;
                String started = startedAt.toString();
                String ended = endedAt.toString();
                workoutSessionViewModel.startManualPast(
                        new AccountScope(ownerId),
                        date,
                        title,
                        routineId,
                        exercises,
                        started,
                        ended
                );
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

    private static String manualWorkoutRoutineLabel(RoutineSummary routine) {
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
        final CardioActivityType requestedActivityType = activityType;
        continueExistingWorkoutIfPresent(() -> {
            pendingCardioActivityType = requestedActivityType;
            pendingCardioResumeRecordId = null;
            requestCardioPermissionsAndContinue();
        });
    }

    @Override
    public void openCardioSummary(String recordId) {
        if (recordId == null) {
            return;
        }
        cardioSessionViewModel.open(new AccountScope(currentOwnerId()), recordId);
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
        if (recordId == null) {
            toast("일시정지할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.pause(new AccountScope(currentOwnerId()), recordId);
    }

    @Override
    public void resumeCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null) {
            toast("재개할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.prepareResume(new AccountScope(currentOwnerId()), recordId);
    }

    @Override
    public void finishCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null) {
            toast("완료할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.prepareFinish(new AccountScope(currentOwnerId()), recordId);
    }

    @Override
    public void editCardioAverageHeartRate() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null) {
            toast("수정할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.prepareAverageHeartRateEdit(
                new AccountScope(currentOwnerId()), recordId
        );
    }

    @Override
    public void cancelCardioWorkout() {
        String recordId = sessionState.activeRecordId();
        if (recordId == null) {
            toast("취소할 유산소 기록을 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.prepareCancel(new AccountScope(currentOwnerId()), recordId);
    }

    private void openCardioSession(String recordId) {
        if (recordId == null) {
            toast("GPS 유산소 상태를 찾지 못했습니다.");
            return;
        }
        cardioSessionViewModel.open(new AccountScope(currentOwnerId()), recordId);
    }

    private void openCardioSessionLoaded(CardioSessionSnapshot snapshot) {
        if (snapshot == null) {
            toast("GPS 유산소 상태를 찾지 못했습니다.");
            return;
        }
        String recordId = snapshot.getRecordId();
        sessionState.setActiveRecordId(recordId);
        sessionState.setActiveExerciseId(null);
        if (CardioSessionSnapshot.STATUS_COMPLETED.equals(snapshot.getStatus())) {
            navigate(FitnessScreen.CARDIO_SUMMARY);
            return;
        }
        knownInProgressRecordId = recordId;
        if (CardioSessionSnapshot.STATUS_TRACKING.equals(snapshot.getStatus())) {
            dispatchCardioService(CardioTrackingService.ACTION_START, recordId, true);
        }
        navigate(FitnessScreen.CARDIO_SESSION);
    }

    private void showCardioHeartRateSheet(
            String recordId,
            boolean finishAfterSave,
            CardioSessionSnapshot snapshot
    ) {
        if (snapshot == null) {
            toast("유산소 기록을 찾지 못했습니다.");
            return;
        }

        LinearLayout form = ui.form();
        int elapsedSeconds = snapshot.elapsedSeconds(System.currentTimeMillis());
        TextView summary = ui.text(
                CardioMetrics.formatDistanceKilometers(snapshot.getDistanceMeters())
                        + "km · " + CardioMetrics.formatElapsed(elapsedSeconds),
                14,
                FitnessUi.COLOR_MUTED,
                false
        );
        EditText averageHeartRate = ui.numberInput(
                "평균 심박수 bpm (선택)",
                CardioMetrics.hasAverageHeartRate(snapshot.getAverageHeartRateBpm())
                        ? CardioMetrics.formatAverageHeartRate(snapshot.getAverageHeartRateBpm())
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
                finishAfterSave ? cardioActivityType(snapshot).labelKo() + " 완료" : "평균 심박수 수정",
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

                    AccountScope scope = new AccountScope(currentOwnerId());
                    if (finishAfterSave) {
                        cardioSessionViewModel.finish(scope, recordId, averageHeartRateBpm);
                    } else {
                        cardioSessionViewModel.updateAverageHeartRate(
                                scope, recordId, averageHeartRateBpm
                        );
                    }
                    return true;
                }
        );
    }

    private void continueExistingWorkoutIfPresent(Runnable ifNone) {
        String ownerId = currentOwnerId();
        executor.execute(() -> {
            try {
                AccountScope scope = new AccountScope(ownerId);
                String activeRecordId = workoutSessionApplicationService.latestInProgress(scope);
                if (activeRecordId == null) {
                    runOnUiThread(ifNone);
                    return;
                }
                boolean cardioSession = workoutSessionApplicationService.isCardioSession(
                        scope,
                        activeRecordId
                );
                runOnUiThread(() -> {
                    knownInProgressRecordId = activeRecordId;
                    toast("진행 중인 운동을 먼저 이어갑니다.");
                    if (cardioSession) {
                        openCardioSession(activeRecordId);
                    } else {
                        openWorkoutSession(activeRecordId);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("진행 중인 운동을 확인하지 못했습니다."));
            }
        });
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
            cardioSessionViewModel.start(scope, activityType, today());
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

    private static CardioActivityType cardioActivityType(CardioSessionSnapshot snapshot) {
        CardioActivityType activityType = snapshot == null
                ? null
                : CardioActivityType.fromId(snapshot.getActivityId());
        return activityType == null ? CardioActivityType.WALKING : activityType;
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
        bodyMetricsViewModel.open(new AccountScope(currentOwnerId()), date, recordId);
    }

    private void showBodyMetricDialogForm(
            String ownerId,
            BodyMetricsApplicationService.Editor existing
    ) {
        MassUnit inputUnit = preferredMassUnit();
        LinearLayout form = ui.form();
        EditText dateInput = ui.input("날짜 (YYYY-MM-DD)", existing.date);
        EditText weight = ui.decimalInput(
                "체중 " + inputUnit.symbol(),
                existing.exists() ? MassFormatter.formatInput(existing.weightKg, inputUnit) : ""
        );
        EditText memo = ui.input("메모 (선택)", "");
        if (existing.exists()) {
            memo.setText(existing.memo);
        }
        ui.addAll(form, dateInput, weight, memo);
        ui.validatedSheet(!existing.exists() ? "체중 기록" : "체중 수정", form,
                "저장", () -> {
                    try {
                        String selectedDate = FitnessUi.inputText(dateInput);
                        Double selectedWeight = FitnessUi.optionalDouble(weight);
                        if (selectedWeight == null) {
                            throw new IllegalArgumentException("체중을 입력하세요.");
                        }
                        double selectedWeightKg = MassUnit.toKg(selectedWeight, inputUnit);
                        String selectedMemo = FitnessUi.inputText(memo);
                        bodyMetricsViewModel.save(
                                new AccountScope(ownerId),
                                existing.recordId,
                                selectedDate,
                                selectedWeightKg,
                                selectedMemo
                        );
                        return true;
                    } catch (IllegalArgumentException error) {
                        toast(error.getMessage());
                        return false;
                    }
                },
                !existing.exists() ? null : "이 기록 삭제",
                !existing.exists() ? null : () -> {
                    bodyMetricsViewModel.delete(
                            new AccountScope(ownerId),
                            existing.recordId
                    );
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
        selectedMealDate = date == null ? today() : date;
        mealReturnScreen = returnScreen == null ? FitnessScreen.WORKOUT : returnScreen;
        navigate(FitnessScreen.MEALS);
    }

    @Override
    public boolean isDeveloperSurfaceAllowed() {
        return AppSurfacePolicy.allowsDeveloperSurface();
    }

    @Override
    public void showDevelopmentBodyProfileDialog() {
        developmentViewModel.openProfileEditor(
                new AccountScope(currentOwnerId()),
                today()
        );
    }

    private void showDevelopmentBodyProfileDialogForm(
            String ownerId,
            String date,
            DevelopmentApplicationService.ProfileEditor editor
    ) {
        BodyProfile currentProfile = editor.profile;
        LinearLayout form = ui.form();
        EditText heightInput = ui.numberInput(
                "키 cm",
                currentProfile.heightCm == null ? "" : String.valueOf(currentProfile.heightCm)
        );
        MassUnit inputUnit = preferredMassUnit();
        EditText weightInput = ui.decimalInput(
                "오늘 체중 " + inputUnit.symbol(),
                editor.weightKg == null
                        ? ""
                        : MassFormatter.formatInput(editor.weightKg, inputUnit)
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
                BodyProfile savedProfile = nextProfile;
                Double savedWeightKg = nextWeightKg;
                String weightMemo = editor.weightMemo;
                developmentViewModel.saveProfileAndWeight(
                        new AccountScope(ownerId),
                        savedProfile,
                        editor.weightRecordId,
                        date,
                        savedWeightKg,
                        weightMemo
                );
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
        developmentViewModel.openGoalEditor(new AccountScope(currentOwnerId()));
    }

    private void showDevelopmentGoalDialogForm(
            String ownerId,
            DevelopmentGoal currentGoal
    ) {
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
                DevelopmentGoal nextGoal = new DevelopmentGoal(
                        selectedObjective[0],
                        weeklySessions,
                        selectedFocus[0],
                        effectiveFrom,
                        "",
                        ""
                );
                developmentViewModel.saveGoal(new AccountScope(ownerId), nextGoal);
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

    @Override
    public boolean isDataTransferInProgress() {
        return isDataTransferInProgress;
    }

    @Override
    public String dataTransferDetail() {
        return dataTransferDetail;
    }























    @Override
    public void openFleekDataImport() {
        dataTransferCoordinator.openFleekDataImport();
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
            appContainer.applyPriceTraceSessionConfig(priceTraceSupabaseConfig);
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
                List<ProductReadV1> products = nutritionIntegrationService.searchProducts(query);
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
                ProductReadV1 product = nutritionIntegrationService.loadProduct(catalogProductId);
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
                List<NutritionIntegrationService.RestaurantSummary> restaurants =
                        nutritionIntegrationService.searchRestaurants(query);
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
                NutritionIntegrationService.RestaurantDetail restaurant =
                        nutritionIntegrationService.loadRestaurant(restaurantId);
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
                NutritionIntegrationService.PublicProductNutrition nutrition =
                        nutritionIntegrationService.loadPublicProductNutrition(catalogProductId);
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
    public void syncNutritionCatalog(NutritionSyncCallback callback) {
        executor.execute(() -> {
            try {
                NutritionIntegrationService.SyncResult result =
                        nutritionIntegrationService.syncCatalog(nutritionSupabaseConfig);
                if (result.nutritionConfig != null) {
                    applyNutritionSessionConfig(result.nutritionConfig);
                }
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
            NutritionPublicationCallback callback
    ) {
        executor.execute(() -> {
            try {
                NutritionIntegrationService.PublicationResult result =
                        nutritionIntegrationService.publishNutrition(
                                nutritionSupabaseConfig,
                                nutritionFoodId,
                                catalogProductId,
                                publish
                        );
                applyNutritionSessionConfig(result.nutritionConfig);
                if (callback != null) {
                    callback.onComplete(result.state);
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
            NutritionPublicationCallback callback
    ) {
        executor.execute(() -> {
            try {
                NutritionIntegrationService.PublicationResult result =
                        nutritionIntegrationService.publishDiningOut(
                                nutritionSupabaseConfig,
                                priceTraceSupabaseConfig,
                                nutritionFoodId,
                                publish
                        );
                applyNutritionSessionConfig(result.nutritionConfig);
                if (publish && result.priceTraceConfig != null) {
                    applyPriceTraceSessionConfig(result.priceTraceConfig);
                }
                if (callback != null) {
                    callback.onComplete(result.state);
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
                SyncApplicationService.Result syncResult = syncApplicationService.run(
                        supabaseConfig,
                        nutritionSupabaseConfig
                );
                applySharedSessionConfig(syncResult.sharedConfig);
                if (syncResult.nutritionConfig != null) {
                    applyNutritionSessionConfig(syncResult.nutritionConfig);
                }
                SyncApplicationService.SharedResult result = syncResult.sharedResult;
                lastSyncedAt = result.syncedAt;
                final boolean summaryFailed = !result.summaryPublicationSucceeded;
                final boolean completedWithPartialFailure = syncResult.nutritionFailed || summaryFailed;
                final int legacyPushedRows = result.pushedRows - result.summaryPublishedRows;
                final String completedNutritionStatus = syncResult.nutritionStatus;
                final String completedSummaryStatus = summaryFailed
                        ? "Summary v2 실패: " + result.summaryPublicationError
                        : "Summary v2 " + result.summaryPublishedRows + "건";
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = completedWithPartialFailure ? "partial" : "synced";
                    syncDetail = "공통 DB push " + legacyPushedRows + "건 · pull "
                            + result.pulledRows + "건 · " + completedSummaryStatus
                            + " · " + completedNutritionStatus;
                    toast(completedWithPartialFailure
                            ? "공통 DB 동기화는 완료했지만 일부 원격 publication이 실패했습니다."
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
        appContainer.applyAuthenticatedSharedConfig(config);
        supabaseConfig = appContainer.getSupabaseConfig();
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
        appContainer.applySharedSessionConfig(config);
        supabaseConfig = appContainer.getSupabaseConfig();
    }

    private void applyAuthenticatedNutritionConfig(SupabaseConfig config) {
        appContainer.applyAuthenticatedNutritionConfig(config);
        nutritionSupabaseConfig = appContainer.getNutritionSupabaseConfig();
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
        appContainer.applyNutritionSessionConfig(config);
        nutritionSupabaseConfig = appContainer.getNutritionSupabaseConfig();
    }

    private void applyPriceTraceSessionConfig(SupabaseConfig config) {
        appContainer.applyPriceTraceSessionConfig(config);
        priceTraceSupabaseConfig = appContainer.getPriceTraceSupabaseConfig();
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

    /**
     * Coordinates file import/export work while MainActivity retains only platform callbacks and
     * ScreenHost action forwarding.
     */
    private final class DataTransferCoordinator {
        private void createLocalBackup() {
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

        private void restoreLocalBackup() {
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

        private void exportRecordsCsv() {
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

        private void openWorkoutTransferImport() {
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

        private void exportWorkoutTransfer() {
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

    private void writeLocalBackup(Uri uri) {
            beginDataTransfer("백업 파일을 만드는 중입니다.");
            executor.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) {
                        throw new IOException("선택한 위치에 파일을 만들 수 없습니다.");
                    }
                    localDataTransferApplicationService.writeBackup(
                            currentOwnerId(),
                            nutritionSupabaseConfig.effectiveUserId(),
                            output
                    );
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
                    LocalDataTransferApplicationService.BackupPreview preview =
                            localDataTransferApplicationService.previewBackup(
                                    currentOwnerId(),
                                    nutritionSupabaseConfig.effectiveUserId(),
                                    input
                            );
                    runOnUiThread(() -> {
                        isDataTransferInProgress = false;
                        dataTransferDetail = preview.totalRows + "개 항목 확인됨";
                        render();
                        ui.confirmSheet(
                                "백업 복원",
                                preview.totalRows + "개 항목을 현재 기록에 합칩니다. "
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
                    LocalDataTransferApplicationService.RestoreResult result =
                            localDataTransferApplicationService.restoreBackup(
                                    currentOwnerId(),
                                    nutritionSupabaseConfig.effectiveUserId(),
                                    input
                            );
                    finishDataTransfer(
                            result.importedRows + "개 복원 · "
                                    + result.skippedRows + "개 중복 건너뜀",
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
                    localDataTransferApplicationService.writeRecordsSummaryCsv(
                            currentOwnerId(),
                            nutritionSupabaseConfig.effectiveUserId(),
                            output
                    );
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
                    localDataTransferApplicationService.writeWorkoutTransfer(
                            currentOwnerId(),
                            output
                    );
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
                    LocalDataTransferApplicationService.ImportResult result =
                            localDataTransferApplicationService.importWorkoutTransfer(
                                    currentOwnerId(),
                                    input
                            );
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

        private void openFleekDataImport() {
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
                    LocalDataTransferApplicationService.ImportResult result =
                            localDataTransferApplicationService.importFleek(
                                    currentOwnerId(),
                                    input
                            );
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
    }

}
