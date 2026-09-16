package com.yeonsik.fitnessapp.feature.statistics.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSnapshot

/** Read-only boundary for statistics composed from Workout, Body, and Meal-owned facts. */
interface StatisticsReadApi {
    fun load(
        scope: AccountScope,
        referenceDate: String,
        period: StatisticsPeriod
    ): StatisticsSnapshot
}
