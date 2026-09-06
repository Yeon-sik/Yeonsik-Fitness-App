package com.yeonsik.fitnessapp.app

import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.data.LegacyWorkoutRepositoryAdapter

/** Manual dependency assembly; framework-wide DI is intentionally not introduced. */
class AppContainer(repository: FitnessRepository) {
    val workoutRepository: WorkoutRepositoryApi = LegacyWorkoutRepositoryAdapter(repository)
    val initializeWorkoutExercise = InitializeWorkoutExercise(workoutRepository)
}
