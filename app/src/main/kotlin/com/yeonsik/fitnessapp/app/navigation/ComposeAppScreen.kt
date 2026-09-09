package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.ScreenHost
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

/** The single Compose navigation destination host for every application screen. */
object ComposeAppScreen {
    @JvmStatic
    fun install(view: ComposeView, host: ScreenHost, screen: FitnessScreen,
                ownerId: String, today: String, unit: MassUnit, dark: Boolean) {
        view.setContent {
            FitnessComposeTheme(dark) {
                AppDestination(host, screen, ownerId, today, unit)
            }
        }
    }
}

@Composable
private fun AppDestination(host: ScreenHost, screen: FitnessScreen, ownerId: String,
                           today: String, unit: MassUnit) {
    val homeState by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val routineState by host.routineEntryViewModel().uiState.observeAsState(RoutineEntryUiState.Idle)
    val workoutState by host.workoutSessionViewModel().uiState
        .observeAsState(WorkoutSessionUiState.Idle)
    val workoutDetailState by host.workoutExerciseDetailViewModel().uiState
        .observeAsState(WorkoutExerciseDetailUiState.Idle)
    val cardioState by host.cardioSessionViewModel().uiState
        .observeAsState(CardioSessionUiState.Idle)
    val cardioRouteState by host.cardioSessionViewModel().routeState
        .observeAsState(CardioRouteUiState.Idle)
    val developmentState by host.developmentViewModel().uiState
        .observeAsState(DevelopmentUiState.Idle)
    LaunchedEffect(screen, ownerId, today) {
        if (screen == FitnessScreen.DEVELOPMENT) {
            host.developmentViewModel().enter(AccountScope(ownerId), today)
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
            host.cardioSessionViewModel().loadRoute(AccountScope(ownerId), recordId)
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)
    ) {
        when (screen) {
            FitnessScreen.HOME -> error("HOME is installed by ComposeHomeScreen")
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
                        host.routineEntryViewModel().createRoutine(AccountScope(ownerId), name)
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
            FitnessScreen.RECORDS -> RecordsScreen(host, ownerId, today, unit)
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
            FitnessScreen.SETTINGS -> SettingsScreen(host)
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
                        host.workoutExerciseDetailViewModel().addTypedSet(
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
                        host.workoutExerciseDetailViewModel().updateTypedSet(
                            AccountScope(ownerId), recordId, setId, input,
                            java.util.function.Consumer { result -> onResult(result) }
                        )
                    }

                    override fun deleteSet(
                        recordId: String,
                        setId: String,
                        onResult: (Boolean) -> Unit
                    ) {
                        host.workoutExerciseDetailViewModel().deleteSet(
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
            FitnessScreen.MEALS -> MealScreen(host, ownerId, today, unit)
            FitnessScreen.SUPPLEMENTS -> SupplementScreen(host, ownerId, today)
            FitnessScreen.ROUTINE_DETAIL -> RoutineDetailScreen(host, ownerId)
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD -> ExercisePickerScreen(host, ownerId, screen)
        }
    }
}

