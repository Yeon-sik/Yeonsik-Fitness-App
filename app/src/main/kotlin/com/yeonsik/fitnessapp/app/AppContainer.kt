package com.yeonsik.fitnessapp.app

import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout

/** Manual dependency assembly; framework-wide DI is intentionally not introduced. */
class AppContainer(
    workoutRepository: WorkoutRepositoryApi,
    cardioRepository: CardioRepositoryApi,
    routineRepository: RoutineRepositoryApi,
    homeRepository: HomeRepositoryApi
) {
    val workoutRepository: WorkoutRepositoryApi = workoutRepository
    val cardioRepositoryApi: CardioRepositoryApi = cardioRepository
    val routineRepositoryApi: RoutineRepositoryApi = routineRepository
    val homeRepository: HomeRepositoryApi = homeRepository
    val initializeWorkoutExercise = InitializeWorkoutExercise(workoutRepository)
    val completeWorkout = CompleteWorkout(workoutRepository)
}
