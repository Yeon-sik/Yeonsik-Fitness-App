package com.yeonsik.fitnessapp.app.navigation

import com.yeonsik.fitness.shared.feature.body.model.BodyProfile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.AppUiActions
import com.yeonsik.fitnessapp.feature.body.ui.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.records.ui.*
import com.yeonsik.fitnessapp.feature.statistics.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.settings.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.supplement.SupplementPlan
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseDraft
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val unit = settingsState?.preferredMassUnit
        ?: viewModels.getSettings().preferredMassUnit()
    val themeMode = settingsState?.themeMode
        ?: viewModels.getSettings().themeMode()
    val dark = themeMode == "dark" ||
        (themeMode == "system" &&
            androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)
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
    val topLevelSwipeEnabled = navigation.canSwipeTopLevel()
    val topLevelSwipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
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
    LaunchedEffect(homeEntryKey) {
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

    LaunchedEffect(workoutAction, workoutTerminalEvent, bodyEditorState, mealState) {
        val recordsChanged = workoutAction != null || workoutTerminalEvent != null ||
            bodyEditorState is BodyMetricsEditorUiState.Saved ||
            bodyEditorState is BodyMetricsEditorUiState.Deleted ||
            (mealState as? MealUiState.Ready)?.notice != null
        if (recordsChanged) {
            viewModels.getRecords().markStale()
            viewModels.getStatistics().markStale()
            viewModels.getDevelopment().markStale()
            if ((mealState as? MealUiState.Ready)?.notice != null) {
                viewModels.getHome().markStale()
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
            if (state.selectionMode == com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerSelectionMode.WORKOUT_REPLACE) {
                viewModels.getExercisePicker().clearReplacementExercise()
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
                val recordId = event.recordId
                if (recordId == null) {
                    host.toast("운동 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                viewModels.getWorkoutSession().rememberActiveRecord(recordId)
                event.message?.let(host::toast)
                if (event.action == WorkoutSessionAction.OPEN_RECORD && event.cardioSession) {
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
                viewModels.getWorkoutSession().rememberActiveRecord(recordId)
                when (event.action) {
                    WorkoutSessionAction.START_ROUTINE -> host.toast("루틴 운동을 시작했습니다.")
                    WorkoutSessionAction.START_MANUAL_PAST ->
                        host.toast("세트와 횟수를 입력한 뒤 운동 완료를 누르세요.")
                    else -> Unit
                }
                viewModels.getWorkoutSession().dismissManualPastEditor()
                openWorkoutSession(navigation, viewModels, recordId)
            }
            WorkoutSessionActionOutcome.DELETED -> {
                event.recordId?.let { clearActiveWorkout(viewModels, it) }
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
                            host.requestCardioStart(activityType)
                        }
                    }
                    CardioSessionActionOutcome.EXISTING_WORKOUT -> {
                        val existingId = event.recordId
                        if (existingId == null) {
                            host.toast("진행 중인 운동을 찾지 못했습니다.")
                        } else if (event.existingCardioSession && event.session != null) {
                            cardioViewModel.rememberActiveRecord(existingId)
                            event.message?.let(host::toast)
                            if (event.session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_TRACKING) {
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
                    if (session.status == com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot.STATUS_TRACKING) {
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
                host.startCardioTracking(recordId)
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
                host.requestCardioResume(activityType, recordId)
            }
            CardioSessionAction.RESUME -> {
                if (recordId == null) {
                    host.toast("재개할 유산소 기록을 찾지 못했습니다.")
                    return@LaunchedEffect
                }
                cardioViewModel.rememberActiveRecord(recordId)
                host.resumeCardioTracking(recordId)
                host.toast("GPS 기록을 재개했습니다.")
                navigation.navigate(FitnessScreen.CARDIO_SESSION)
            }
            CardioSessionAction.PAUSE -> {
                if (recordId == null) return@LaunchedEffect
                host.pauseCardioTracking(recordId)
                host.toast("GPS 기록을 일시정지했습니다.")
                cardioViewModel.refresh(AccountScope(ownerId), recordId)
            }
            CardioSessionAction.PREPARE_FINISH -> {
                if (event.outcome == CardioSessionActionOutcome.COMPLETED) {
                    if (recordId != null) cardioViewModel.rememberActiveRecord(recordId)
                    navigation.navigate(FitnessScreen.CARDIO_SUMMARY)
                } else if (recordId != null && session != null) {
                    if (event.pausedByFinish) host.pauseCardioTracking(recordId)
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
                viewModels.getHome().enter(AccountScope(ownerId), routeDate)
                viewModels.getDevelopment().enter(AccountScope(ownerId), navigationState.today)
                viewModels.getBodyMetrics().dismissEditor()
            }
            is BodyMetricsEditorUiState.Deleted -> {
                host.toast("체중 기록을 삭제했습니다.")
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
                        "회복 체크인을 저장했습니다."
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

    FitnessComposeTheme(dark) {
        val bottomNavigationVisible = isBottomNavigationVisible(screen)
        val bottomNavigationHeight = with(LocalDensity.current) {
            // The icon and scaled label must both fit inside the capsule.
            36.dp + MaterialTheme.typography.labelMedium.lineHeight.toDp()
        }.coerceAtLeast(56.dp)
        val bottomNavigationSpace = bottomNavigationHeight + 24.dp
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (screen == FitnessScreen.WORKOUT_SESSION) {
                    SessionTopBar(navigation, viewModels, ownerId, workoutState)
                }
                if (screen == FitnessScreen.RECORDS) {
                    RecordsHubTabs(
                        selected = recordsHubTab,
                        onSelected = navigation::selectRecordsHubTab
                    )
                }
                destinationStateHolder.SaveableStateProvider(
                    destinationScrollStateKey(screen) +
                        if (screen == FitnessScreen.RECORDS) ":${recordsHubTab.name}" else ""
                ) {
                    val contentScrollState = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (isExercisePickerDestination(screen)) {
                                    Modifier
                                } else {
                                    Modifier.verticalScroll(contentScrollState)
                                }
                            )
                            .then(
                                if (topLevelSwipeEnabled) {
                                    Modifier.pointerInput(screen, topLevelSwipeThreshold) {
                                        var totalDrag = 0f
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                totalDrag += dragAmount
                                            },
                                            onDragEnd = {
                                                when {
                                                    totalDrag <= -topLevelSwipeThreshold ->
                                                        navigation.swipeTopLevel(forward = true)
                                                    totalDrag >= topLevelSwipeThreshold ->
                                                        navigation.swipeTopLevel(forward = false)
                                                }
                                                totalDrag = 0f
                                            },
                                            onDragCancel = { totalDrag = 0f }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(
                                start = FitnessSpacing.page,
                                end = FitnessSpacing.page,
                                top = FitnessSpacing.gap,
                                bottom = FitnessSpacing.gap +
                                    if (bottomNavigationVisible) bottomNavigationSpace else 0.dp
                            )
                    ) {
                        if (destinationScreen == FitnessScreen.HOME) {
                            HomeDestination(homeState, ownerId, routeDate, homeActions)
                        } else {
                            AppDestination(
                                host,
                                viewModels,
                                navigation,
                                destinationScreen,
                                ownerId,
                                routeDate,
                                unit
                            )
                        }
                    }
                }
                RestTimerBar(host, viewModels, screen, restState, ownerId)
            }
            if (bottomNavigationVisible) {
                BottomNavigation(
                    navigation, homeState, screen, dark, bottomNavigationHeight,
                    Modifier.align(Alignment.BottomCenter)
                )
            }
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
            AlertDialog(
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

private fun currentWorkoutRecordId(
    screen: FitnessScreen,
    viewModels: AppViewModels,
    homeState: HomeUiState
): String? = when (screen) {
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
    state: WorkoutSessionUiState
) {
    val recordId = viewModels.getWorkoutSession().activeRecordId()
    val canEdit = (state as? WorkoutSessionUiState.Ready)?.let { ready -> ready.ownerId == ownerId && ready.session.status != "completed" } == true
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                if (!navigation.back()) navigation.replace(FitnessScreen.STRENGTH)
            }
        ) { Text("←", style = MaterialTheme.typography.headlineSmall) }
        Text("운동 세션", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
private fun RestTimerBar(
    host: AppUiActions,
    viewModels: AppViewModels,
    screen: FitnessScreen,
    state: WorkoutRestTimerState,
    ownerId: String
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
            if (now >= active.endsAtMillis) {
                viewModels.getWorkoutSession().stopRestTimer()
                host.toast("휴식 종료. 다음 세트를 시작하세요.")
                break
            }
            delay(250)
        }
    }
    if (!visible || active == null) return
    val remainingMillis = (active.endsAtMillis - now).coerceAtLeast(0L)
    val remainingSeconds = ((remainingMillis + 999L) / 1000L).toInt()
    val ratio = (remainingMillis / (active.totalSeconds * 1000f)).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("휴식", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.ROOT, "%d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                style = MaterialTheme.typography.headlineSmall
            )
            TextButton(onClick = { viewModels.getWorkoutSession().stopRestTimer() }) {
                Text("건너뛰기")
            }
        }
        LinearProgressIndicator(progress = ratio, modifier = Modifier.fillMaxWidth())
    }
}

private data class NavigationItem(val label: String, val screen: FitnessScreen)

/**
 * Translucent glass treatment: the destination scrolls beneath the capsule while a milked
 * surface and a narrow light-catching rim keep its controls legible. This is alpha compositing,
 * not a backdrop blur; text and icons are drawn sharply above the surface.
 */
private fun Modifier.frostedGlassSlab(dark: Boolean): Modifier =
    shadow(
        elevation = 16.dp,
        shape = CircleShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = if (dark) 0.20f else 0.08f),
        spotColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.10f)
    )
        .clip(CircleShape)
        .drawWithCache {
            val radius = size.height / 2f
            val cornerRadius = CornerRadius(radius, radius)
            val inset = 0.65.dp.toPx()
            val surface = Brush.verticalGradient(
                colors = if (dark) {
                    listOf(
                        Color(0xFF30343A).copy(alpha = 0.94f),
                        Color(0xFF25292F).copy(alpha = 0.94f),
                        Color(0xFF20242A).copy(alpha = 0.90f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.94f),
                        Color(0xFFFCFDFE).copy(alpha = 0.84f),
                        Color(0xFFF3F5F7).copy(alpha = 0.90f)
                    )
                }
            )
            val sheen = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.04f else 0.24f),
                    Color.Transparent,
                    Color.White.copy(alpha = if (dark) 0.025f else 0.06f)
                ),
                start = Offset.Zero,
                end = Offset(size.width * 0.8f, size.height)
            )
            val rim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.24f else 0.92f),
                    Color.White.copy(alpha = if (dark) 0.09f else 0.46f),
                    Color.White.copy(alpha = if (dark) 0.15f else 0.72f)
                )
            )
            onDrawWithContent {
                drawRoundRect(brush = surface, cornerRadius = cornerRadius)
                drawRoundRect(brush = sheen, cornerRadius = cornerRadius)
                drawContent()
                drawRoundRect(
                    brush = rim,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(radius - inset, radius - inset),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

private fun Modifier.glassSelection(dark: Boolean, accent: Color): Modifier =
    clip(CircleShape)
        .background(
            Brush.verticalGradient(
                colors = if (dark) {
                    listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.13f))
                } else {
                    listOf(accent.copy(alpha = 0.44f), accent.copy(alpha = 0.28f))
                }
            )
        )

@Composable
private fun Modifier.glassTabFeedback(
    dark: Boolean,
    interactionSource: MutableInteractionSource
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val opacity by animateFloatAsState(
        targetValue = when {
            pressed -> if (dark) 0.08f else 0.10f
            focused -> if (dark) 0.07f else 0.09f
            hovered -> 0.05f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (pressed) 80 else 160)
    )
    val tint = if (dark) Color.White else Color(0xFF28313A)
    // Round only the feedback layer. Clipping the full tab cuts off large Korean labels.
    return drawWithCache {
        val radius = size.minDimension / 2f
        onDrawBehind {
            drawRoundRect(
                color = tint.copy(alpha = opacity),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun RecordsHubTabs(
    selected: RecordsHubTab,
    onSelected: (RecordsHubTab) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FitnessSpacing.page, vertical = FitnessSpacing.small)
            .clip(FitnessShape.input)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(FitnessSpacing.micro),
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        RecordsHubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = FitnessSpacing.touch)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelected(tab) }
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
    navigation: AppNavigationViewModel,
    homeState: HomeUiState,
    screen: FitnessScreen,
    dark: Boolean,
    itemHeight: Dp,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationItem("메인", FitnessScreen.HOME),
        NavigationItem("피트니스", FitnessScreen.WORKOUT),
        NavigationItem("기록", FitnessScreen.RECORDS),
        NavigationItem("설정", FitnessScreen.SETTINGS)
    )
    val active = navigationRoot(screen)
    val workoutInProgress = (homeState as? HomeUiState.Ready)?.snapshot?.inProgressSessionId != null
    val selectedIndex = items.indexOfFirst { it.screen == active }.coerceAtLeast(0)
    val selectedContent = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val unselectedContent = if (dark) Color(0xFFCCD2D8) else Color(0xFF555F69)
    val progressTint = LocalFitnessColors.current.action
    // Give the longer Korean label more room at large accessibility font sizes.
    val workoutTabWeight = if (LocalDensity.current.fontScale > 1.5f) 1.6f else 1f
    val tabWeights = items.map { if (it.screen == FitnessScreen.WORKOUT) workoutTabWeight else 1f }

    Box(
        modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .frostedGlassSlab(dark)
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
                    .glassSelection(dark, progressTint)
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
                            .glassTabFeedback(dark, interactionSource)
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { navigation.selectTopLevel(item.screen) }
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
                                            if (dark) Color(0xFF343C43) else Color.White,
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
    unit: MassUnit
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
    val supplementState by viewModels.getSupplement().uiState
        .observeAsState(SupplementUiState.Idle)
    val exercisePickerState by viewModels.getExercisePicker().uiState
        .observeAsState(ExercisePickerUiState.Idle)
    val settingsState by viewModels.getSettings().uiState.observeAsState()
    val activeRecordId = currentWorkoutRecordId(screen, viewModels, homeState)
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

    LaunchedEffect(screen, ownerId, activeRecordId, workoutReadOnly) {
        when (screen) {
            FitnessScreen.WORKOUT_SESSION,
            FitnessScreen.WORKOUT_SUMMARY ->
                viewModels.getWorkoutSession().enter(
                    AccountScope(ownerId), activeRecordId
                )
            FitnessScreen.WORKOUT_EXERCISE_DETAIL ->
                activeRecordId?.let { recordId ->
                    viewModels.getWorkoutExerciseDetail().enter(
                        AccountScope(ownerId),
                        recordId,
                        viewModels.getWorkoutExerciseDetail().activeExerciseId(),
                        readOnly = workoutReadOnly
                    )
                }
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
                        com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerSelectionMode
                            .forTarget(screen, replacementId)
                    )
                }
            else -> Unit
        }
    }
    val mealNotice = (mealState as? MealUiState.Ready)?.notice
    LaunchedEffect(screen, ownerId, today, mealNotice) {
        if (screen == FitnessScreen.MEALS && mealNotice != null) {
            viewModels.getHome().enter(AccountScope(ownerId), today)
        }
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
        override fun cancel() {
            cardioRecordId?.let {
                viewModels.getCardioSession().prepareCancel(AccountScope(ownerId), it)
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
        override fun searchFood(query: String) = viewModels.getMeal().search(query)
        override fun selectFood(food: com.yeonsik.fitnessapp.data.NutritionFood) {
            viewModels.getMeal().selectFood(food)
        }
        override fun useDiningOutFood(food: com.yeonsik.fitnessapp.data.NutritionFood) {
            viewModels.getMeal().useDiningOutFood(food)
        }
        override fun saveReusableDiningOutMenu() =
            viewModels.getMeal().saveReusableDiningOutMenu(AccountScope(ownerId)) { }
        override fun updateQuantity(value: String) = viewModels.getMeal().updateQuantity(value)
        override fun updateTime(value: String) = viewModels.getMeal().updateTime(value)
        override fun saveFood() = viewModels.getMeal().saveFood(AccountScope(ownerId)) { }
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
        override fun updatePriceTraceQuery(value: String) =
            viewModels.getMeal().updatePriceTraceQuery(value)
        override fun searchPriceTraceRestaurants() =
            viewModels.getMeal().searchPriceTraceRestaurants()
        override fun loadPriceTraceRestaurant(restaurantId: String) =
            viewModels.getMeal().loadPriceTraceRestaurant(restaurantId)
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
        override fun showBodyMetric() = viewModels.getBodyMetrics().open(
            AccountScope(ownerId), today, null
        )
        override fun editMeal(meal: com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary) =
            viewModels.getMeal().openRecordEditor(meal)
        override fun deleteMeal(recordId: String) =
            viewModels.getMeal().deleteMeal(AccountScope(ownerId), recordId)
        override fun saveMealTime(recordId: String, mealTime: String) =
            viewModels.getMeal().saveMealTime(AccountScope(ownerId), recordId, mealTime)
        override fun cancelMealEdit() = viewModels.getMeal().closeRecordEditor()
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
        override fun previousMonth() = viewModels.getRecords().previousMonth(
            AccountScope(ownerId), recordsToday, recordsSelectedDate
        )
        override fun nextMonth() = viewModels.getRecords().nextMonth(
            AccountScope(ownerId), recordsToday, recordsSelectedDate
        )
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
        override fun openMeals(date: String) =
            navigation.selectMealDate(date).also { navigation.navigate(FitnessScreen.MEALS) }
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
        override fun back() { navigation.back() }
        override fun search(query: String) = viewModels.getExercisePicker().search(query)
        override fun setBodyPart(bodyPart: com.yeonsik.fitness.shared.feature.exercise.model.BodyPart?) =
            viewModels.getExercisePicker().setBodyPart(bodyPart)
        override fun setPrimarySubPart(primarySubPart: String?) =
            viewModels.getExercisePicker().setPrimarySubPart(primarySubPart)
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
                }
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
                    override fun navigate(screen: FitnessScreen) = navigation.navigate(screen)
                    override fun startRoutineWorkout(
                        routineId: String?,
                        title: String,
                        exercises: List<com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance>
                    ) = viewModels.getWorkoutSession().startRoutine(
                        AccountScope(ownerId), today, title, routineId, exercises
                    )
                }
            )
            FitnessScreen.CARDIO -> CardioStartScreen(cardioActions)
            FitnessScreen.RECORDS -> RecordsScreen(
                recordsState,
                ownerId,
                navigation.uiState.value?.today ?: today,
                unit,
                navigation.uiState.value?.selectedRecordsDate ?: today,
                recordsActions
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
                }
            )
            FitnessScreen.SETTINGS -> settingsState?.let {
                SettingsScreen(it, settingsActions)
            } ?: Text("설정을 불러오는 중입니다.")
            FitnessScreen.WORKOUT_SESSION -> WorkoutSessionScreen(
                workoutState,
                ownerId,
                unit,
                { exerciseId ->
                    viewModels.getWorkoutExerciseDetail().rememberActiveExercise(exerciseId)
                    navigation.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                }
            )
            FitnessScreen.WORKOUT_EXERCISE_DETAIL -> WorkoutDetailScreen(
                workoutDetailState,
                ownerId,
                unit,
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
                        navigation.replace(FitnessScreen.WORKOUT_EXERCISE_DETAIL)
                    }
                    override fun replaceExercise(exerciseId: String) {
                        viewModels.getExercisePicker().rememberReplacementExercise(exerciseId)
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

                    override fun applyPreviousHistory(
                        recordId: String,
                        exerciseId: String,
                        currentSets: List<WorkoutSet>,
                        history: WorkoutExerciseHistory,
                        onResult: (Boolean) -> Unit
                    ) {
                        viewModels.getWorkoutExerciseDetail().applyPreviousHistory(
                            AccountScope(ownerId), recordId, exerciseId, currentSets, history,
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

                    override fun startRestTimer(restSeconds: Int?) =
                        viewModels.getWorkoutSession().startRestTimer(ownerId, restSeconds)
                    override fun toast(message: String) = host.toast(message)
                }
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
                ownerId,
                today,
                unit,
                mealActions
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
                routineActions
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
