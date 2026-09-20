package com.yeonsik.fitnessapp.feature.nutrition.analysis.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport

/** Read-only nutrition analysis port; it never writes catalog, meal, or recovery facts. */
interface NutritionAnalysisApi {
    fun analyze(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): NutritionAnalysisReport

    fun analyzeDay(scope: AccountScope, date: String): NutritionAnalysisReport =
        analyze(scope, date, date)
}
