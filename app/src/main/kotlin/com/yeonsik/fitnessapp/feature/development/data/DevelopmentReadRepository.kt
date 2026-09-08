package com.yeonsik.fitnessapp.feature.development.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.DevelopmentRoomDao
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentNutritionGoal

/** Development-owned read adapter for nutrition goal projections. */
class DevelopmentReadRepository(roomDatabase: FitnessRoomDatabase) : DevelopmentReadApi {
    private val developmentDao: DevelopmentRoomDao = roomDatabase.developmentRoomDao()

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
}
