package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.EnumMap;
import java.util.Map;

/** Builds the remaining platform-backed screens at the navigation boundary. */
public final class ScreenRegistry {
    private ScreenRegistry() {
    }

    public static Map<FitnessScreen, BaseScreen> create(ScreenHost host) {
        Map<FitnessScreen, BaseScreen> screens = new EnumMap<>(FitnessScreen.class);
        RoutineEditorScreen routineEditor = new RoutineEditorScreen(host);
        screens.put(FitnessScreen.WORKOUT, new WorkoutScreen(host));
        screens.put(FitnessScreen.STRENGTH, new StrengthScreen(host));
        screens.put(FitnessScreen.CARDIO, new CardioScreen(host));
        screens.put(FitnessScreen.RECORDS, new RecordsScreen(host));
        screens.put(FitnessScreen.DEVELOPMENT, new DevelopmentScreen(host));
        screens.put(FitnessScreen.SETTINGS, new SettingsScreen(host));
        screens.put(FitnessScreen.WORKOUT_SESSION, new WorkoutSessionScreen(host));
        screens.put(FitnessScreen.WORKOUT_EXERCISE_DETAIL, new WorkoutExerciseDetailScreen(host));
        screens.put(FitnessScreen.WORKOUT_SUMMARY, new WorkoutSummaryScreen(host));
        screens.put(FitnessScreen.CARDIO_SESSION, new CardioSessionScreen(host));
        screens.put(FitnessScreen.CARDIO_SUMMARY, new CardioSummaryScreen(host));
        screens.put(FitnessScreen.MEALS, new MealManagementScreen(host));
        screens.put(FitnessScreen.SUPPLEMENTS, new SupplementScreen(host));
        screens.put(FitnessScreen.ROUTINE_ADD, routineEditor);
        screens.put(FitnessScreen.ROUTINE_DETAIL, routineEditor);
        screens.put(FitnessScreen.WORKOUT_EXERCISE_ADD, routineEditor);
        return screens;
    }
}
