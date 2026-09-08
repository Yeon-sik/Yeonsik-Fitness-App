package com.yeonsik.fitnessapp.feature.workout.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentBodyPartSets
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentWeekProgress
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutReadDayMetrics

/** Read-only workout port used by cross-feature read models. */
interface WorkoutReadApi {
    fun sessionsForDate(scope: AccountScope, date: String): List<String>
    fun dayMetrics(scope: AccountScope, date: String): WorkoutReadDayMetrics
    fun latestInProgress(scope: AccountScope): String?
    fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String?
    fun weekProgress(scope: AccountScope, startDate: String, endDate: String): DevelopmentWeekProgress
    fun strengthSetsByBodyPart(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<DevelopmentBodyPartSets>
    fun latestDetailedTrainingDate(
        scope: AccountScope,
        referenceDate: String,
        bodyPartAliases: List<String>
    ): String?
    fun completedRecordedDays(scope: AccountScope, startDate: String, endDate: String): Int
    fun completedDates(scope: AccountScope, startDate: String, endDate: String): List<String>
    fun completedResistanceSessions(scope: AccountScope, startDate: String, endDate: String): Int
}
