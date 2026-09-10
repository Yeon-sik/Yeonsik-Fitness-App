package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentBodyPartSets
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentWeekProgress
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutReadDayMetrics

/** Adapter exposing workout-owned read calculations without exposing storage rows. */
class WorkoutReadRepository(
    private val storage: WorkoutRoomStorage
) : WorkoutReadApi {
    constructor(roomDatabase: FitnessRoomDatabase, context: Context) : this(
        WorkoutRoomStorage(roomDatabase, context)
    )

    override fun sessionsForDate(scope: AccountScope, date: String): List<String> =
        storage.sessionsForDate(scope, date)

    override fun dayMetrics(scope: AccountScope, date: String): WorkoutReadDayMetrics =
        storage.dayMetrics(scope, date).let {
            WorkoutReadDayMetrics(
                it.sessionCount,
                it.totalSetCount,
                it.totalVolumeKg,
                it.totalDurationSeconds
            )
        }

    override fun latestInProgress(scope: AccountScope): String? = storage.latestInProgress(scope)

    override fun latestRoutineDate(
        scope: AccountScope,
        routineId: String,
        routineName: String
    ): String? = storage.latestCompletedForRoutine(scope, routineId, routineName)

    override fun weekProgress(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): DevelopmentWeekProgress = storage.weekProgress(scope, startDate, endDate)

    override fun strengthSetsByBodyPart(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<DevelopmentBodyPartSets> = storage.strengthSetsByBodyPart(scope, startDate, endDate)

    override fun latestDetailedTrainingDate(
        scope: AccountScope,
        referenceDate: String,
        bodyPartAliases: List<String>
    ): String? = storage.latestDetailedTrainingDate(scope, referenceDate, bodyPartAliases)

    override fun completedRecordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
        storage.completedRecordedDays(scope, startDate, endDate)

    override fun completedDates(scope: AccountScope, startDate: String, endDate: String): List<String> =
        storage.completedDates(scope, startDate, endDate)

    override fun completedResistanceSessions(scope: AccountScope, startDate: String, endDate: String): Int =
        storage.completedResistanceSessions(scope, startDate, endDate)
}
