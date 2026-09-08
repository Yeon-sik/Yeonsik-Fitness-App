package com.yeonsik.fitnessapp.feature.workout.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutReadDayMetrics

/** Read-only workout port used by cross-feature read models. */
interface WorkoutReadApi {
    fun sessionsForDate(scope: AccountScope, date: String): List<String>
    fun dayMetrics(scope: AccountScope, date: String): WorkoutReadDayMetrics
    fun latestInProgress(scope: AccountScope): String?
    fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String?
}
