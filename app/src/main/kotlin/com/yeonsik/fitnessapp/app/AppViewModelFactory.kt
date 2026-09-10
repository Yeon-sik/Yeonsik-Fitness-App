package com.yeonsik.fitnessapp.app

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.yeonsik.fitnessapp.app.navigation.AppNavigationViewModel
import com.yeonsik.fitnessapp.feature.body.ui.BodyMetricsViewModel
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionViewModel
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentViewModel
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerViewModel
import com.yeonsik.fitnessapp.feature.home.ui.HomeViewModel
import com.yeonsik.fitnessapp.feature.meal.ui.MealViewModel
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryViewModel
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsViewModel
import com.yeonsik.fitnessapp.feature.supplement.ui.SupplementViewModel
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailViewModel
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionViewModel

/**
 * Activity-scoped ViewModel composition owned by the application container.
 *
 * The Activity only asks the lifecycle provider for a model. Feature dependencies and the
 * shared workout write queue stay in the composition root, so route setup does not become a
 * second dependency container.
 */
class AppViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val container: AppContainer
) : AbstractSavedStateViewModelFactory(owner, null) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(AppNavigationViewModel::class.java) ->
                AppNavigationViewModel(handle)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    handle,
                    container.massUnitPreferences,
                    container.themeModePreferences,
                    container.configStore,
                    container.nutritionConfigStore,
                    container.priceTraceConfigStore,
                    container.supabaseAuthManager,
                    container.nutritionAuthManager,
                    container.priceTraceAuthManager,
                    container.syncApplicationService,
                    container.localDataTransferApplicationService,
                    container,
                )
            modelClass.isAssignableFrom(BodyMetricsViewModel::class.java) ->
                BodyMetricsViewModel(handle, container.bodyMetricsApplicationService)
            modelClass.isAssignableFrom(WorkoutSessionViewModel::class.java) ->
                WorkoutSessionViewModel(
                    handle,
                    container.workoutRepository,
                    container.completeWorkout,
                    container.workoutSessionApplicationService,
                    container.getWorkoutWriteExecutor(),
                    false,
                )
            modelClass.isAssignableFrom(WorkoutExerciseDetailViewModel::class.java) ->
                WorkoutExerciseDetailViewModel(
                    handle,
                    container.workoutRepository,
                    container.initializeWorkoutExercise,
                    container.getWorkoutWriteExecutor(),
                    false,
                )
            modelClass.isAssignableFrom(CardioSessionViewModel::class.java) ->
                CardioSessionViewModel(
                    handle,
                    container.cardioRepositoryApi,
                    container.cardioSessionApplicationService,
                )
            modelClass.isAssignableFrom(RoutineEntryViewModel::class.java) ->
                RoutineEntryViewModel(handle, container.routineRepositoryApi)
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(handle, container.homeRepository)
            modelClass.isAssignableFrom(DevelopmentViewModel::class.java) ->
                DevelopmentViewModel(
                    handle,
                    container.developmentReportApi,
                    container.developmentApplicationService,
                )
            modelClass.isAssignableFrom(SupplementViewModel::class.java) ->
                SupplementViewModel(handle, container.supplementRepositoryApi)
            modelClass.isAssignableFrom(ExercisePickerViewModel::class.java) ->
                ExercisePickerViewModel(
                    handle,
                    container.exerciseMasterRepositoryApi,
                    container.routineRepositoryApi,
                    container.workoutRepository,
                )
            modelClass.isAssignableFrom(MealViewModel::class.java) ->
                MealViewModel(
                    handle,
                    container.mealRecordRepositoryApi,
                    container.nutritionCatalogRepositoryApi,
                    container.nutritionIntegrationService,
                )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
