package com.yeonsik.fitnessapp.app.navigation

import com.yeonsik.fitnessapp.core.ui.FitnessAlertDialog

import android.os.Build
import android.os.SystemClock

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.config.LauncherIconController
import com.yeonsik.fitnessapp.ui.AppUiActions
import com.yeonsik.fitnessapp.feature.body.ui.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionEditorState
import com.yeonsik.fitnessapp.feature.records.ui.*
import com.yeonsik.fitnessapp.feature.statistics.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.settings.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.supplement.SupplementPlan
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseDraft
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The single Compose root and navigation destination host for every application screen. */
object ComposeAppScreen {
    @JvmStatic
    fun install(
        view: androidx.compose.ui.platform.ComposeView,
        host: AppUiActions,
        navigation: AppNavigationViewModel,
        viewModels: AppViewModels
    ) {
        view.setContent {
            AppRoot(host, navigation, viewModels)
        }
    }
}

internal fun destinationScrollStateKey(screen: FitnessScreen): String =
    "fitness-destination:${screen.name}"

internal fun isExercisePickerDestination(screen: FitnessScreen): Boolean =
    screen == FitnessScreen.ROUTINE_ADD || screen == FitnessScreen.WORKOUT_EXERCISE_ADD

internal data class HomeEntryEffectKey(
    val screen: FitnessScreen,
    val ownerId: String,
    val date: String
)

internal fun homeEntryEffectKey(
    screen: FitnessScreen,
    ownerId: String,
    date: String
): HomeEntryEffectKey = HomeEntryEffectKey(screen, ownerId, date)

internal fun mealMutationHomeRefreshDate(
    destinationScreen: FitnessScreen,
    today: String,
    selectedMealDate: String
): String? = when (destinationScreen) {
    FitnessScreen.HOME -> today
    FitnessScreen.MEALS -> selectedMealDate
    else -> null
}

internal data class RecordEntryEffectKey(
    val screen: FitnessScreen,
    val ownerId: String,
    val recordId: String?
)

internal const val STARTUP_MIN_VISIBLE_MS = 550L
internal const val STARTUP_MAX_WAIT_MS = 10_000L

internal fun startupDataSettled(
    home: HomeUiState,
    records: RecordsUiState,
    ownerId: String,
    today: String
): Boolean {
    val month = runCatching { YearMonth.from(LocalDate.parse(today)).toString() }
        .getOrNull() ?: return false
    val homeReady = when (home) {
        is HomeUiState.Ready -> home.requestIdentity.ownerId == ownerId &&
            home.requestIdentity.date == today
        else -> false
    }
    val recordsReady = when (records) {
        is RecordsUiState.Ready -> records.snapshot.ownerId == ownerId &&
            records.snapshot.today == today && records.snapshot.displayedMonth == month
        else -> false
    }
    val homeError = home is HomeUiState.Error && home.ownerId == ownerId &&
        (home.date == null || home.date == today)
    val recordsError = records is RecordsUiState.Error && records.ownerId == ownerId &&
        records.today == today && records.displayedMonth == month
    return homeError || recordsError || (homeReady && recordsReady)
}

internal fun startupCanComplete(
    home: HomeUiState,
    records: RecordsUiState,
    ownerId: String,
    today: String,
    elapsedMs: Long
): Boolean = (elapsedMs >= STARTUP_MIN_VISIBLE_MS &&
    startupDataSettled(home, records, ownerId, today)) || elapsedMs >= STARTUP_MAX_WAIT_MS

@Composable
private fun AppRoot(
    host: AppUiActions,
    navigation: AppNavigationViewModel,
    viewModels: AppViewModels
) {
    val navigationState by navigation.uiState.observeAsState(AppNavigationState())
    val settingsState by viewModels.getSettings().uiState.observeAsState()
    val settingsEvent by viewModels.getSettings().events.observeAsState()
    val homeState by viewModels.getHome().uiState.observeAsState(HomeUiState.Idle)
    val homeActivityState by viewModels.getHome().activityState.observeAsState(
        com.yeonsik.fitnessapp.feature.home.ui.HomeActivityUiState.Idle
    )
    val homeActivityDayDetails by viewModels.getHome().activityDayDetails.observeAsState(
        com.yeonsik.fitnessapp.feature.home.ui.HomeActivityDayDetailsUiState.Idle
    )
    val recordsState by viewModels.getRecords().uiState.observeAsState(RecordsUiState.Idle)
    val startupCompleted by navigation.startupCompleted.observeAsState(false)
    val routineState by viewModels.getRoutineEntry().uiState.observeAsState(RoutineEntryUiState.Idle)

    val cardioState by viewModels.getCardioSession().uiState
        .observeAsState(CardioSessionUiState.Idle)
    val workoutState by viewModels.getWorkoutSession().uiState
        .observeAsState(WorkoutSessionUiState.Idle)
    val exercisePickerState by viewModels.getExercisePicker().uiState
        .observeAsState(ExercisePickerUiState.Idle)
    val screen = navigationState.screen
    val recordsHubTab = navigationState.recordsHubTab
    val destinationScreen = if (screen == FitnessScreen.RECORDS) {
        recordsHubTab.screen
    } else {
        screen
    }
    val ownerId = host.currentOwnerId()
    var createdRoutineRecordId by remember(ownerId) { mutableStateOf<String?>(null) }
    val today = navigationState.today
    val startupStartedAt = remember(ownerId, today) { SystemClock.elapsedRealtime() }
    val unit = settingsState?.preferredMassUnit
        ?: viewModels.getSettings().preferredMassUnit()
    val themeMode = settingsState?.themeMode
        ?: viewModels.getSettings().themeMode()
    val dark = themeMode == "dark" ||
        (themeMode == "system" &&
            androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)
    val launcherContext = LocalContext.current.applicationContext
    LaunchedEffect(launcherContext, dark) {
        LauncherIconController.synchronize(launcherContext, dark)
    }
    val routeDate = when (destinationScreen) {
        FitnessScreen.MEALS -> navigationState.selectedMealDate
        FitnessScreen.RECORDS -> navigationState.selectedRecordsDate
        else -> navigationState.today
    }
    val restState by viewModels.getWorkoutSession().restTimerState
        .observeAsState(WorkoutRestTimerState.Inactive)
    val mealState by viewModels.getMeal().uiState.observeAsState(MealUiState.Idle)
    val workoutReadOnly = (workoutState as? WorkoutSessionUiState.Ready)?.let {
        it.session.status == "completed"
    } == true
    val destinationStateHolder = rememberSaveableStateHolder()
    // Above the swipe host: preview/current composition changes must not reset consumption.
    val homeEntranceState = rememberTopLevelEntranceState("HOME")
    val workoutEntranceState = rememberTopLevelEntranceState("WORKOUT")
    val recordsEntranceState = rememberTopLevelEntranceState("RECORDS")
    val topLevelSwipeEnabled = navigation.canSwipeTopLevel()
    var swipeSelection by remember(screen) { mutableStateOf<TopLevelSwipeSelection?>(null) }
    val workoutAction by viewModels.getWorkoutSession().actionState
        .observeAsState()
    val workoutTerminalEvent by viewModels.getWorkoutSession().terminalEvents
        .observeAsState()
    val cardioAction by viewModels.getCardioSession().actionState
        .observeAsState()
    val cardioHeartRateEditorState by viewModels.getCardioSession().heartRateEditorState
        .observeAsState(CardioHeartRateEditorUiState.Idle)
    val cardioCancelConfirmationState by viewModels.getCardioSession().cancelConfirmationState
        .observeAsState(CardioCancelConfirmationUiState.Idle)
    val manualPastState by viewModels.getWorkoutSession().manualPastState
        .observeAsState(ManualPastWorkoutUiState.Idle)
    val workoutDeleteConfirmationState by viewModels.getWorkoutSession().deleteConfirmationState
        .observeAsState(WorkoutDeleteConfirmationUiState.Idle)
    val bodyEditorState by viewModels.getBodyMetrics().editorState
        .observeAsState(BodyMetricsEditorUiState.Idle)
    val profileEditorState by viewModels.getDevelopment().profileEditorState
        .observeAsState(DevelopmentProfileEditorUiState.Idle)
    val goalEditorState by viewModels.getDevelopment().goalEditorState
        .observeAsState(DevelopmentGoalEditorUiState.Idle)
    val recoveryEditorState by viewModels.getDevelopment().recoveryEditorState
        .observeAsState(RecoveryEditorUiState.Idle)
    var backupPreview by remember {
        mutableStateOf<LocalDataTransferApplicationService.BackupPreview?>(null)
    }

    LaunchedEffect(settingsEvent) {
        val event = settingsEvent ?: return@LaunchedEffect
        if (!event.consume()) return@LaunchedEffect
        when (event) {
            is SettingsEvent.BackupPreviewReady -> backupPreview = event.preview
            is SettingsEvent.Notice -> {
                host.toast(event.message)
                viewModels.getSettings().refresh()
            }
            is SettingsEvent.Failure -> {
                host.toast(event.message)
                viewModels.getSettings().refresh()
            }
            is SettingsEvent.ConfigSaved -> {
                host.toast(event.message)
                viewModels.getSettings().refresh()
            }
            is SettingsEvent.Authenticated -> {
                host.toast(event.message)
                viewModels.getSettings().refresh()
            }
            is SettingsEvent.SignedOut -> {
                host.toast(event.message)
                viewModels.getSettings().refresh()
            }
            is SettingsEvent.SyncCompleted -> {
                host.toast("수동 동기화 결과를 반영했습니다.")
                viewModels.getSettings().refresh()
            }
        }
    }
    val homeActions = object : HomeScreenActions {
        override fun continueWorkout() = viewModels.getWorkoutSession()
            .continueIfAvailable(AccountScope(ownerId))
        override fun navigate(screen: FitnessScreen) = navigation.navigate(screen)

        override fun showBodyMetric() = viewModels.getBodyMetrics().open(
            AccountScope(ownerId), navigationState.today, null
        )
        override fun openMealManagement(date: String, returnScreen: FitnessScreen) {
            navigation.selectMealDate(date)
            navigation.navigate(FitnessScreen.MEALS)
        }
    }

    val homeEntryKey = homeEntryEffectKey(destinationScreen, ownerId, routeDate)
    LaunchedEffect(ownerId, today, startupCompleted) {
        if (!startupCompleted) {
            val scope = AccountScope(ownerId)
            viewModels.getRoutineEntry().enterIfNeeded(scope)
            viewModels.getHome().enterIfNeeded(scope, today)
            viewModels.getRecords().rememberSelectedDate(scope, today)
            viewModels.getRecords().enterIfNeeded(scope, today, today)
        }
    }
    LaunchedEffect(
        ownerId, today, homeState, recordsState,
        navigationState.selectedRecordsDate, startupCompleted
    ) {
        if (startupCompleted) return@LaunchedEffect
        val deadline = if (startupDataSettled(homeState, recordsState, ownerId, today)) {
            STARTUP_MIN_VISIBLE_MS
        } else {
            STARTUP_MAX_WAIT_MS
        }
        val remaining = deadline -
            (SystemClock.elapsedRealtime() - startupStartedAt)
        if (remaining > 0) delay(remaining)
        if (!startupCanComplete(
                homeState, recordsState, ownerId, today,
                SystemClock.elapsedRealtime() - startupStartedAt
            )) return@LaunchedEffect
        viewModels.getRecords().rememberSelectedDate(
            AccountScope(ownerId), navigationState.selectedRecordsDate
        )
        navigation.completeStartup()
    }
    LaunchedEffect(homeEntryKey, startupCompleted) {
        if (!startupCompleted) return@LaunchedEffect
        when (destinationScreen) {
            FitnessScreen.HOME,
            FitnessScreen.STRENGTH -> {
                viewModels.getRoutineEntry().enterIfNeeded(AccountScope(ownerId))
                viewModels.getHome().enterIfNeeded(AccountScope(ownerId), navigationState.today)
            }
            FitnessScreen.WORKOUT,
            FitnessScreen.ROUTINE_DETAIL ->
                viewModels.getHome().enterIfNeeded(AccountScope(ownerId), routeDate)
            FitnessScreen.MEALS ->
                viewModels.getHome().enter(AccountScope(ownerId), routeDate)
            FitnessScreen.RECORDS ->
                viewModels.getRecords().enterIfNeeded(
                    AccountScope(ownerId),
                    navigationState.today,
                    navigationState.selectedRecordsDate
                )
            else -> Unit
        }
    }

    val mealRecordNotice = (mealState as? MealUiState.Ready)?.notice
    LaunchedEffect(workoutAction, workoutTerminalEvent, bodyEditorState, mealRecordNotice) {
        val recordsChanged = workoutAction != null || workoutTerminalEvent != null ||
            bodyEditorState is BodyMetricsEditorUiState.Saved ||
            bodyEditorState is BodyMetricsEditorUiState.Deleted ||
            mealRecordNotice != null
        if (recordsChanged) {
            viewModels.getRecords().markStale()
            viewModels.getStatistics().markStale()
            viewModels.getDevelopment().markStale()
            if (mealRecordNotice != null) {
                viewModels.getHome().markStale()
                mealMutationHomeRefreshDate(destinationScreen, navigationState.today, routeDate)?.let { date ->
                    viewModels.getHome().enterIfNeeded(AccountScope(ownerId), date)
                }
            }
            if (destinationScreen == FitnessScreen.RECORDS) {
                viewModels.getRecords().enterIfNeeded(
                    AccountScope(ownerId),
                    navigationState.today,
                    navigationState.selectedRecordsDate
                )
            }
        }
    }

    LaunchedEffect(routineState, ownerId) {
        val state = routineState as? RoutineEntryUiState.Ready ?: return@LaunchedEffect
        if (state.ownerId == ownerId && state.notice != null) {
            viewModels.getHome().markStale()
            viewModels.getDevelopment().markStale()
            if (screen == FitnessScreen.HOME || screen == FitnessScreen.STRENGTH) {
                viewModels.getHome().enter(AccountScope(ownerId), routeDate)
            }
        }
    }

    LaunchedEffect(routineState, screen, ownerId) {
        val state = routineState as? RoutineEntryUiState.Error ?: return@LaunchedEffect
        if (state.ownerId == ownerId &&
            (screen == FitnessScreen.HOME || screen == FitnessScreen.STRENGTH)
        ) {
            host.toast(state.message)
        }
    }

    LaunchedEffect(homeState, screen, ownerId) {
        val state = homeState
        if (state is HomeUiState.Error
            && state.ownerId == ownerId
            && screen == FitnessScreen.HOME
        ) {
            host.toast(state.message)
        }
    }

    LaunchedEffect(cardioState, screen, ownerId) {
        val state = cardioState
        if (state is CardioSessionUiState.Missing
            && state.ownerId == ownerId
            && screen == FitnessScreen.CARDIO_SESSION
            && state.recordId == currentWorkoutRecordId(screen, viewModels, homeState)
        ) {
            navigation.replace(FitnessScreen.CARDIO)
        }
    }

    LaunchedEffect(exercisePickerState, screen, ownerId, navigationState.today) {
        val state = exercisePickerState
        if (state !is ExercisePickerUiState.Saved
            || state.ownerId != ownerId
            || screen != state.mode
        ) {
            return@LaunchedEffect
        }
        if (state.mode == FitnessScreen.ROUTINE_ADD) {
            viewModels.getHome().enter(AccountScope(ownerId), routeDate)
        } else {
            val recordId = viewModels.getExercisePicker().activeRecordId()
                ?: currentWorkoutRecordId(screen, viewModels, homeState)
            if (recordId != null) {
                viewModels.getWorkoutSession().enter(AccountScope(ownerId), recordId)
                viewModels.getWorkoutExerciseDetail().enter(
                    AccountScope(ownerId),
                    recordId,
                    viewModels.getWorkoutExerciseDetail().activeExerciseId(),
                    readOnly = workoutReadOnly
                )
            }
            if (state.selectionMode in listOf(
                com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerSelectionMode.WORKOUT_REPLACE,
                com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerSelectionMode.WORKOUT_LINK_MANUAL
            )) {
                viewModels.getExercisePicker().clearReplacementExercise()
                viewModels.getStatistics().markStale()
                viewModels.getDevelopment().markStale()
            }
        }
        navigation.back()
    }

    LaunchedEffect(workoutAction) {
        val event = workoutAction ?: return@LaunchedEffect
        if (event.ownerId != host.currentOwnerId() || !event.consume()) {
            return@LaunchedEffect
        }
        if (event.outcome != WorkoutSessionActionOutcome.NONE) {
            viewModels.getHome().markStale()
        }
        when (event.outcome) {
            WorkoutSessionActionOutcome.FAILURE,
            WorkoutSessionActionOutcome.NONE ->
                host.toast(event.message ?: "운동 작업을 완료하지 못했습니다.")
            WorkoutSessionActionOutcome.OPEN_EXISTING -> {
                createdRoutineRecordId = null
                val recordId = event.recordId
                if (recordId == null) {
                    host.toast("운동 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                viewModels.getWorkoutSession().rememberActiveRecord(recordId)
                event.message?.let(host::toast)
                if (event.completedEvidence != null) {
                    viewModels.getWorkoutSession().prepareCompletedEvidenceSession(AccountScope(ownerId), event)
                    viewModels.getWorkoutExerciseDetail().rememberActiveExercise(event.completedEvidence.exerciseId)
                    navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                } else if (event.action == WorkoutSessionAction.OPEN_RECORD && event.cardioSession) {
                    viewModels.getCardioSession().open(AccountScope(ownerId), recordId)
                } else if (event.cardioSession) {
                    viewModels.getCardioSession().open(AccountScope(ownerId), recordId)
                } else {
                    openWorkoutSession(navigation, viewModels, recordId)
                }
            }
            WorkoutSessionActionOutcome.CREATED -> {
                val recordId = event.recordId
                if (recordId == null) {
                    host.toast("운동 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                viewModels.getWorkoutSession().prepareCreatedRoutineSession(AccountScope(ownerId), event)
                viewModels.getWorkoutSession().rememberActiveRecord(recordId)
                createdRoutineRecordId = recordId.takeIf { event.action == WorkoutSessionAction.START_ROUTINE }
                when (event.action) {
                    WorkoutSessionAction.START_ROUTINE -> Unit
                    WorkoutSessionAction.START_MANUAL_PAST ->
                        host.toast("세트와 횟수를 입력한 뒤 운동 완료를 누르세요.")
                    else -> Unit
                }
                viewModels.getWorkoutSession().dismissManualPastEditor()
                openWorkoutSession(navigation, viewModels, recordId)
            }
            WorkoutSessionActionOutcome.DELETED -> {
                event.recordId?.let { clearActiveWorkout(viewModels, it) }
                viewModels.getDevelopment().markStale()
                host.toast("운동 기록을 삭제했습니다.")
                if (screen != FitnessScreen.RECORDS) {
                    navigation.replace(
                        if (event.cardioSession) FitnessScreen.CARDIO else FitnessScreen.STRENGTH
                    )
                }
            }
        }
    }

    LaunchedEffect(workoutTerminalEvent) {
        val event = workoutTerminalEvent ?: return@LaunchedEffect
        if (event.ownerId != host.currentOwnerId() || !event.consume()) {
            return@LaunchedEffect
        }
        viewModels.getHome().markStale()
        when (event.outcome) {
            WorkoutSessionTerminalOutcome.MISSING -> {
                event.recordId?.let { clearActiveWorkout(viewModels, it) }
                host.toast(event.message ?: "운동 기록을 찾지 못했습니다.")
                navigation.replace(FitnessScreen.STRENGTH)
            }
            WorkoutSessionTerminalOutcome.COMPLETED -> {
                host.toast("운동을 완료했습니다.")
                navigation.replace(FitnessScreen.WORKOUT_SUMMARY)
            }
            WorkoutSessionTerminalOutcome.CANCELLED -> {
                viewModels.getWorkoutSession().stopRestTimer()
                event.recordId?.let { clearActiveWorkout(viewModels, it) }
                host.toast(event.message ?: "운동을 취소했습니다.")
                navigation.replace(FitnessScreen.STRENGTH)
            }
            WorkoutSessionTerminalOutcome.DISCARDED_EMPTY -> {
                event.recordId?.let { clearActiveWorkout(viewModels, it) }
                host.toast(event.message ?: "수행한 세트가 없어 운동을 저장하지 않았습니다.")
                navigation.replace(FitnessScreen.STRENGTH)
            }
            WorkoutSessionTerminalOutcome.FAILURE ->
                host.toast(event.message ?: "운동을 완료하지 못했습니다.")
        }
    }

    LaunchedEffect(cardioAction) {
        val event = cardioAction ?: return@LaunchedEffect
        if (event.ownerId != host.currentOwnerId() || !event.consume()) {
            return@LaunchedEffect
        }
        val cardioViewModel = viewModels.getCardioSession()
        if (event.action == CardioSessionAction.PREPARE_START &&
            event.outcome != CardioSessionActionOutcome.START_READY) {
            cardioViewModel.clearPendingPermissionAction()
        }
        if (event.outcome != CardioSessionActionOutcome.FAILURE &&
            event.outcome != CardioSessionActionOutcome.NOT_FOUND
        ) {
            viewModels.getHome().markStale()
            viewModels.getRecords().markStale()
            viewModels.getStatistics().markStale()
            viewModels.getDevelopment().markStale()
        }
        val recordId = event.recordId
        val session = event.session
        if (event.outcome == CardioSessionActionOutcome.FAILURE
            || event.outcome == CardioSessionActionOutcome.NOT_FOUND
        ) {
            host.toast(event.message ?: "유산소 작업을 완료하지 못했습니다.")
            return@LaunchedEffect
        }
        when (event.action) {
            CardioSessionAction.PREPARE_START -> {
                when (event.outcome) {
                    CardioSessionActionOutcome.START_READY -> {
                        val activityType = cardioViewModel.pendingStartActivityType()
                        if (activityType == null) {
                            host.toast("유산소 기록을 시작할 준비를 하지 못했습니다.")
                        } else {
                            if (activityType.usesGps(cardioViewModel.pendingStartEnvironment()
                                    ?: activityType.defaultEnvironment)) {
                                host.requestCardioStart(activityType)
                            } else cardioViewModel.startAfterPermissions(AccountScope(ownerId))
                        }
                    }
                    CardioSessionActionOutcome.EXISTING_WORKOUT -> {
                        val existingId = event.recordId
                        if (existingId == null) {
                            host.toast("진행 중인 운동을 찾지 못했습니다.")
                        } else if (event.existingCardioSession && event.session != null) {
                            cardioViewModel.rememberActiveRecord(existingId)
                            event.message?.let(host::toast)
                            if (event.session.usesGps && event.session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_TRACKING) {
                                host.startCardioTracking(existingId)
                            }
                            navigation.navigate(
                                if (event.session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_COMPLETED) {
                                    FitnessScreen.CARDIO_SUMMARY
                                } else {
                                    FitnessScreen.CARDIO_SESSION
                                }
                            )
                        } else {
                            event.message?.let(host::toast)
                            viewModels.getWorkoutSession().openRecord(AccountScope(ownerId), existingId)
                        }
                    }
                    else -> Unit
                }
            }
            CardioSessionAction.OPEN -> {
                if (session == null || recordId == null) {
                    host.toast("GPS 유산소 상태를 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                cardioViewModel.rememberActiveRecord(recordId)
                if (session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_COMPLETED) {
                    navigation.navigate(FitnessScreen.CARDIO_SUMMARY)
                } else {
                    if (session.usesGps && session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_TRACKING) {
                        host.startCardioTracking(recordId)
                    }
                    navigation.navigate(FitnessScreen.CARDIO_SESSION)
                    if (cardioViewModel.consumePendingFinishRequest()) {
                        cardioViewModel.prepareFinish(AccountScope(ownerId), recordId)
                    }
                }
            }
            CardioSessionAction.START -> {
                if (session == null || recordId == null) {
                    host.toast("유산소 기록을 시작하지 못했습니다.")
                    return@LaunchedEffect
                }
                cardioViewModel.rememberActiveRecord(recordId)
                if (session.usesGps) host.startCardioTracking(recordId)
                host.toast("${session.activityLabel} 기록을 시작했습니다.")
                navigation.navigate(FitnessScreen.CARDIO_SESSION)
            }
            CardioSessionAction.PREPARE_RESUME -> {
                if (session == null || recordId == null) {
                    host.toast("재개할 유산소 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                val activityType = runCatching {
                    CardioActivityType.fromId(session.activityId)
                }.getOrDefault(CardioActivityType.WALKING)
                if (session.usesGps) host.requestCardioResume(activityType, recordId)
                else cardioViewModel.continueAfterPermissions(AccountScope(ownerId))
            }
            CardioSessionAction.RESUME -> {
                if (recordId == null) {
                    host.toast("재개할 유산소 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                cardioViewModel.rememberActiveRecord(recordId)
                if (session?.usesGps == true) host.resumeCardioTracking(recordId)
                host.toast("운동 기록을 재개했습니다.")
                navigation.navigate(FitnessScreen.CARDIO_SESSION)
            }
            CardioSessionAction.PAUSE -> {
                if (recordId == null) return@LaunchedEffect
                if (session?.usesGps == true) host.pauseCardioTracking(recordId)
                host.toast("운동 기록을 일시정지했습니다.")
                cardioViewModel.refresh(AccountScope(ownerId), recordId)
            }
            CardioSessionAction.PREPARE_FINISH -> {
                if (event.outcome == CardioSessionActionOutcome.COMPLETED) {
                    if (recordId != null) cardioViewModel.rememberActiveRecord(recordId)
                    navigation.navigate(FitnessScreen.CARDIO_SUMMARY)
                } else if (recordId != null && session != null) {
                    if (event.pausedByFinish && session.usesGps) host.pauseCardioTracking(recordId)
                    cardioViewModel.openHeartRateEditor(
                        AccountScope(ownerId), recordId, true, session
                    )
                }
            }
            CardioSessionAction.FINISH -> {
                if (recordId == null || session == null) {
                    host.toast("평균 심박수를 저장하지 못했습니다.")
                    return@LaunchedEffect
                }
                cardioViewModel.rememberActiveRecord(recordId)
                host.stopCardioTracking()
                cardioViewModel.dismissHeartRateEditor()
                host.toast("유산소 운동을 완료했습니다.")
                if (screen == FitnessScreen.CARDIO_SESSION) {
                    navigation.replace(FitnessScreen.CARDIO_SUMMARY)
                } else {
                    navigation.navigate(FitnessScreen.CARDIO_SUMMARY)
                }
            }
            CardioSessionAction.PREPARE_HEART_RATE_EDIT -> {
                if (recordId != null && session != null) {
                    cardioViewModel.openHeartRateEditor(
                        AccountScope(ownerId), recordId, false, session
                    )
                }
            }
            CardioSessionAction.UPDATE_HEART_RATE -> {
                if (recordId != null) {
                    cardioViewModel.dismissHeartRateEditor()
                    host.toast("평균 심박수를 저장했습니다.")
                    cardioViewModel.refresh(AccountScope(ownerId), recordId)
                }
            }
            CardioSessionAction.PREPARE_CANCEL -> {
                if (recordId != null && session != null) {
                    cardioViewModel.openCancelConfirmation(
                        AccountScope(ownerId), recordId, session
                    )
                }
            }
            CardioSessionAction.CANCEL -> {
                if (recordId == null) {
                    host.toast("취소할 유산소 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                host.stopCardioTracking()
                clearActiveWorkout(viewModels, recordId)
                host.toast("유산소 기록을 취소했습니다.")
                navigation.replace(FitnessScreen.CARDIO)
            }
            CardioSessionAction.LOAD_ROUTE -> Unit
        }
    }

    LaunchedEffect(manualPastState) {
        val state = manualPastState
        if (state is ManualPastWorkoutUiState.Error && state.ownerId == ownerId) {
            host.toast(state.message)
            viewModels.getWorkoutSession().dismissManualPastEditor()
        }
    }

    LaunchedEffect(workoutDeleteConfirmationState) {
        val state = workoutDeleteConfirmationState
        if (state is WorkoutDeleteConfirmationUiState.Ready && state.ownerId != ownerId) {
            viewModels.getWorkoutSession().dismissDeleteConfirmation()
        }
    }

    LaunchedEffect(cardioHeartRateEditorState) {
        val state = cardioHeartRateEditorState
        if (state is CardioHeartRateEditorUiState.Ready && state.ownerId != ownerId) {
            viewModels.getCardioSession().dismissHeartRateEditor()
        }
    }

    LaunchedEffect(cardioCancelConfirmationState) {
        val state = cardioCancelConfirmationState
        if (state is CardioCancelConfirmationUiState.Ready && state.ownerId != ownerId) {
            viewModels.getCardioSession().dismissCancelConfirmation()
        }
    }

    val homeSnapshot = (homeState as? HomeUiState.Ready)?.snapshot
    val manualPastActions = object : ManualPastWorkoutActions {
        override fun start(
            date: String,
            title: String,
            routineId: String?,
            exercises: List<com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance>,
            startedAt: String,
            endedAt: String
        ) = viewModels.getWorkoutSession().startManualPast(
            AccountScope(ownerId), date, title, routineId, exercises, startedAt, endedAt
        )

        override fun dismiss() = viewModels.getWorkoutSession().dismissManualPastEditor()
        override fun notify(message: String) = host.toast(message)
    }

    val editorActions = object : BodyMetricsEditorActions, DevelopmentEditorActions {
        override fun save(
            recordId: String?,
            date: String,
            weightKg: Double,
            memo: String
        ) = viewModels.getBodyMetrics().save(
            AccountScope(ownerId), recordId, date, weightKg, memo
        )

        override fun delete(recordId: String) = viewModels.getBodyMetrics().delete(
            AccountScope(ownerId), recordId
        )

        override fun saveProfile(
            profile: BodyProfile?,
            weightRecordId: String?,
            date: String,
            weightKg: Double?,
            memo: String
        ) = viewModels.getDevelopment().saveProfileAndWeight(
            AccountScope(ownerId), profile, weightRecordId, date, weightKg, memo
        )

        override fun saveGoal(goal: com.yeonsik.fitnessapp.development.DevelopmentGoal) =
            viewModels.getDevelopment().saveGoal(AccountScope(ownerId), goal)

        override fun saveNutritionGoal(goal: com.yeonsik.fitnessapp.data.AthleteNutritionGoal) =
            viewModels.getDevelopment().saveNutritionGoal(AccountScope(ownerId), goal)

        override fun saveRecoveryCheckIn(checkIn: com.yeonsik.fitnessapp.data.AthleteDailyCheckIn) =
            viewModels.getDevelopment().saveCheckIn(AccountScope(ownerId), checkIn)

        override fun dismiss() {
            viewModels.getBodyMetrics().dismissEditor()
            viewModels.getDevelopment().dismissProfileEditor()
            viewModels.getDevelopment().dismissGoalEditor()
        }

        override fun dismissRecovery() = viewModels.getDevelopment().dismissRecoveryEditor()

        override fun notify(message: String) = host.toast(message)
    }

    LaunchedEffect(bodyEditorState) {
        when (val state = bodyEditorState) {
            is BodyMetricsEditorUiState.Saved -> {
                host.toast("체중 기록을 저장했습니다.")
                viewModels.getHome().markStale()
                viewModels.getHome().enter(AccountScope(ownerId), routeDate)
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getBodyMetrics().dismissEditor()
            }
            is BodyMetricsEditorUiState.Deleted -> {
                host.toast("체중 기록을 삭제했습니다.")
                viewModels.getHome().markStale()
                viewModels.getHome().enter(AccountScope(ownerId), routeDate)
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getBodyMetrics().dismissEditor()
            }
            is BodyMetricsEditorUiState.Error -> {
                if (state.ownerId == ownerId) host.toast(state.message)
                viewModels.getBodyMetrics().dismissEditor()
            }
            else -> Unit
        }
    }

    LaunchedEffect(profileEditorState) {
        when (val state = profileEditorState) {
            is DevelopmentProfileEditorUiState.Saved -> {
                host.toast("바디 정보를 저장했습니다.")
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getDevelopment().dismissProfileEditor()
            }
            is DevelopmentProfileEditorUiState.Error -> {
                if (state.ownerId == ownerId) host.toast(state.message)
                viewModels.getDevelopment().dismissProfileEditor()
            }
            else -> Unit
        }
    }

    LaunchedEffect(goalEditorState) {
        when (val state = goalEditorState) {
            is DevelopmentGoalEditorUiState.Saved -> {
                host.toast("발전 목표를 저장했습니다.")
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getDevelopment().dismissGoalEditor()
            }
            is DevelopmentGoalEditorUiState.Error -> {
                if (state.ownerId == ownerId) host.toast(state.message)
                viewModels.getDevelopment().dismissGoalEditor()
            }
            else -> Unit
        }
    }
    LaunchedEffect(recoveryEditorState) {
        when (val state = recoveryEditorState) {
            is RecoveryEditorUiState.Saved -> {
                host.toast(
                    if (state.editor == RecoveryEditorKind.NUTRITION_GOAL) {
                        "영양 목표를 저장했습니다."
                    } else {
                        "임시저장 완료"
                    }
                )
                viewModels.getDevelopment().dismissRecoveryEditor()
                viewModels.getHome().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getDevelopment().enterRecovery(AccountScope(ownerId), navigationState.today)
            }
            is RecoveryEditorUiState.Error -> {
                if (state.ownerId == ownerId) host.toast(state.message)
                viewModels.getDevelopment().dismissRecoveryEditor()
            }
            else -> Unit
        }
    }

    WorkoutRestTimerHost(restState, ownerId, onElapsed = { timer ->
        if (timer.ownerId == host.currentOwnerId() &&
            viewModels.getWorkoutSession().completeRestTimerIfDue(timer.ownerId, timer.endsAtMillis)) {
            host.toast("휴식 종료. 다음 세트를 시작하세요.")
        }
    })
    FitnessComposeTheme(dark) {
        if (!startupCompleted) {
            StartupOrbScreen()
            return@FitnessComposeTheme
        }
        val backdrop = rememberFitnessLayerBackdrop()
        val highContrastEnabled = rememberHighContrastTextEnabled()
        val bottomNavigationVisible = isBottomNavigationVisible(screen)
        val useBackdropGlass = fitnessGlassSupported(Build.VERSION.SDK_INT, highContrastEnabled)
        val bottomNavigationHeight = with(LocalDensity.current) {
            // The icon and scaled label must both fit inside the capsule.
            36.dp + MaterialTheme.typography.labelMedium.lineHeight.toDp()
        }.coerceAtLeast(56.dp)
        val layoutDirection = LocalLayoutDirection.current
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // The root consumes IME height. Only the uncovered navigation-bar portion remains.
        val navigationBarInset = WindowInsets.navigationBars.exclude(WindowInsets.ime)
            .asPaddingValues().calculateBottomPadding()
        val horizontalInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal).asPaddingValues()
        val restTimerVisible = restState is WorkoutRestTimerState.Active &&
            (restState as WorkoutRestTimerState.Active).ownerId == ownerId &&
            (screen == FitnessScreen.WORKOUT_SESSION || screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL)
        var restBarHeightPx by remember { mutableIntStateOf(0) }
        val restBarHeight = with(LocalDensity.current) { restBarHeightPx.toDp() }
            .coerceAtLeast(80.dp + navigationBarInset)
        val bottomNavigationSpace = bottomNavigationHeight + 24.dp
        CompositionLocalProvider(
            LocalFitnessGlassEnabled provides useBackdropGlass,
            LocalFitnessGlassHighContrast provides highContrastEnabled,
            LocalFitnessPageBackdrop provides backdrop
        ) {
        val destinationPage: @Composable (FitnessScreen, Boolean) -> Unit = { pageScreen, isActualActive ->
            val recordsBackdrop = rememberFitnessLayerBackdrop()
            var sessionBarHeightPx by remember(pageScreen) { mutableIntStateOf(0) }
            val sessionBarHeight = with(LocalDensity.current) { sessionBarHeightPx.toDp() }
                .coerceAtLeast(64.dp + statusBarInset)
            var recordsBarHeightPx by remember(pageScreen) { mutableIntStateOf(0) }
            val recordsBarHeight = if (recordsBarHeightPx > 0) {
                with(LocalDensity.current) { recordsBarHeightPx.toDp() }
            } else {
                FitnessSpacing.touch + (FitnessSpacing.small + FitnessSpacing.micro) * 2
            }
            val pageDestination = if (pageScreen == FitnessScreen.RECORDS) {
                recordsHubTab.screen
            } else {
                pageScreen
            }
            val entranceToken = navigationState.topLevelEntrance
                ?.takeIf { it.destination == pageDestination }?.generation
            val entranceState = when (pageScreen) {
                FitnessScreen.WORKOUT -> workoutEntranceState
                FitnessScreen.RECORDS -> recordsEntranceState
                else -> homeEntranceState
            }
            val pageDate = when (pageDestination) {
                FitnessScreen.MEALS -> navigationState.selectedMealDate
                FitnessScreen.RECORDS -> navigationState.selectedRecordsDate
                else -> navigationState.today
            }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    val workoutPageScope = if (isWorkoutFlowScreen(pageScreen)) {
                        val identity = if (pageScreen == FitnessScreen.ROUTINE_DETAIL)
                            navigationState.selectedRoutineId ?: homeSnapshot?.activeRoutineId
                        else currentWorkoutRecordId(pageScreen, viewModels, homeState)
                        ":$ownerId:$identity"
                    } else ""
                    destinationStateHolder.SaveableStateProvider(
                        destinationScrollStateKey(pageScreen) + workoutPageScope +
                            if (pageScreen == FitnessScreen.RECORDS) ":${recordsHubTab.name}" else ""
                    ) {
                        val contentScrollState = rememberScrollState()
                        val tabScrollReset = navigationState.topLevelTabScrollReset
                            ?.takeIf { it.destination == pageScreen }
                        LaunchedEffect(tabScrollReset?.generation, isActualActive) {
                            val reset = tabScrollReset ?: return@LaunchedEffect
                            if (!isActualActive) return@LaunchedEffect
                            contentScrollState.scrollTo(0)
                            navigation.consumeTopLevelTabScrollReset(reset)
                        }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if ((pageScreen == FitnessScreen.RECORDS || pageScreen == FitnessScreen.WORKOUT_SESSION) && useBackdropGlass) {
                                        Modifier.fitnessBackdropSource(recordsBackdrop)
                                    } else Modifier
                                )
                                .then(
                                    if (isExercisePickerDestination(pageScreen) || pageScreen == FitnessScreen.CARDIO ||
                                        pageScreen == FitnessScreen.ROUTINE_DETAIL) {
                                        Modifier
                                    } else {
                                        Modifier.verticalScroll(contentScrollState)
                                    }
                                )
                                .padding(
                                    start = FitnessSpacing.page + horizontalInsets.calculateStartPadding(layoutDirection),
                                    end = FitnessSpacing.page + horizontalInsets.calculateEndPadding(layoutDirection),
                                    top = FitnessSpacing.gap +
                                        if (pageScreen == FitnessScreen.RECORDS) recordsBarHeight
                                        else if (pageScreen == FitnessScreen.WORKOUT_SESSION) sessionBarHeight
                                        else if (pageScreen == FitnessScreen.CARDIO_SESSION) 0.dp else statusBarInset,
                                    bottom = FitnessSpacing.gap +
                                        (if (bottomNavigationVisible) bottomNavigationSpace else 0.dp) +
                                        (if (restTimerVisible) restBarHeight else navigationBarInset)
                                )
                        ) {
                            if (pageDestination == FitnessScreen.HOME) {
                                HomeDestination(
                                    homeState, ownerId, pageDate, homeActions,
                                    activityState = homeActivityState,
                                    onActivityPrevious = viewModels.getHome()::previousActivityPage,
                                    onActivityNext = viewModels.getHome()::nextActivityPage,
                                    onActivitySelectPage = viewModels.getHome()::selectActivityPage,
                                    onActivityRetry = viewModels.getHome()::retryActivityHistory,
                                    activityDayDetails = homeActivityDayDetails,
                                    onActivityDateSelected = viewModels.getHome()::selectActivityDay,
                                    onActivityOpenRecords = { date ->
                                        val scope = AccountScope(ownerId)
                                        viewModels.getRecords().rememberSelectedDate(scope, date)
                                        navigation.selectRecordsDate(date)
                                        navigation.navigate(FitnessScreen.RECORDS)
                                    },
                                    entranceState = homeEntranceState,
                                    preferredMassUnit = unit,
                                    entranceToken = entranceToken,
                                    isActualActive = isActualActive
                                )
                            } else {
                                AppDestination(
                                    host,
                                    viewModels,
                                    navigation,
                                    pageDestination,
                                    ownerId,
                                    pageDate,
                                    unit,
                                    entranceState,
                                    entranceToken,
                                    isActualActive
                                )
                            }
                        }
                    }
                    if (pageScreen == FitnessScreen.WORKOUT_SESSION) {
                        SessionTopBar(navigation, viewModels, ownerId, workoutState,
                            Modifier.align(Alignment.TopCenter)
                                .onSizeChanged { sessionBarHeightPx = it.height }
                                .statusBarsPadding().padding(horizontalInsets)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fitnessGlassSurface(RoundedCornerShape(28.dp), backdrop = recordsBackdrop)
                                .pointerInput(Unit) { detectTapGestures(onTap = {}) })
                    }
                    if (pageScreen == FitnessScreen.RECORDS) {
                        RecordsHubTabs(
                            selected = recordsHubTab,
                            onSelected = navigation::selectRecordsHubTab,
                            backdrop = recordsBackdrop,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .onSizeChanged { recordsBarHeightPx = it.height }
                                .statusBarsPadding()
                                .padding(horizontalInsets)
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (useBackdropGlass) Modifier.fitnessBackdropSource(backdrop) else Modifier)
            ) {
                if (screen == FitnessScreen.CARDIO_SESSION) {
                    Box(Modifier.statusBarsPadding().padding(horizontalInsets)) {
                        CardioSessionTopBar(navigation, viewModels, ownerId, cardioState)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (topLevelSwipeEnabled) {
                        TopLevelSwipeHost(
                            screen, navigation, Modifier.fillMaxSize(),
                            onSettlingDestinationChange = {
                                swipeSelection = it?.let { destination ->
                                    TopLevelSwipeSelection(screen, destination)
                                }
                            },
                            content = destinationPage
                        )
                    } else if (isWorkoutFlowScreen(screen)) {
                        val routineId = navigationState.selectedRoutineId ?: homeSnapshot?.activeRoutineId
                        WorkoutFlowHost(ownerId, screen, currentWorkoutRecordId(screen, viewModels, homeState),
                            workoutState, Modifier.fillMaxSize(), routineId = routineId,
                            routineExercises = homeSnapshot?.routineExercises?.get(routineId).orEmpty(),
                            createdRoutineRecordId = createdRoutineRecordId, content = destinationPage)
                    } else {
                        destinationPage(screen, true)
                    }
                }
            }
            if (restTimerVisible) {
                RestTimerBar(host, viewModels, screen, restState, ownerId,
                    Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { restBarHeightPx = it.height }
                        .navigationBarsPadding().padding(horizontalInsets)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .widthIn(max = 400.dp))
            }
            if (bottomNavigationVisible) {
                BottomNavigation(
                    homeState, visualActiveTopLevelTab(screen, swipeSelection),
                    onTabSelected = {
                        swipeSelection = null
                        navigation.selectTopLevelFromTab(it)
                    },
                    bottomNavigationHeight, backdrop,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontalInsets)
                )
            }
            FitnessStatusBarScrim(Modifier.align(Alignment.TopCenter))
        }
        BodyMetricsEditorDialog(bodyEditorState, ownerId, unit, editorActions)
        DevelopmentProfileEditorDialog(profileEditorState, ownerId, unit, editorActions)
        DevelopmentGoalEditorDialog(
            goalEditorState,
            ownerId,
            navigationState.today,
            editorActions
        )
        RecoveryNutritionGoalEditorDialog(recoveryEditorState, ownerId, editorActions)
        RecoveryCheckInEditorDialog(recoveryEditorState, ownerId, editorActions)
        ManualPastWorkoutDialog(
            manualPastState,
            ownerId,
            homeSnapshot?.routines.orEmpty(),
            homeSnapshot?.routineExercises.orEmpty(),
            navigationState.selectedRoutineId,
            manualPastActions
        )
        val cardioEditorActions = object : CardioHeartRateEditorActions {
            override fun updateInput(value: String) =
                viewModels.getCardioSession().updateHeartRateInput(value)

            override fun updateDistanceInput(value: String) =
                viewModels.getCardioSession().updateDistanceInput(value)

            override fun save(value: String) = viewModels.getCardioSession().submitHeartRate(
                AccountScope(ownerId), value
            )

            override fun dismiss() = viewModels.getCardioSession().dismissHeartRateEditor()
        }
        CardioHeartRateEditorDialog(cardioHeartRateEditorState, ownerId, cardioEditorActions)
        val cardioCancelActions = object : CardioCancelConfirmationActions {
            override fun confirm() = viewModels.getCardioSession().confirmCancel(AccountScope(ownerId))
            override fun dismiss() = viewModels.getCardioSession().dismissCancelConfirmation()
        }
        CardioCancelConfirmationDialog(
            cardioCancelConfirmationState,
            ownerId,
            cardioCancelActions
        )
        val workoutDeleteActions = object : WorkoutDeleteConfirmationActions {
            override fun confirm() = viewModels.getWorkoutSession()
                .confirmDelete(AccountScope(ownerId))

            override fun dismiss() = viewModels.getWorkoutSession().dismissDeleteConfirmation()
        }
        WorkoutDeleteConfirmationDialog(
            workoutDeleteConfirmationState,
            ownerId,
            workoutDeleteActions
        )
        backupPreview?.let { preview ->
            FitnessAlertDialog(
                onDismissRequest = {
                    backupPreview = null
                    host.cancelPendingBackupRestore()
                },
                title = { Text("백업 복원") },
                text = {
                    Text(
                        "${preview.totalRows}개 항목을 현재 기록에 합칩니다. " +
                            "기존 기록은 유지하고 같은 항목은 건너뜁니다."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        backupPreview = null
                        host.confirmPendingBackupRestore()
                    }) { Text("병합 복원") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        backupPreview = null
                        host.cancelPendingBackupRestore()
                    }) { Text("취소") }
                }
            )
        }
        }
    }
}

private fun currentWorkoutRecordId(
    screen: FitnessScreen,
    viewModels: AppViewModels,
    homeState: HomeUiState
): String? = when (screen) {
    FitnessScreen.ROUTINE_DETAIL -> viewModels.getWorkoutSession().activeRecordId()
        ?: (homeState as? HomeUiState.Ready)?.snapshot?.inProgressSessionId
    FitnessScreen.WORKOUT_SESSION,
    FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_EXERCISE_ADD,
    FitnessScreen.WORKOUT_SUMMARY -> viewModels.getWorkoutSession().activeRecordId()
    FitnessScreen.CARDIO_SESSION,
    FitnessScreen.CARDIO_SUMMARY -> viewModels.getCardioSession().activeRecordId()
    else -> (homeState as? HomeUiState.Ready)?.snapshot?.inProgressSessionId
}

private fun clearActiveWorkout(viewModels: AppViewModels, recordId: String) {
    viewModels.getWorkoutSession().clearActiveRecordIfMatches(recordId)
    viewModels.getCardioSession().clearActiveRecordIfMatches(recordId)
    viewModels.getExercisePicker().clearActiveRecordIfMatches(recordId)
    viewModels.getWorkoutExerciseDetail().clearActiveExercise()
}

private fun openWorkoutSession(
    navigation: AppNavigationViewModel,
    viewModels: AppViewModels,
    recordId: String
) {
    viewModels.getWorkoutSession().rememberActiveRecord(recordId)
    viewModels.getWorkoutExerciseDetail().clearActiveExercise()
    navigation.navigate(FitnessScreen.WORKOUT_SESSION)
}

@Composable
private fun SessionTopBar(
    navigation: AppNavigationViewModel,
    viewModels: AppViewModels,
    ownerId: String,
    state: WorkoutSessionUiState,
    modifier: Modifier = Modifier
) {
    val recordId = viewModels.getWorkoutSession().activeRecordId()
    val ready = state as? WorkoutSessionUiState.Ready
    val canEdit = ready?.let {
        it.ownerId == ownerId && it.session.status != "completed"
    } == true
    FitnessSessionTopBar(
        title = "운동 세션",
        modifier = modifier,
        containerColor = Color.Transparent,
        canCancel = ready?.let {
            it.ownerId == ownerId && it.session.status == "in_progress"
        } == true,
        onBack = {
            if (!navigation.back()) navigation.replace(FitnessScreen.STRENGTH)
        },
        onCancel = {
            recordId?.let {
                viewModels.getWorkoutSession().openCancelConfirmation(
                    AccountScope(ownerId), it
                )
            }
        }
    ) {
        if (canEdit) {
            TextButton(onClick = {
                navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_ADD)
            }) { Text("종목 추가") }
            TextButton(
                onClick = {
                    recordId?.let {
                        viewModels.getWorkoutSession().finish(AccountScope(ownerId), it)
                    }
                },
                enabled = recordId != null
            ) { Text("완료") }
        }
    }
}

@Composable
private fun CardioSessionTopBar(
    navigation: AppNavigationViewModel,
    viewModels: AppViewModels,
    ownerId: String,
    state: CardioSessionUiState
) {
    val ready = state as? CardioSessionUiState.Ready
    val session = ready?.session
    val canCancel = ready?.let {
        it.ownerId == ownerId && it.session.status != CardioSessionSnapshot.STATUS_COMPLETED
    } == true
    FitnessSessionTopBar(
        title = session?.activityLabel ?: "유산소 진행",
        canCancel = canCancel,
        onBack = {
            if (!navigation.back()) navigation.replace(FitnessScreen.CARDIO)
        },
        onCancel = {
            session?.recordId?.let { recordId ->
                viewModels.getCardioSession().prepareCancel(AccountScope(ownerId), recordId)
            }
        }
    )
}

@Composable
private fun RestTimerBar(
    host: AppUiActions,
    viewModels: AppViewModels,
    screen: FitnessScreen,
    state: WorkoutRestTimerState,
    ownerId: String,
    modifier: Modifier = Modifier
) {
    val active = state as? WorkoutRestTimerState.Active
    val visible = active != null
        && ownerId == active.ownerId
        && (screen == FitnessScreen.WORKOUT_SESSION
            || screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL)
    var now by remember(active?.endsAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(active?.endsAtMillis, visible) {
        if (!visible || active == null) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    if (!visible || active == null) return
    var expanded by rememberSaveable(active.ownerId, active.endsAtMillis) { mutableStateOf(false) }
    val remainingMillis = (active.endsAtMillis - now).coerceAtLeast(0L)
    val remainingSeconds = ((remainingMillis + 999L) / 1000L).toInt()
    val ratio = (remainingMillis / (active.totalSeconds * 1000f)).coerceIn(0f, 1f)
    val timeLabel = String.format(Locale.ROOT, "%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    val radius by animateDpAsState(if (expanded) 28.dp else 100.dp, tween(180), label = "rest-capsule-radius")
    Column(
        modifier
            .fillMaxWidth().heightIn(min = 56.dp)
            .fitnessGlassSurface(RoundedCornerShape(radius))
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .animateContentSize(tween(180))
            .padding(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f).heightIn(min = FitnessSpacing.touch)
                .semantics { stateDescription = if (expanded) "휴식 타이머 펼쳐짐" else "휴식 타이머 접힘" }) {
                Text(if (expanded) "휴식 · 접기" else "휴식 · 펼치기")
            }
            if (!expanded) Text(timeLabel, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            TextButton(onClick = { viewModels.getWorkoutSession().stopRestTimer() }) {
                Text("건너뛰기")
            }
        }
        if (expanded) {
            Text(timeLabel, Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"))
            Text("호흡을 정리하고 다음 세트를 준비하세요.",
                Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(3.dp))
    }
}

private data class NavigationItem(val label: String, val screen: FitnessScreen)

@Composable
private fun RecordsHubTabs(
    selected: RecordsHubTab,
    onSelected: (RecordsHubTab) -> Unit,
    backdrop: FitnessBackdrop,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .padding(horizontal = 24.dp, vertical = FitnessSpacing.small)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .fitnessGlassSurface(CircleShape, backdrop = backdrop)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(FitnessSpacing.micro)
            .selectableGroup()
    ) {
        RecordsHubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = FitnessSpacing.touch)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.fitnessGlassSurface(CircleShape, FitnessGlassLevel.SELECTION, backdrop)
                        else Modifier
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(tab) }
                    )
                    .padding(vertical = FitnessSpacing.micro),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BottomNavigation(
    homeState: HomeUiState,
    screen: FitnessScreen,
    onTabSelected: (FitnessScreen) -> Unit,
    itemHeight: Dp,
    backdrop: FitnessBackdrop,
    modifier: Modifier = Modifier
) {
    val dark = LocalFitnessDarkTheme.current
    val items = listOf(
        NavigationItem("메인", FitnessScreen.HOME),
        NavigationItem("피트니스", FitnessScreen.WORKOUT),
        NavigationItem("기록", FitnessScreen.RECORDS),
        NavigationItem("설정", FitnessScreen.SETTINGS)
    )
    val active = navigationRoot(screen)
    val workoutInProgress = (homeState as? HomeUiState.Ready)?.snapshot?.inProgressSessionId != null
    val selectedIndex = items.indexOfFirst { it.screen == active }.coerceAtLeast(0)
    val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant
    val progressTint = LocalFitnessColors.current.action
    // Give the longer Korean label more room at large accessibility font sizes.
    val workoutTabWeight = if (LocalDensity.current.fontScale > 1.5f) 1.6f else 1f
    val tabWeights = items.map { if (it.screen == FitnessScreen.WORKOUT) workoutTabWeight else 1f }

    Box(
        modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .fitnessGlassSurface(CircleShape, backdrop = backdrop)
            // Consume taps on the glass rim rather than activating content underneath it.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(4.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
        ) {
            val widthPerWeight = maxWidth / tabWeights.sum()
            val indicatorWidth by animateDpAsState(
                targetValue = widthPerWeight * tabWeights[selectedIndex],
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "bottom-navigation-indicator-width"
            )
            val indicatorOffset by animateDpAsState(
                targetValue = widthPerWeight * tabWeights.take(selectedIndex).sum(),
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "bottom-navigation-indicator"
            )
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .fitnessGlassSurface(CircleShape, FitnessGlassLevel.SELECTION, backdrop)
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    val interactionSource = remember(item.screen) { MutableInteractionSource() }
                    val selected = item.screen == active
                    val contentColor = if (selected) selectedContent else unselectedContent
                    val hasActiveWorkout = item.screen == FitnessScreen.WORKOUT && workoutInProgress
                    Column(
                        Modifier
                            .weight(tabWeights[index])
                            .fillMaxHeight()
                            .fitnessGlassPressFeedback(interactionSource)
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onTabSelected(item.screen) }
                            )
                            .semantics {
                                if (hasActiveWorkout) stateDescription = "운동 진행 중"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            BottomNavigationIcon(item.screen, contentColor, Modifier.fillMaxSize())
                            if (hasActiveWorkout) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-1).dp)
                                        .size(6.dp)
                                        .background(progressTint, CircleShape)
                                        .border(
                                            1.dp,
                                            if (dark) Color(FitnessUiTokens.GLASS_BADGE_STROKE_DARK) else Color.White,
                                            CircleShape
                                        )
                                )
                            }
                        }
                        Text(
                            item.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun navigationRoot(screen: FitnessScreen): FitnessScreen = when (screen) {
    FitnessScreen.HOME -> FitnessScreen.HOME
    FitnessScreen.RECORDS,
    FitnessScreen.STATISTICS,
    FitnessScreen.DEVELOPMENT -> FitnessScreen.RECORDS
    FitnessScreen.SETTINGS -> FitnessScreen.SETTINGS
    FitnessScreen.MEALS,
    FitnessScreen.WORKOUT,
    FitnessScreen.STRENGTH,
    FitnessScreen.CARDIO,
    FitnessScreen.SUPPLEMENTS,
    FitnessScreen.ROUTINE_DETAIL,
    FitnessScreen.ROUTINE_ADD,
    FitnessScreen.WORKOUT_EXERCISE_ADD,
    FitnessScreen.WORKOUT_SESSION,
    FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_SUMMARY,
    FitnessScreen.CARDIO_SESSION,
    FitnessScreen.CARDIO_SUMMARY -> FitnessScreen.WORKOUT
}

private fun isBottomNavigationVisible(screen: FitnessScreen): Boolean = when (screen) {
    FitnessScreen.ROUTINE_DETAIL,
    FitnessScreen.ROUTINE_ADD,
    FitnessScreen.WORKOUT_SESSION,
    FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_EXERCISE_ADD,
    FitnessScreen.WORKOUT_SUMMARY,
    FitnessScreen.CARDIO_SESSION,
    FitnessScreen.CARDIO_SUMMARY -> false
    else -> true
}

@Composable
private fun AppDestination(
    host: AppUiActions,
    viewModels: AppViewModels,
    navigation: AppNavigationViewModel,
    screen: FitnessScreen,
    ownerId: String,
    today: String,
    unit: MassUnit,
    entranceState: TopLevelEntranceState,
    entranceToken: Long?,
    isActualActive: Boolean
) {
    val homeState by viewModels.getHome().uiState.observeAsState(HomeUiState.Idle)
    val recordsState by viewModels.getRecords().uiState
        .observeAsState(RecordsUiState.Idle)
    val statisticsState by viewModels.getStatistics().uiState
        .observeAsState(StatisticsUiState.Idle)
    val routineState by viewModels.getRoutineEntry().uiState.observeAsState(RoutineEntryUiState.Idle)
    val workoutState by viewModels.getWorkoutSession().uiState
        .observeAsState(WorkoutSessionUiState.Idle)
    val workoutDetailState by viewModels.getWorkoutExerciseDetail().uiState
        .observeAsState(WorkoutExerciseDetailUiState.Idle)
    val routineStarting by viewModels.getWorkoutSession().routineStarting.observeAsState(false)
    val cardioState by viewModels.getCardioSession().uiState
        .observeAsState(CardioSessionUiState.Idle)
    val cardioRouteState by viewModels.getCardioSession().routeState
        .observeAsState(CardioRouteUiState.Idle)
    val developmentState by viewModels.getDevelopment().uiState
        .observeAsState(DevelopmentUiState.Idle)
    val paperAdviceState by viewModels.getDevelopment().paperAdviceState
        .observeAsState(PaperAdviceUiState.Idle)
    val mealState by viewModels.getMeal().uiState.observeAsState(MealUiState.Idle)
    val mealPriceTraceState by viewModels.getMeal().priceTraceState
        .observeAsState(PriceTraceUiState.Idle)
    val mealNutritionPublicationState by viewModels.getMeal().nutritionPublicationState
        .observeAsState(NutritionPublicationUiState())
    val nutritionEditorState by viewModels.getMeal().nutritionEditor.uiState
        .observeAsState(NutritionEditorState())
    val supplementState by viewModels.getSupplement().uiState
        .observeAsState(SupplementUiState.Idle)
    val exercisePickerState by viewModels.getExercisePicker().uiState
        .observeAsState(ExercisePickerUiState.Idle)
    val settingsState by viewModels.getSettings().uiState.observeAsState()
    val activeRecordId = currentWorkoutRecordId(screen, viewModels, homeState)
    val inProgressSessionId = (homeState as? HomeUiState.Ready)?.snapshot
        ?.takeIf { it.ownerId == ownerId }?.inProgressSessionId
    var inProgressIsCardio by remember(ownerId, inProgressSessionId) {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(screen, ownerId, inProgressSessionId, isActualActive) {
        if (screen != FitnessScreen.WORKOUT || !isActualActive || inProgressSessionId == null) {
            return@LaunchedEffect
        }
        if (inProgressIsCardio == null) {
            inProgressIsCardio = viewModels.getWorkoutSession().readInProgressIsCardio(
                AccountScope(ownerId), inProgressSessionId
            )
        }
    }
    val workoutReadOnly = (workoutState as? WorkoutSessionUiState.Ready)?.let {
        it.session.status == "completed"
    } == true
    LaunchedEffect(screen, ownerId, today) {
        when (screen) {
            FitnessScreen.DEVELOPMENT ->
                viewModels.getDevelopment().also { development ->
                    development.enterIfNeeded(AccountScope(ownerId), today)
                    development.enterRecoveryIfNeeded(AccountScope(ownerId), today)
                }
            FitnessScreen.MEALS ->
                viewModels.getMeal().enter(AccountScope(ownerId), today)
            FitnessScreen.STATISTICS -> viewModels.getStatistics().enterIfNeeded(AccountScope(ownerId), today)
            FitnessScreen.SETTINGS -> viewModels.getSettings().enterIfNeeded()
            FitnessScreen.SUPPLEMENTS ->
                viewModels.getSupplement().enter(AccountScope(ownerId), today)
            else -> Unit
        }
    }

    LaunchedEffect(RecordEntryEffectKey(screen, ownerId, activeRecordId), isActualActive) {
        if (!isActualActive) return@LaunchedEffect
        when (screen) {
            FitnessScreen.WORKOUT_SESSION,
            FitnessScreen.WORKOUT_SUMMARY ->
                viewModels.getWorkoutSession().enter(
                    AccountScope(ownerId), activeRecordId
                )
            FitnessScreen.CARDIO_SESSION,
            FitnessScreen.CARDIO_SUMMARY ->
                viewModels.getCardioSession().enter(
                    AccountScope(ownerId), activeRecordId
                )
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD ->
                viewModels.getExercisePicker().let { picker ->
                    val replacementId = picker.activeReplacementId()
                    picker.enter(
                        AccountScope(ownerId),
                        screen,
                        activeRecordId,
                        replacementId,
                        navigation.selectedRoutineId(),
                        picker.selectionModeForTarget(screen, replacementId)
                    )
                }
            else -> Unit
        }
    }
    LaunchedEffect(screen, ownerId, activeRecordId, workoutReadOnly, isActualActive) {
        if (screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL && isActualActive) {
            activeRecordId?.let { recordId ->
                viewModels.getWorkoutExerciseDetail().enter(
                    AccountScope(ownerId),
                    recordId,
                    viewModels.getWorkoutExerciseDetail().activeExerciseId(),
                    readOnly = workoutReadOnly
                )
            }
        }
    }
    val mealNotice = (mealState as? MealUiState.Ready)?.notice
    LaunchedEffect(screen, ownerId, today, mealNotice) {
        if (screen == FitnessScreen.MEALS && mealNotice != null) {
            viewModels.getHome().enter(AccountScope(ownerId), today)
        }
    }
    val cardioStartState by viewModels.getCardioStart().uiState.observeAsState(CardioStartUiState())
    val cardioStarting by viewModels.getCardioSession().actionPending.observeAsState(false)
    LaunchedEffect(screen, ownerId, isActualActive) {
        if (screen == FitnessScreen.CARDIO && isActualActive) viewModels.getCardioStart().enter(AccountScope(ownerId))
    }
    val cardioRecordId = (cardioState as? CardioSessionUiState.Ready)?.session?.recordId
        ?: viewModels.getCardioSession().activeRecordId()
    val cardioActions = object : CardioScreenActions {
        override fun start(activityType: CardioActivityType) = viewModels.getCardioSession()
            .prepareStart(AccountScope(ownerId), activityType, today)
        override fun back() { navigation.back() }
        override fun refresh() {
            cardioRecordId?.let {
            viewModels.getCardioSession().refresh(AccountScope(ownerId), it)
            }
        }
        override fun pause() {
            cardioRecordId?.let {
            viewModels.getCardioSession().pause(AccountScope(ownerId), it)
            }
        }
        override fun resume() {
            cardioRecordId?.let {
            viewModels.getCardioSession().prepareResume(AccountScope(ownerId), it)
            }
        }
        override fun editAverageHeartRate() {
            cardioRecordId?.let {
                viewModels.getCardioSession().prepareAverageHeartRateEdit(
                    AccountScope(ownerId), it
                )
            }
        }
        override fun finish() {
            cardioRecordId?.let {
                viewModels.getCardioSession().prepareFinish(AccountScope(ownerId), it)
            }
        }
        override fun loadRoute(recordId: String) {
            viewModels.getCardioSession().loadRoute(AccountScope(ownerId), recordId)
        }
    }
    val mealActions = object : MealScreenActions {
        override fun back() { navigation.back() }
        override fun selectDate(date: String) = navigation.selectMealDate(date)
        override fun startDraft() = viewModels.getMeal().startDraft()
        override fun closeDraft() = viewModels.getMeal().closeDraft()
        override fun chooseFood() = viewModels.getMeal().chooseFood()
        override fun chooseDiningOut() = viewModels.getMeal().chooseDiningOut()
        override fun chooseCategory(category: com.yeonsik.fitnessapp.feature.meal.ui.MealCatalogCategory) = viewModels.getMeal().chooseCategory(category)
        override fun searchFood(query: String) = viewModels.getMeal().search(query)
        override fun selectFood(food: com.yeonsik.fitnessapp.data.NutritionFood) {
            viewModels.getMeal().selectFood(food)
        }
        override fun selectFood(food: com.yeonsik.fitnessapp.data.NutritionFood, quantity: String,
            mode: com.yeonsik.fitnessapp.feature.nutrition.model.PortionInputMode) {
            viewModels.getMeal().selectFood(food, quantity, mode)
        }
        override fun useDiningOutFood(food: com.yeonsik.fitnessapp.data.NutritionFood) {
            viewModels.getMeal().useDiningOutFood(food)
        }
        override fun saveReusableDiningOutMenu() =
            viewModels.getMeal().saveReusableDiningOutMenu(AccountScope(ownerId)) { }
        override fun updateQuantity(value: String) = viewModels.getMeal().updateQuantity(value)
        override fun updateFoodQuantity(itemId: String, value: String) =
            viewModels.getMeal().updateFoodQuantity(itemId, value)
        override fun removeFood(itemId: String) = viewModels.getMeal().removeFood(itemId)
        override fun updateFoodAmountMode(itemId: String, mode: com.yeonsik.fitnessapp.feature.nutrition.model.PortionInputMode) = viewModels.getMeal().updateFoodAmountMode(itemId, mode)
        override fun updateDiningPortion(value: String) = viewModels.getMeal().updateDiningPortion(value)
        override fun updateFoodConsumedPercent(itemId: String, value: String) = viewModels.getMeal().updateFoodConsumedPercent(itemId, value)
        override fun updateDiningConsumedPercent(value: String) = viewModels.getMeal().updateDiningConsumedPercent(value)
        override fun addDiningMenu() = viewModels.getMeal().addDiningMenu()
        override fun startAnotherDiningMenu(menuId: String) = viewModels.getMeal().startAnotherDiningMenu(menuId)
        override fun clearCurrentDiningMenu() = viewModels.getMeal().clearCurrentDiningMenu()
        override fun updateDiningMenu(menu: MealDiningDraftItem) = viewModels.getMeal().updateDiningMenu(menu)
        override fun removeDiningMenu(menuId: String) = viewModels.getMeal().removeDiningMenu(menuId)
        override fun undoRemoveItem() = viewModels.getMeal().undoRemoveItem()
        override fun openNutritionEditor() = viewModels.getMeal().nutritionEditor.open()
        override fun updateTime(value: String) = viewModels.getMeal().updateTime(value)
        override fun saveFood() = viewModels.getMeal().saveFood(AccountScope(ownerId)) { }
        override fun openManualFood() = viewModels.getMeal().openManualFood()
        override fun closeManualFood() = viewModels.getMeal().closeManualFood()
        override fun updateManualFood(draft: ManualFoodDraft) = viewModels.getMeal().updateManualFood(draft)
        override fun saveManualFood() = viewModels.getMeal().saveManualFood(AccountScope(ownerId))
        override fun updateStore(value: String) = viewModels.getMeal().updateStore(value)
        override fun updateBranch(value: String) = viewModels.getMeal().updateBranch(value)
        override fun updateMenu(value: String) = viewModels.getMeal().updateMenu(value)
        override fun updateCalories(value: String) = viewModels.getMeal().updateCalories(value)
        override fun updateCarbs(value: String) = viewModels.getMeal().updateCarbs(value)
        override fun updateProtein(value: String) = viewModels.getMeal().updateProtein(value)
        override fun updateFat(value: String) = viewModels.getMeal().updateFat(value)
        override fun updateSodium(value: String) = viewModels.getMeal().updateSodium(value)
        override fun updateSugars(value: String) = viewModels.getMeal().updateSugars(value)
        override fun updateSaturatedFat(value: String) = viewModels.getMeal().updateSaturatedFat(value)
        override fun updateTransFat(value: String) = viewModels.getMeal().updateTransFat(value)
        override fun updatePriceTraceQuery(value: String) =
            viewModels.getMeal().updatePriceTraceQuery(value)
        override fun searchPriceTraceRestaurants() =
            viewModels.getMeal().searchPriceTraceRestaurants()
        override fun loadPriceTraceRestaurant(restaurantId: String) =
            viewModels.getMeal().loadPriceTraceRestaurant(restaurantId)
        override fun openNutritionPublication() =
            viewModels.getMeal().openNutritionPublication()
        override fun closeNutritionPublication() =
            viewModels.getMeal().closeNutritionPublication()
        override fun createDiningOutMenu() = viewModels.getMeal().createDiningOutMenu()
        override fun selectNutritionPublicationMenu(foodId: String) =
            viewModels.getMeal().selectNutritionPublicationMenu(foodId)
        override fun syncNutritionPublicationCatalog() =
            viewModels.getMeal().syncNutritionPublicationCatalog()
        override fun publishNutritionMenu(locationId: String, menuId: String, catalogProductId: String) =
            viewModels.getMeal().publishNutritionMenuForPriceTraceSelection(
                locationId,
                menuId,
                catalogProductId
            )
        override fun verifyNutritionMenu(locationId: String, menuId: String, catalogProductId: String) =
            viewModels.getMeal().verifyNutritionMenuForPriceTraceSelection(locationId, menuId, catalogProductId)
        override fun proposeDiningMerchant(facts: com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts) =
            viewModels.getMeal().proposeDiningMerchant(facts)
        override fun proposeDiningMenu(locationId: String?, merchantCandidateId: String?, menuName: String) =
            viewModels.getMeal().proposeDiningMenu(locationId, merchantCandidateId, menuName)
        override fun resubmitDiningMerchant(previousCandidateId: String,
            facts: com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts, userVerified: Boolean) =
            viewModels.getMeal().resubmitDiningMerchant(previousCandidateId, facts, userVerified)
        override fun resubmitDiningMenu(previousCandidateId: String, restaurantId: String?, locationId: String?,
            merchantCandidateId: String?, menuName: String, userVerified: Boolean) =
            viewModels.getMeal().resubmitDiningMenu(previousCandidateId, restaurantId, locationId,
                merchantCandidateId, menuName, userVerified)
        override fun refreshDiningProposals() = viewModels.getMeal().refreshDiningProposals()
        override fun publishApprovedDiningProposal() = viewModels.getMeal().publishApprovedDiningProposal()
        override fun applyPriceTraceSelection(
            restaurantId: String,
            restaurantName: String,
            locationId: String,
            branchName: String,
            menuId: String,
            menuName: String,
            catalogProductId: String
        ) = viewModels.getMeal().applyPriceTraceSelection(
            restaurantId,
            restaurantName,
            locationId,
            branchName,
            menuId,
            menuName,
            catalogProductId
        )
        override fun saveDiningOut() = viewModels.getMeal().save(AccountScope(ownerId)) { }
        override fun editMeal(meal: com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary) =
            viewModels.getMeal().openRecordEditor(meal)
        override fun editMealDateTime(meal: com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary) =
            viewModels.getMeal().openMealDateTimeEditor(meal)
        override fun deleteMeal(recordId: String) =
            viewModels.getMeal().deleteMeal(AccountScope(ownerId), recordId)
        override fun saveMealTime(recordId: String, mealTime: String) =
            viewModels.getMeal().saveMealTime(AccountScope(ownerId), recordId, mealTime)
        override fun saveMealDateTime(recordId: String, date: String, mealTime: String) =
            viewModels.getMeal().saveMealDateTime(AccountScope(ownerId), recordId, date, mealTime)
        override fun viewMealDetail(recordId: String) =
            viewModels.getMeal().openMealDetail(AccountScope(ownerId), recordId)
        override fun closeMealDetail() = viewModels.getMeal().closeMealDetail()
        override fun cancelMealDateTimeEdit() = viewModels.getMeal().closeMealDateTimeEditor()
    }
    val settingsActions = object : SettingsScreenActions {
        override fun setPreferredMassUnit(unit: MassUnit) = viewModels.getSettings().setPreferredMassUnit(unit)
        override fun setThemeMode(mode: String) = viewModels.getSettings().setThemeMode(mode)
        override fun runManualSync() = viewModels.getSettings().runManualSync()
        override fun openFleekDataImport() = host.openFleekDataImport()
        override fun openWorkoutTransferImport() = host.openWorkoutTransferImport()
        override fun exportWorkoutTransfer() = host.exportWorkoutTransfer()
        override fun createLocalBackup() = host.createLocalBackup()
        override fun restoreLocalBackup() = host.restoreLocalBackup()
        override fun exportRecordsCsv() = host.exportRecordsCsv()
        override fun saveConnection(connection: SettingsConnection, url: String, anonKey: String) =
            viewModels.getSettings().saveConnection(connection, url, anonKey)
        override fun signIn(connection: SettingsConnection, email: String, password: String) =
            viewModels.getSettings().signIn(connection, email, password)
        override fun signUp(connection: SettingsConnection, email: String, password: String) =
            viewModels.getSettings().signUp(connection, email, password)
        override fun signOut(connection: SettingsConnection) =
            viewModels.getSettings().signOut(connection)
    }
    val recordsToday = navigation.uiState.value?.today ?: today
    val recordsSelectedDate = navigation.uiState.value?.selectedRecordsDate ?: today
    val recordsActions = object : RecordsScreenActions {
        override fun selectDate(date: String) {
            val scope = AccountScope(ownerId)
            viewModels.getRecords().rememberSelectedDate(scope, date)
            navigation.selectRecordsDate(date)
        }
        override fun previousMonth() {
            val targetDate = viewModels.getRecords().previousMonth(
                AccountScope(ownerId), recordsToday, recordsSelectedDate
            )
            navigation.selectRecordsDate(targetDate)
        }
        override fun nextMonth() {
            val targetDate = viewModels.getRecords().nextMonth(
                AccountScope(ownerId), recordsToday, recordsSelectedDate
            )
            navigation.selectRecordsDate(targetDate)
        }
        override fun today() {
            val scope = AccountScope(ownerId)
            viewModels.getRecords().rememberSelectedDate(scope, recordsToday)
            viewModels.getRecords().showToday(scope, recordsToday)
            navigation.selectRecordsDate(recordsToday)
        }
        override fun openRecord(recordId: String) = viewModels.getWorkoutSession()
            .openRecord(AccountScope(ownerId), recordId)
        override fun deleteRecord(recordId: String) = viewModels.getWorkoutSession()
            .openDeleteConfirmation(AccountScope(ownerId), recordId)
        override fun showBodyMetric(date: String, recordId: String?) =
            viewModels.getBodyMetrics().open(AccountScope(ownerId), date, recordId)
        override fun addMeal(date: String) = navigation.openMealForDate(date)
    }
    val supplementActions = object : SupplementScreenActions {
        override fun back() { navigation.back() }
        override fun selectDate(date: String) =
            viewModels.getSupplement().selectDate(AccountScope(ownerId), today, date)
        override fun record(date: String, scheduleId: String, status: String) =
            viewModels.getSupplement().record(AccountScope(ownerId), today, date, scheduleId, status)
        override fun undo(date: String, scheduleId: String) =
            viewModels.getSupplement().undo(AccountScope(ownerId), today, date, scheduleId)
        override fun savePlan(existing: SupplementPlan?, draft: SupplementPlanDraft) =
            viewModels.getSupplement().savePlan(AccountScope(ownerId), today, existing, draft)
        override fun archive(itemId: String) =
            viewModels.getSupplement().archive(AccountScope(ownerId), today, itemId)
        override fun updateHistory(recordId: String, status: String) =
            viewModels.getSupplement().updateHistory(AccountScope(ownerId), today, recordId, status)
        override fun deleteHistory(recordId: String) =
            viewModels.getSupplement().deleteHistory(AccountScope(ownerId), today, recordId)
        override fun saveEffect(itemId: String, score: Int, adverseEffects: String, note: String) =
            viewModels.getSupplement().saveEffectCheckin(
                AccountScope(ownerId), today, itemId, score, adverseEffects, note
            )
    }
    val routineActions = object : RoutineDetailActions {
        override fun back() { navigation.back() }
        override fun openExerciseHistory(exercise: com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance) =
            viewModels.getRoutineEntry().openExerciseHistory(AccountScope(ownerId), exercise)
        override fun closeExerciseHistory() = viewModels.getRoutineEntry().closeExerciseHistory()
        override fun rename(routineId: String, name: String) =
            viewModels.getRoutineEntry().renameRoutine(AccountScope(ownerId), routineId, name)
        override fun copy(routineId: String, name: String) =
            viewModels.getRoutineEntry().copyRoutine(AccountScope(ownerId), routineId, name)
        override fun delete(routineId: String) =
            viewModels.getRoutineEntry().deleteRoutine(AccountScope(ownerId), routineId)
        override fun navigate(screen: FitnessScreen) = navigation.navigate(screen)
        override fun startWorkout(
            routineId: String,
            title: String,
            exercises: List<com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance>
        ) = viewModels.getWorkoutSession().startRoutine(
            AccountScope(ownerId), today, title, routineId, exercises
        )
    }
    val exercisePickerActions = object : ExercisePickerScreenActions {
        override fun back() {
            viewModels.getExercisePicker().clearReplacementExercise()
            navigation.back()
        }
        override fun search(query: String) = viewModels.getExercisePicker().search(query)
        override fun setBodyPart(bodyPart: com.yeonsik.fitness.shared.feature.exercise.model.BodyPart?) =
            viewModels.getExercisePicker().setBodyPart(bodyPart)
        override fun setPrimarySubPart(primarySubPart: String?) =
            viewModels.getExercisePicker().setPrimarySubPart(primarySubPart)
        override fun selectMuscleGroup(groupId: String) =
            viewModels.getExercisePicker().selectMuscleGroup(groupId)
        override fun clearBodyPartSelection() = viewModels.getExercisePicker().clearBodyPartSelection()
        override fun setEquipmentCategory(category: com.yeonsik.fitnessapp.exercise.UiEquipmentCategory?) =
            viewModels.getExercisePicker().setEquipmentCategory(category)
        override fun setSortOrder(order: com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker.SortOrder) =
            viewModels.getExercisePicker().setSortOrder(order)
        override fun resetFilters() = viewModels.getExercisePicker().resetFilters()
        override fun selectFamily(familyId: String) =
            viewModels.getExercisePicker().selectFamily(familyId)
        override fun selectPreset(familyId: String, presetId: String) =
            viewModels.getExercisePicker().selectPreset(familyId, presetId)
        override fun choose(preset: com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset) =
            viewModels.getExercisePicker().choose(preset)
        override fun chooseManual(exercise: com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise) =
            viewModels.getExercisePicker().chooseManual(exercise)
        override fun removePendingPreset(presetId: String) =
            viewModels.getExercisePicker().removePendingPreset(presetId)
        override fun confirmPendingSelection() = viewModels.getExercisePicker().confirmPendingSelection()
    }
    Column(
        Modifier.fillMaxWidth().then(
            if (isExercisePickerDestination(screen)) Modifier.fillMaxHeight() else Modifier
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)
    ) {
        when (screen) {
            FitnessScreen.HOME -> error("HOME is rendered by AppRoot")
            FitnessScreen.WORKOUT -> WorkoutOverview(
                homeState,
                ownerId,
                today,
                unit,
                object : WorkoutOverviewActions {
                    override fun continueWorkout() = viewModels.getWorkoutSession()
                        .continueIfAvailable(AccountScope(ownerId))
                    override fun navigate(screen: FitnessScreen) = navigation.navigate(screen)
                    override fun showBodyMetric() = viewModels.getBodyMetrics().open(
                        AccountScope(ownerId), today, null
                    )
                    override fun openMeals() {
                        navigation.selectMealDate(today)
                        navigation.navigate(FitnessScreen.MEALS)
                    }
                },
                entranceState, entranceToken, isActualActive,
                inProgressIsCardio = inProgressIsCardio
            )
            FitnessScreen.STRENGTH -> StrengthScreen(
                homeState,
                routineState,
                ownerId,
                object : StrengthActions {
                    override fun createRoutine(name: String) {
                        viewModels.getRoutineEntry().createRoutine(AccountScope(ownerId), name)
                    }

                    override fun startEmptyWorkout() = viewModels.getWorkoutSession()
                        .startEmpty(AccountScope(ownerId), today)
                    override fun showPastWorkout() = viewModels.getWorkoutSession()
                        .openManualPastEditor(AccountScope(ownerId))
                    override fun selectRoutine(routineId: String) = navigation.selectRoutine(routineId)
                    override fun navigate(screen: FitnessScreen) {
                        if (screen == FitnessScreen.WORKOUT) navigation.returnToWorkoutSelection()
                        else navigation.navigate(screen)
                    }
                    override fun startRoutineWorkout(
                        routineId: String?,
                        title: String,
                        exercises: List<com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance>
                    ) = viewModels.getWorkoutSession().startRoutine(
                        AccountScope(ownerId), today, title, routineId, exercises
                    )
                }
            )
            FitnessScreen.CARDIO -> CardioStartContent(
                state = cardioStartState.takeIf { it.ownerId == ownerId } ?: CardioStartUiState(),
                onSearch = viewModels.getCardioStart()::search,
                onSelectActivity = viewModels.getCardioStart()::selectActivity,
                onSelectEnvironment = viewModels.getCardioStart()::selectEnvironment,
                onStart = { type, environment -> viewModels.getCardioSession()
                    .prepareStart(AccountScope(ownerId), type, today, environment) },
                onBack = navigation::returnToWorkoutSelection,
                starting = cardioStarting
            )
            FitnessScreen.RECORDS -> RecordsScreen(
                recordsState,
                ownerId,
                navigation.uiState.value?.today ?: today,
                unit,
                navigation.uiState.value?.selectedRecordsDate ?: today,
                recordsActions,
                entranceState, entranceToken, isActualActive
            )
            FitnessScreen.STATISTICS -> StatisticsScreen(
                statisticsState,
                ownerId,
                unit,
                object : StatisticsScreenActions {
                    override fun selectPeriod(period: com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod) =
                        viewModels.getStatistics().selectPeriod(AccountScope(ownerId), today, period)
                }
            )
            FitnessScreen.DEVELOPMENT -> DevelopmentScreen(
                developmentState,
                paperAdviceState,
                ownerId,
                unit,
                object : DevelopmentScreenActions {
                    override fun showBodyProfile() = viewModels.getDevelopment().openProfileEditor(
                        AccountScope(ownerId), today
                    )
                    override fun showGoal() = viewModels.getDevelopment().openGoalEditor(
                        AccountScope(ownerId)
                    )
                    override fun showNutritionGoal() = viewModels.getDevelopment()
                        .openNutritionGoalEditor(AccountScope(ownerId))
                    override fun showRecoveryCheckIn() = viewModels.getDevelopment()
                        .openCheckInEditor(AccountScope(ownerId))
                    override fun refresh() = viewModels.getDevelopment().enter(AccountScope(ownerId), today)
                    override fun followExercises(keys: List<String>) = viewModels.getDevelopment()
                        .followExercises(AccountScope(ownerId), keys)
                    override fun openMeals() {
                        navigation.selectMealDate(today)
                        navigation.navigate(FitnessScreen.MEALS)
                    }
                    override fun requestPaperAdvice() = viewModels.getDevelopment()
                        .requestPaperAdvice(AccountScope(ownerId), today)
                    override fun startSuggestedWorkout(proposal: com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetCountProposal) {
                        val snapshot = (homeState as? HomeUiState.Ready)?.snapshot?.takeIf { it.ownerId == ownerId }
                        val routine = snapshot?.routines?.find { it.id == proposal.routineId }
                        val exercises = snapshot?.routineExercises?.get(proposal.routineId)
                        if (routine == null || exercises.isNullOrEmpty()) {
                            host.toast("현재 루틴 목록에서 운동할 루틴을 선택해 주세요.")
                            navigation.navigate(FitnessScreen.STRENGTH)
                            return
                        }
                        navigation.selectRoutine(routine.id)
                        viewModels.getWorkoutSession().startRoutineWithSetProposal(
                            AccountScope(ownerId), today, routine.name, routine.id, exercises, proposal
                        )
                    }
                    override fun openExerciseRecord(recordId: String, exerciseOccurrenceId: String) =
                        viewModels.getWorkoutSession().openCompletedExercise(AccountScope(ownerId), recordId, exerciseOccurrenceId)
                    override fun openRoutine(routineId: String?) {
                        val snapshot = (homeState as? HomeUiState.Ready)?.snapshot?.takeIf { it.ownerId == ownerId }
                        if (routineId != null && snapshot?.routines?.any { it.id == routineId } == true) {
                            navigation.selectRoutine(routineId)
                            navigation.navigate(FitnessScreen.ROUTINE_DETAIL)
                        } else {
                            if (routineId != null) host.toast("현재 루틴 목록에서 운동할 루틴을 선택해 주세요.")
                            navigation.navigate(FitnessScreen.STRENGTH)
                        }
                    }
                    override fun openInsightAction(insight: com.yeonsik.fitnessapp.development.DevelopmentInsight) {
                        when {
                            insight.category == "planning" ->
                                viewModels.getDevelopment().openGoalEditor(AccountScope(ownerId))
                            insight.category == "consistency" || insight.category == "focus" ->
                                navigation.navigate(FitnessScreen.WORKOUT)
                            insight.category == "recovery" || insight.category == "nutrition_logging" ->
                                navigation.selectMealDate(today).also {
                                    navigation.navigate(FitnessScreen.MEALS)
                                }
                            insight.category == "coverage" && insight.title.contains("체중") ->
                                viewModels.getDevelopment().openProfileEditor(
                                    AccountScope(ownerId), today
                                )
                            insight.category == "coverage" ->
                                navigation.navigate(FitnessScreen.RECORDS)
                            else -> host.toast("연결된 다음 행동이 아직 없습니다.")
                        }
                    }
                },
                startingWorkout = routineStarting
            )
            FitnessScreen.SETTINGS -> settingsState?.let {
                SettingsScreen(it, settingsActions)
            } ?: Text("설정을 불러오는 중입니다.")
            FitnessScreen.WORKOUT_SESSION -> WorkoutSessionScreen(
                workoutState,
                ownerId,
                unit,
                onSaveCondition = { recordId, note, onResult ->
                    viewModels.getWorkoutSession().updateConditionNote(AccountScope(ownerId), recordId, note,
                        java.util.function.Consumer { onResult(it) })
                },
                onExercise = { exerciseId ->
                    viewModels.getWorkoutExerciseDetail().rememberActiveExercise(exerciseId)
                    navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                }
            )
            FitnessScreen.WORKOUT_EXERCISE_DETAIL -> WorkoutDetailScreen(
                workoutDetailState,
                ownerId,
                unit,
                remember(ownerId, activeRecordId, workoutReadOnly, viewModels, navigation, host) {
                    object : WorkoutDetailActions {
                        override fun back() {
                            if (!navigation.back()) navigation.replace(FitnessScreen.STRENGTH)
                        }
                        override fun refresh() {
                            activeRecordId?.let { recordId ->
                                viewModels.getWorkoutSession().enter(AccountScope(ownerId), recordId)
                                viewModels.getWorkoutExerciseDetail().enter(
                                    AccountScope(ownerId),
                                    recordId,
                                    viewModels.getWorkoutExerciseDetail().activeExerciseId(),
                                    readOnly = workoutReadOnly
                                )
                            }
                        }
                        override fun openExercise(exerciseId: String) {
                            viewModels.getWorkoutExerciseDetail().rememberActiveExercise(exerciseId)
                            activeRecordId?.let { recordId ->
                                viewModels.getWorkoutExerciseDetail().enter(
                                    AccountScope(ownerId), recordId, exerciseId, readOnly = workoutReadOnly)
                            }
                            navigation.replace(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                        }
                        override fun linkManualExercise(exerciseId: String) {
                            viewModels.getExercisePicker().rememberManualLinkExercise(exerciseId)
                            navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_ADD)
                        }
                        override fun deleteExercise(
                            recordId: String,
                            exerciseId: String,
                            onResult: (Boolean) -> Unit
                        ) {
                            viewModels.getWorkoutExerciseDetail().deleteExercise(
                                AccountScope(ownerId), recordId, exerciseId,
                                java.util.function.Consumer { result -> onResult(result) }
                            )
                        }

                        override fun addSet(
                            recordId: String,
                            exerciseId: String,
                            setIndex: Int,
                            input: WorkoutSetInput,
                            onResult: (Boolean) -> Unit
                        ) {
                            viewModels.getWorkoutExerciseDetail().addTypedSet(
                                AccountScope(ownerId), recordId, exerciseId, setIndex, input,
                                java.util.function.Consumer { result -> onResult(result) }
                            )
                        }

                        override fun updateSet(
                            recordId: String,
                            setId: String,
                            input: WorkoutSetInput,
                            onResult: (Boolean) -> Unit
                        ) {
                            viewModels.getWorkoutExerciseDetail().updateTypedSet(
                                AccountScope(ownerId), recordId, setId, input,
                                java.util.function.Consumer { result -> onResult(result) }
                            )
                        }

                        override fun deleteSet(
                            recordId: String,
                            setId: String,
                            onResult: (Boolean) -> Unit
                        ) {
                            viewModels.getWorkoutExerciseDetail().deleteSet(
                                AccountScope(ownerId), recordId, setId,
                                java.util.function.Consumer { result -> onResult(result) }
                            )
                        }

                        override fun updateSetType(recordId: String, setId: String,
                            type: com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetType, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().updateSetType(AccountScope(ownerId), recordId, setId, type,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun addDropSet(recordId: String, exerciseId: String, parentSetId: String, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().addDropSet(AccountScope(ownerId), recordId, exerciseId, parentSetId,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun linkSuperset(recordId: String, exerciseId: String, partnerExerciseId: String?, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().linkSuperset(AccountScope(ownerId), recordId, exerciseId, partnerExerciseId,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun linkExerciseGroup(recordId: String, exerciseId: String, partnerExerciseId: String?,
                            type: com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseGroupType, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().linkExerciseGroup(AccountScope(ownerId), recordId, exerciseId, partnerExerciseId, type,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun updateSetupNote(recordId: String, exerciseId: String, note: String, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().updateSetupNote(AccountScope(ownerId), recordId, exerciseId, note,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun updateConditionNote(recordId: String, note: String, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().updateConditionNote(AccountScope(ownerId), recordId, note,
                                java.util.function.Consumer { onResult(it) })
                        }
                        override fun startRestTimer(restSeconds: Int?) =
                            viewModels.getWorkoutSession().startRestTimer(ownerId, restSeconds)
                        override fun updateExerciseRestSeconds(recordId: String, exerciseId: String,
                                                              seconds: Int, onResult: (Boolean) -> Unit) {
                            viewModels.getWorkoutExerciseDetail().updateExerciseRestSeconds(
                                AccountScope(ownerId), recordId, exerciseId, seconds,
                                java.util.function.Consumer { onResult(it) }
                            )
                        }
                        override fun toast(message: String) = host.toast(message)
                    }
                },
                previewExercise = (workoutState as? WorkoutSessionUiState.Ready)
                    ?.takeIf { it.ownerId == ownerId && it.session.recordId == activeRecordId }
                    ?.session?.exercises?.firstOrNull {
                        it.id == viewModels.getWorkoutExerciseDetail().activeExerciseId()
                    },
                previewRecordId = activeRecordId
            )
            FitnessScreen.WORKOUT_SUMMARY -> WorkoutSummaryScreen(
                workoutState,
                ownerId,
                unit,
                { navigation.back() },
                { exerciseId ->
                    viewModels.getWorkoutExerciseDetail().rememberActiveExercise(exerciseId)
                    navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                },
                onRecords = { navigation.navigate(FitnessScreen.RECORDS) },
                onSaveAsRoutine = { name, exercises ->
                    viewModels.getRoutineEntry().saveWorkoutAsRoutine(
                        AccountScope(ownerId),
                        name,
                        exercises.map { exercise ->
                            RoutineExerciseDraft(
                                exerciseId = exercise.exerciseId,
                                nameKo = exercise.name,
                                nameEn = exercise.familyIdentity?.presetNameEn.orEmpty(),
                                bodyPartId = exercise.familyIdentity?.defaultUiPart
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() },
                                equipmentVariantId = null,
                                primarySubPart = exercise.primarySubPart
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() },
                                recordType = exercise.recordType,
                                familyIdentity = exercise.familyIdentity
                            )
                        }
                    )
                }
            )
            FitnessScreen.CARDIO_SESSION -> CardioSessionScreen(cardioState, ownerId, cardioActions)
            FitnessScreen.CARDIO_SUMMARY -> CardioSummaryScreen(
                cardioState,
                cardioRouteState,
                ownerId,
                cardioActions
            )
            FitnessScreen.MEALS -> MealScreen(
                homeState,
                mealState,
                mealPriceTraceState,
                mealNutritionPublicationState,
                ownerId,
                today,
                mealActions,
                nutritionEditorState = nutritionEditorState,
                nutritionEditorActions = viewModels.getMeal().nutritionEditor,
                onUseComposition = viewModels.getMeal()::useComposition
            )
            FitnessScreen.SUPPLEMENTS -> SupplementScreen(
                supplementState,
                ownerId,
                today,
                supplementActions
            )
            FitnessScreen.ROUTINE_DETAIL -> RoutineDetailScreen(
                homeState,
                ownerId,
                navigation.selectedRoutineId(),
                routineActions,
                historyState = viewModels.getRoutineEntry().exerciseHistoryState.observeAsState().value,
                unit = unit,
                starting = routineStarting,
                inProgressSessionId = (workoutState as? WorkoutSessionUiState.Ready)
                    ?.takeIf { it.ownerId == ownerId && it.session.status == "in_progress" }?.session?.recordId
            )
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD -> ExercisePickerScreen(
                exercisePickerState,
                ownerId,
                screen,
                exercisePickerActions
            )
        }
    }
}
