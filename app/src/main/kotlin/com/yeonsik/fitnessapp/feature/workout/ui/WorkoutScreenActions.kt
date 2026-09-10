package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.state.FitnessScreen

/**
 * Actions exposed to workout composables by the application route.
 *
 * The UI does not know about Activity, repositories, or another
 * feature's ViewModel. Route assembly owns those adapters until the remaining
 * U2 work moves the operations into feature ViewModels.
 */
internal interface WorkoutOverviewActions {
    fun continueWorkout()
    fun navigate(screen: FitnessScreen)
    fun showBodyMetric()
    fun openMeals()
}

internal interface StrengthActions {
    fun createRoutine(name: String)
    fun startEmptyWorkout()
    fun showPastWorkout()
    fun selectRoutine(routineId: String)
    fun navigate(screen: FitnessScreen)
    fun startRoutineWorkout(
        routineId: String?,
        title: String,
        exercises: List<RoutineExerciseInstance>
    )
}

internal interface WorkoutDetailActions {
    fun back()
    fun refresh()
    fun replaceExercise(exerciseId: String)
    fun addSet(
        recordId: String,
        exerciseId: String,
        setIndex: Int,
        input: WorkoutSetInput,
        onResult: (Boolean) -> Unit
    )
    fun updateSet(
        recordId: String,
        setId: String,
        input: WorkoutSetInput,
        onResult: (Boolean) -> Unit
    )
    fun deleteSet(recordId: String, setId: String, onResult: (Boolean) -> Unit)
    fun startRestTimer(restSeconds: Int?)
    fun toast(message: String)
}
