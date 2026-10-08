package com.yeonsik.fitnessapp.feature.meal.data

import com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.nutrition.analysis.application.NutritionAnalysisCalculator
import java.time.LocalDate

/** Reuses Nutrition analysis of immutable intake snapshots, including partial item records. */
internal object MealNutritionSummaryCalculator {
    fun calculate(start: LocalDate, end: LocalDate, snapshots: List<MealSnapshotRead>): MealNutritionReadSummary {
        val report = NutritionAnalysisCalculator.calculate(start, end, snapshots, null)
        val protein = report.metric(NutritionProfile.PROTEIN_GRAMS)!!
        return MealNutritionReadSummary(
            proteinGrams = protein.knownSum.takeIf { protein.recordedCount + protein.estimatedCount > 0 },
            recordedDays = report.recordedDays,
            mealCount = report.recordedMealCount,
            estimatedMealCount = report.estimatedMealCount,
            proteinKnownMealCount = protein.knownCount,
            proteinMissingMealCount = protein.missingCount,
            proteinEstimatedMealCount = protein.estimatedCount,
            proteinUnknownProvenanceMealCount = protein.unknownProvenanceCount,
            proteinCompleteDays = report.meals.groupBy { it.date }.values.count { meals ->
                meals.all { it.proteinMetric()?.let { metric ->
                    metric.isComplete && metric.unknownProvenanceCount == 0
                } == true }
            }
        )
    }
}
