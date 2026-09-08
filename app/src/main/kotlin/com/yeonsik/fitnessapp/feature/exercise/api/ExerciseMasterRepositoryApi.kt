package com.yeonsik.fitnessapp.feature.exercise.api

import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog

/** Read-only exercise catalog port used by picker application state. */
interface ExerciseMasterRepositoryApi {
    fun runtimeCatalog(): RuntimeExerciseCatalog
}
