package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.AppUiActions
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.records.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.settings.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
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

@Composable
private fun AppRoot(
    host: AppUiActions,
    navigation: AppNavigationViewModel,
    viewModels: AppViewModels
) {
    val navigationState by navigation.uiState.observeAsState(AppNavigationState())
    val settingsState by viewModels.getSettings().uiState.observeAsState()
    val homeState by viewModels.getHome().uiState.observeAsState(HomeUiState.Idle)
    val routineState by viewModels.getRoutineEntry().uiState
        .observeAsState(RoutineEntryUiState.Idle)
    val screen = navigationState.screen
    val ownerId = host.currentOwnerId()
    val unit = settingsState?.preferredMassUnit ?: host.preferredMassUnit()
    val themeMode = settingsState?.themeMode ?: host.themeMode()
    val dark = themeMode == "dark" ||
        (themeMode == "system" &&
            androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)
    val routeDate = when (screen) {
        FitnessScreen.MEALS -> navigationState.selectedMealDate
        FitnessScreen.RECORDS -> navigationState.selectedRecordsDate
        else -> navigationState.today
    }
    val restState by viewModels.getWorkoutSession().restTimerState
        .observeAsState(WorkoutRestTimerState.Inactive)
    val homeActions = object : HomeScreenActions {
        override fun continueWorkout() = host.continueWorkoutIfAvailable()
        override fun navigate(screen: FitnessScreen) = host.navigate(screen)
        override fun startEmptyWorkout() = host.startEmptyWorkout()
        override fun selectRoutine(routineId: String) = host.selectRoutine(routineId)
        override fun startRoutineWorkout(
            exercises: List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance>
        ) = host.startRoutineWorkout(exercises)
        override fun showBodyMetric() = host.showBodyMetricDialog()
        override fun openMealManagement(date: String, returnScreen: FitnessScreen) =
            host.openMealManagement(date, returnScreen)
    }

    FitnessComposeTheme(dark) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding()
        ) {
            if (screen == FitnessScreen.WORKOUT_SESSION) {
                SessionTopBar(host)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                .padding(horizontal = FitnessSpacing.page, vertical = FitnessSpacing.gap)
            ) {
                if (screen == FitnessScreen.HOME) {
                    HomeDestination(homeState, routineState, ownerId, routeDate, unit, homeActions)
                } else {
                    AppDestination(host, viewModels, navigation, screen, ownerId, routeDate, unit)
                }
            }
            RestTimerBar(host, viewModels, screen, restState)
            if (isBottomNavigationVisible(screen)) {
                BottomNavigation(host, screen)
            }
        }
    }
}

@Composable
private fun SessionTopBar(host: AppUiActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                if (!host.back()) host.replace(FitnessScreen.STRENGTH)
            }
        ) { Text("←", style = MaterialTheme.typography.headlineSmall) }
        Text("운동 세션", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RestTimerBar(
    host: AppUiActions,
    viewModels: AppViewModels,
    screen: FitnessScreen,
    state: WorkoutRestTimerState
) {
    val active = state as? WorkoutRestTimerState.Active
    val visible = active != null
        && host.currentOwnerId() == active.ownerId
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

@Composable
private fun BottomNavigation(host: AppUiActions, screen: FitnessScreen) {
    val items = listOf(
        NavigationItem("메인", FitnessScreen.HOME),
        NavigationItem("피트니스", FitnessScreen.WORKOUT),
        NavigationItem("기록", FitnessScreen.RECORDS),
        NavigationItem("발전", FitnessScreen.DEVELOPMENT),
        NavigationItem("설정", FitnessScreen.SETTINGS)
    )
    val active = navigationRoot(screen)
    val workoutInProgress = host.currentWorkoutRecordId() != null
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FitnessSpacing.small, vertical = FitnessSpacing.micro),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
        ) {
            items.forEach { item ->
                val selected = item.screen == active
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { host.replace(item.screen) }
                        .padding(vertical = FitnessSpacing.micro),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(24.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (item.screen == FitnessScreen.WORKOUT && workoutInProgress)
                                    MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    Text(
                        item.label,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun navigationRoot(screen: FitnessScreen): FitnessScreen = when (screen) {
    FitnessScreen.HOME -> FitnessScreen.HOME
    FitnessScreen.RECORDS -> FitnessScreen.RECORDS
    FitnessScreen.DEVELOPMENT -> FitnessScreen.DEVELOPMENT
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
    FitnessScreen.WORKOUT_SESSION,
    FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_SUMMARY,
    FitnessScreen.CARDIO_SESSION,
    FitnessScreen.CARDIO_SUMMARY -> false
    FitnessScreen.WORKOUT_EXERCISE_ADD -> true
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
    val mealState by viewModels.getMeal().uiState.observeAsState(MealUiState.Idle)
    val mealPriceTraceState by viewModels.getMeal().priceTraceState
        .observeAsState(PriceTraceUiState.Idle)
    val supplementState by viewModels.getSupplement().uiState
        .observeAsState(SupplementUiState.Idle)
    val exercisePickerState by viewModels.getExercisePicker().uiState
        .observeAsState(ExercisePickerUiState.Idle)
    val settingsState by viewModels.getSettings().uiState.observeAsState()
    LaunchedEffect(screen, ownerId, today) {
        when (screen) {
            FitnessScreen.HOME,
            FitnessScreen.STRENGTH -> {
                viewModels.getRoutineEntry().enter(AccountScope(ownerId))
                viewModels.getHome().enter(AccountScope(ownerId), today)
            }
            FitnessScreen.WORKOUT,
            FitnessScreen.RECORDS,
            FitnessScreen.ROUTINE_DETAIL ->
                viewModels.getHome().enter(AccountScope(ownerId), today)
            FitnessScreen.DEVELOPMENT ->
                viewModels.getDevelopment().enter(AccountScope(ownerId), today)
            FitnessScreen.MEALS -> {
                viewModels.getHome().enter(AccountScope(ownerId), today)
                viewModels.getMeal().enter(AccountScope(ownerId), today)
            }
            FitnessScreen.SETTINGS -> viewModels.getSettings().enter()
            FitnessScreen.SUPPLEMENTS ->
                viewModels.getSupplement().enter(AccountScope(ownerId), today)
            FitnessScreen.WORKOUT_SESSION,
            FitnessScreen.WORKOUT_SUMMARY ->
                viewModels.getWorkoutSession().enter(
                    AccountScope(ownerId), host.currentWorkoutRecordId()
                )
            FitnessScreen.WORKOUT_EXERCISE_DETAIL ->
                host.currentWorkoutRecordId()?.let { recordId ->
                    viewModels.getWorkoutExerciseDetail().enter(
                        AccountScope(ownerId), recordId, null
                    )
                }
            FitnessScreen.CARDIO_SESSION,
            FitnessScreen.CARDIO_SUMMARY ->
                viewModels.getCardioSession().enter(
                    AccountScope(ownerId), host.currentWorkoutRecordId()
                )
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD ->
                viewModels.getExercisePicker().enter(
                    AccountScope(ownerId), screen, host.currentWorkoutRecordId(), null, host.selectedRoutineId()
                )
            else -> Unit
        }
    }
    val mealNotice = (mealState as? MealUiState.Ready)?.notice
    LaunchedEffect(screen, ownerId, today, mealNotice) {
        if (screen == FitnessScreen.MEALS && mealNotice != null) {
            viewModels.getHome().enter(AccountScope(ownerId), today)
        }
    }
    val cardioActions = object : CardioScreenActions {
        override fun start(activityType: CardioActivityType) = host.startCardioWorkout(activityType)
        override fun back() { host.back() }
        override fun refresh() = host.refreshCardioSession()
        override fun pause() = host.pauseCardioWorkout()
        override fun resume() = host.resumeCardioWorkout()
        override fun editAverageHeartRate() = host.editCardioAverageHeartRate()
        override fun finish() = host.finishCardioWorkout()
        override fun cancel() = host.cancelCardioWorkout()
        override fun loadRoute(recordId: String) {
            viewModels.getCardioSession().loadRoute(AccountScope(ownerId), recordId)
        }
    }
    val mealActions = object : MealScreenActions {
        override fun back() { host.back() }
        override fun startDraft() = viewModels.getMeal().startDraft()
        override fun closeDraft() = viewModels.getMeal().closeDraft()
        override fun chooseFood() = viewModels.getMeal().chooseFood()
        override fun chooseDiningOut() = viewModels.getMeal().chooseDiningOut()
        override fun searchFood(query: String) = viewModels.getMeal().search(query)
        override fun selectFood(food: com.yeonsik.fitnessapp.data.NutritionFood) {
            viewModels.getMeal().selectFood(food)
        }
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
        override fun showBodyMetric() = host.showBodyMetricDialog(today, null)
    }
    val settingsActions = object : SettingsScreenActions {
        override fun setPreferredMassUnit(unit: MassUnit) = host.setPreferredMassUnit(unit)
        override fun setThemeMode(mode: String) = host.setThemeMode(mode)
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
    val recordsActions = object : RecordsScreenActions {
        override fun selectDate(date: String) = navigation.selectRecordsDate(date)
        override fun openRecord(recordId: String) = host.openRecord(recordId)
        override fun deleteRecord(recordId: String) = host.confirmDeleteSession(recordId)
        override fun showBodyMetric(date: String, recordId: String?) =
            host.showBodyMetricDialog(date, recordId)
        override fun openMeals(date: String) =
            host.openMealManagement(date, FitnessScreen.RECORDS)
    }
    val supplementActions = object : SupplementScreenActions {
        override fun back() { host.back() }
        override fun record(scheduleId: String, status: String) =
            viewModels.getSupplement().record(AccountScope(ownerId), today, scheduleId, status)
        override fun undo(scheduleId: String) =
            viewModels.getSupplement().undo(AccountScope(ownerId), today, scheduleId)
    }
    val routineActions = object : RoutineDetailActions {
        override fun back() { host.back() }
        override fun rename(routineId: String, name: String) =
            viewModels.getRoutineEntry().renameRoutine(AccountScope(ownerId), routineId, name)
        override fun copy(routineId: String, name: String) =
            viewModels.getRoutineEntry().copyRoutine(AccountScope(ownerId), routineId, name)
        override fun delete(routineId: String) =
            viewModels.getRoutineEntry().deleteRoutine(AccountScope(ownerId), routineId)
        override fun navigate(screen: FitnessScreen) = host.navigate(screen)
        override fun startWorkout(
            exercises: List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance>
        ) = host.startRoutineWorkout(exercises)
    }
    val exercisePickerActions = object : ExercisePickerScreenActions {
        override fun back() { host.back() }
        override fun search(query: String) = viewModels.getExercisePicker().search(query)
        override fun choose(preset: com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset) =
            viewModels.getExercisePicker().choose(preset)
    }
    Column(
        Modifier.fillMaxWidth(),
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
                    override fun continueWorkout() = host.continueWorkoutIfAvailable()
                    override fun navigate(screen: FitnessScreen) = host.navigate(screen)
                    override fun showBodyMetric() = host.showBodyMetricDialog()
                    override fun openMeals() = host.openMealManagement()
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

                    override fun startEmptyWorkout() = host.startEmptyWorkout()
                    override fun showPastWorkout() = host.showPastWorkoutDialog()
                    override fun selectRoutine(routineId: String) = host.selectRoutine(routineId)
                    override fun navigate(screen: FitnessScreen) = host.navigate(screen)
                    override fun startRoutineWorkout(
                        exercises: List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance>
                    ) = host.startRoutineWorkout(exercises)
                }
            )
            FitnessScreen.CARDIO -> CardioStartScreen(cardioActions)
            FitnessScreen.RECORDS -> RecordsScreen(
                homeState,
                ownerId,
                navigation.uiState.value?.today ?: today,
                unit,
                navigation.uiState.value?.selectedRecordsDate ?: today,
                recordsActions
            )
            FitnessScreen.DEVELOPMENT -> DevelopmentScreen(
                developmentState,
                ownerId,
                unit,
                object : DevelopmentScreenActions {
                    override fun showBodyProfile() = host.showDevelopmentBodyProfileDialog()
                    override fun showGoal() = host.showDevelopmentGoalDialog()
                    override fun openInsightAction(insight: com.yeonsik.fitnessapp.development.DevelopmentInsight) {
                        host.openDevelopmentInsightAction(insight)
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
                host::openWorkoutExerciseDetail
            )
            FitnessScreen.WORKOUT_EXERCISE_DETAIL -> WorkoutDetailScreen(
                workoutDetailState,
                ownerId,
                unit,
                object : WorkoutDetailActions {
                    override fun back() { host.back() }
                    override fun refresh() = host.refreshWorkoutExerciseDetail()
                    override fun replaceExercise(exerciseId: String) {
                        host.openWorkoutExerciseReplacementPicker(exerciseId)
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

                    override fun startRestTimer(restSeconds: Int?) = host.startRestTimer(restSeconds)
                    override fun toast(message: String) = host.toast(message)
                }
            )
            FitnessScreen.WORKOUT_SUMMARY -> WorkoutSummaryScreen(
                workoutState,
                ownerId,
                unit,
                host::back
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
                host.selectedRoutineId(),
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
