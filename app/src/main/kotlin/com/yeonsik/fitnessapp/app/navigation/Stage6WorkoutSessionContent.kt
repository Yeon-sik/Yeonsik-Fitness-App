package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.runtime.Composable
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionContent

@Composable
internal fun Stage6WorkoutSessionContent(
    session: WorkoutSessionSnapshot,
    unit: MassUnit,
    onExercise: (String) -> Unit
) = WorkoutSessionContent(session, unit, onExercise)
