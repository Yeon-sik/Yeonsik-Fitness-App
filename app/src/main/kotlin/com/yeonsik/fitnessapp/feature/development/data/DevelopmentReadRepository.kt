package com.yeonsik.fitnessapp.feature.development.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.DevelopmentRoomDao
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInStats
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentNutritionGoal
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInSummary

/** Development-owned read adapter for nutrition goal projections. */
class DevelopmentReadRepository(roomDatabase: FitnessRoomDatabase) : DevelopmentReadApi {
    private val developmentDao: DevelopmentRoomDao = roomDatabase.developmentRoomDao()

    override fun developmentGoal(scope: AccountScope): DevelopmentGoal =
        developmentDao.goal(scope.ownerId)?.let { goal ->
            DevelopmentGoal(
                goal.objective,
                goal.weeklySessionsTarget.toInt(),
                goal.focusBodyPart,
                goal.effectiveFrom,
                goal.createdAt,
                goal.updatedAt
            )
        } ?: DevelopmentGoal.empty()

    override fun nutritionGoal(scope: AccountScope): DevelopmentNutritionGoal? =
        developmentDao.nutritionGoal(scope.ownerId)?.let { goal ->
            DevelopmentNutritionGoal(
                goal.phase,
                goal.caloriesKcal,
                goal.proteinGrams,
                goal.carbsGrams,
                goal.fatGrams,
                goal.fiberGrams,
                goal.sodiumMg,
                goal.waterMl.toInt()
            )
        }

    override fun checkInStats(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): DevelopmentCheckInStats = developmentDao.recentCheckInStats(
        scope.ownerId, startDate, endDate
    ).let { stats ->
        DevelopmentCheckInStats(stats.recordedDays, stats.lowEnergyOrReadinessDays)
    }

    override fun checkInDates(scope: AccountScope, startDate: String, endDate: String): List<String> =
        developmentDao.checkInDates(scope.ownerId, startDate, endDate)

    override fun checkInSummary(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): DevelopmentCheckInSummary {
        val summary = developmentDao.recentCheckInSummary(scope.ownerId, startDate, endDate)
        val latest = developmentDao.latestCheckIn(scope.ownerId, startDate, endDate)
        return DevelopmentCheckInSummary(
            summary.averageSleepHours,
            summary.sleepRecordedDays,
            summary.lowEnergyOrReadinessDays,
            latest?.energyScore?.toInt(),
            latest?.trainingReadinessScore?.toInt()
        )
    }
}
