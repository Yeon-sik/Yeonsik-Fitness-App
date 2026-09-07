package com.yeonsik.fitnessapp.app

import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.cardio.CardioRepository
import com.yeonsik.fitnessapp.routine.RoutineRepository
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.data.HomeReadRepository
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation

/** Manual dependency assembly; framework-wide DI is intentionally not introduced. */
class AppContainer(
    repository: FitnessRepository,
    cardioRepository: CardioRepository,
    routineRepository: RoutineRepository
) {
    val workoutRepository: WorkoutRepositoryApi = WorkoutRepositoryImplementation(repository)
    val cardioRepositoryApi: CardioRepositoryApi = cardioRepository
    val routineRepositoryApi: com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi =
        routineRepository
    val homeRepository: HomeRepositoryApi = HomeReadRepository(repository, routineRepository)
    val initializeWorkoutExercise = InitializeWorkoutExercise(workoutRepository)
    val completeWorkout = CompleteWorkout(workoutRepository)
}
