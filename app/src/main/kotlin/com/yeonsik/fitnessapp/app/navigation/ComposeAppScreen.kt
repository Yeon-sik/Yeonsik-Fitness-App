package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.yeonsik.fitnessapp.core.ui.*
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
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)
    ) {
        when (screen) {
            FitnessScreen.HOME -> error("HOME is installed by ComposeHomeScreen")
            FitnessScreen.WORKOUT -> WorkoutOverview(host, ownerId, today, unit)
            FitnessScreen.STRENGTH -> StrengthScreen(host, ownerId)
            FitnessScreen.CARDIO -> CardioStartScreen(host)
            FitnessScreen.RECORDS -> RecordsScreen(host, ownerId, today, unit)
            FitnessScreen.DEVELOPMENT -> DevelopmentScreen(host, ownerId, today, unit)
            FitnessScreen.SETTINGS -> SettingsScreen(host)
            FitnessScreen.WORKOUT_SESSION -> WorkoutSessionScreen(host, ownerId, unit)
            FitnessScreen.WORKOUT_EXERCISE_DETAIL -> WorkoutDetailScreen(host, ownerId, unit)
            FitnessScreen.WORKOUT_SUMMARY -> WorkoutSummaryScreen(host, ownerId, unit)
            FitnessScreen.CARDIO_SESSION -> CardioSessionScreen(host, ownerId)
            FitnessScreen.CARDIO_SUMMARY -> CardioSummaryScreen(host, ownerId)
            FitnessScreen.MEALS -> MealScreen(host, ownerId, today, unit)
            FitnessScreen.SUPPLEMENTS -> SupplementScreen(host, ownerId, today)
            FitnessScreen.ROUTINE_DETAIL -> RoutineDetailScreen(host, ownerId)
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD -> ExercisePickerScreen(host, ownerId, screen)
        }
    }
}

