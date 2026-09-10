package com.yeonsik.fitnessapp.app.navigation;

import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionViewModel;
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentViewModel;
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerViewModel;
import com.yeonsik.fitnessapp.feature.home.ui.HomeViewModel;
import com.yeonsik.fitnessapp.feature.meal.ui.MealViewModel;
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryViewModel;
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsViewModel;
import com.yeonsik.fitnessapp.feature.supplement.ui.SupplementViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailViewModel;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionViewModel;

/**
 * Explicit composition-time collection of feature ViewModels used by the Compose root.
 *
 * Keeping this collection separate from navigation actions prevents the destination tree
 * from reaching back into Activity fields to discover feature state.
 */
public final class AppViewModels {
    private final WorkoutSessionViewModel workoutSession;
    private final WorkoutExerciseDetailViewModel workoutExerciseDetail;
    private final CardioSessionViewModel cardioSession;
    private final RoutineEntryViewModel routineEntry;
    private final HomeViewModel home;
    private final DevelopmentViewModel development;
    private final SupplementViewModel supplement;
    private final ExercisePickerViewModel exercisePicker;
    private final MealViewModel meal;
    private final SettingsViewModel settings;

    public AppViewModels(
            WorkoutSessionViewModel workoutSession,
            WorkoutExerciseDetailViewModel workoutExerciseDetail,
            CardioSessionViewModel cardioSession,
            RoutineEntryViewModel routineEntry,
            HomeViewModel home,
            DevelopmentViewModel development,
            SupplementViewModel supplement,
            ExercisePickerViewModel exercisePicker,
            MealViewModel meal,
            SettingsViewModel settings
    ) {
        this.workoutSession = workoutSession;
        this.workoutExerciseDetail = workoutExerciseDetail;
        this.cardioSession = cardioSession;
        this.routineEntry = routineEntry;
        this.home = home;
        this.development = development;
        this.supplement = supplement;
        this.exercisePicker = exercisePicker;
        this.meal = meal;
        this.settings = settings;
    }

    public WorkoutSessionViewModel getWorkoutSession() {
        return workoutSession;
    }

    public WorkoutExerciseDetailViewModel getWorkoutExerciseDetail() {
        return workoutExerciseDetail;
    }

    public CardioSessionViewModel getCardioSession() {
        return cardioSession;
    }

    public RoutineEntryViewModel getRoutineEntry() {
        return routineEntry;
    }

    public HomeViewModel getHome() {
        return home;
    }

    public DevelopmentViewModel getDevelopment() {
        return development;
    }

    public SupplementViewModel getSupplement() {
        return supplement;
    }

    public ExercisePickerViewModel getExercisePicker() {
        return exercisePicker;
    }

    public MealViewModel getMeal() {
        return meal;
    }

    public SettingsViewModel getSettings() {
        return settings;
    }
}
