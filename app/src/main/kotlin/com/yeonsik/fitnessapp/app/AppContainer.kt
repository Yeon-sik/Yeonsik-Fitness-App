package com.yeonsik.fitnessapp.app

import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.cardio.CardioRepository
import com.yeonsik.fitnessapp.routine.RoutineRepository
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.data.LegacyCardioRepositoryAdapter
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.data.LegacyHomeRepositoryAdapter
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.data.LegacyRoutineRepositoryAdapter
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.data.LegacyWorkoutRepositoryAdapter

/** Manual dependency assembly; framework-wide DI is intentionally not introduced. */
class AppContainer(
    repository: FitnessRepository,
    cardioRepository: CardioRepository,
    routineRepository: RoutineRepository
) {
    val workoutRepository: WorkoutRepositoryApi = LegacyWorkoutRepositoryAdapter(repository)
    val cardioRepositoryApi: CardioRepositoryApi =
        LegacyCardioRepositoryAdapter(cardioRepository, repository)
    val routineRepositoryApi: RoutineRepositoryApi =
        LegacyRoutineRepositoryAdapter(routineRepository, repository)
    val homeRepository: HomeRepositoryApi = LegacyHomeRepositoryAdapter(repository, routineRepository)
    val initializeWorkoutExercise = InitializeWorkoutExercise(workoutRepository)
    val completeWorkout = CompleteWorkout(workoutRepository)
}
