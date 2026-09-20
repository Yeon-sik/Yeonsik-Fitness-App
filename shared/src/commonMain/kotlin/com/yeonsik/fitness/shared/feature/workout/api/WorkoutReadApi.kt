package com.yeonsik.fitness.shared.feature.workout.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutBodyPartSets
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercisePerformancePoint
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadDayMetrics
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadSessionSummary
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutWeekProgress

/** Read-only workout port used by cross-feature read models. */
interface WorkoutReadApi {
    fun sessionsForDate(scope: AccountScope, date: String): List<String>
    fun dayMetrics(scope: AccountScope, date: String): WorkoutReadDayMetrics
    fun latestInProgress(scope: AccountScope): String?
    fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String?
    fun weekProgress(scope: AccountScope, startDate: String, endDate: String): WorkoutWeekProgress
    fun strengthSetsByBodyPart(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutBodyPartSets>
    fun latestDetailedTrainingDate(
        scope: AccountScope,
        referenceDate: String,
        bodyPartAliases: List<String>
    ): String?
    fun completedRecordedDays(scope: AccountScope, startDate: String, endDate: String): Int
    fun completedDates(scope: AccountScope, startDate: String, endDate: String): List<String>

    /**
     * Completed session facts for read-only cross-feature compositions such as Records.
     * Implementations must keep the source account and completed-record policy unchanged.
     */
    fun completedSessionSummaries(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutReadSessionSummary> = emptyList()

    fun exercisePerformance(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<WorkoutExercisePerformancePoint> = emptyList()

    fun completedResistanceSessions(scope: AccountScope, startDate: String, endDate: String): Int
}
