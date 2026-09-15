package com.yeonsik.fitnessapp.feature.nutrition.analysis.model

import com.yeonsik.fitnessapp.data.NutritionProfile
import java.util.Locale

/** Provenance of known nutrition values; estimated and recorded values are never silently merged. */
enum class NutritionDataQuality {
    UNKNOWN,
    RECORDED,
    ESTIMATED,
    MIXED;

    fun label(): String = when (this) {
        UNKNOWN -> "알 수 없음"
        RECORDED -> "기록값"
        ESTIMATED -> "추정값"
        MIXED -> "기록값·추정값 혼합"
    }
}

enum class NutritionTargetStatus {
    NO_TARGET,
    UNKNOWN_ACTUAL,
    BELOW_TARGET,
    AT_TARGET,
    ABOVE_TARGET;

    fun label(): String = when (this) {
        NO_TARGET -> "목표 없음"
        UNKNOWN_ACTUAL -> "섭취량 미상"
        BELOW_TARGET -> "목표 미만"
        AT_TARGET -> "목표 도달"
        ABOVE_TARGET -> "목표 초과"
    }
}

/** Aggregated value with explicit known/missing and estimated/recorded counts. */
data class NutritionAnalysisMetric(
    val key: String,
    val unit: String,
    val knownSum: Double,
    val knownCount: Int,
    val missingCount: Int,
    val estimatedCount: Int,
    val recordedCount: Int,
    val unknownProvenanceCount: Int = 0
) {
    val isComplete: Boolean
        get() = knownCount > 0 && missingCount == 0

    val completeValue: Double?
        get() = if (isComplete) knownSum else null

    val dataQuality: NutritionDataQuality
        get() = when {
            knownCount == 0 -> NutritionDataQuality.UNKNOWN
            unknownProvenanceCount > 0 && estimatedCount == 0 && recordedCount == 0 ->
                NutritionDataQuality.UNKNOWN
            estimatedCount > 0 && (recordedCount > 0 || unknownProvenanceCount > 0) ->
                NutritionDataQuality.MIXED
            unknownProvenanceCount > 0 && recordedCount > 0 -> NutritionDataQuality.MIXED
            estimatedCount > 0 -> NutritionDataQuality.ESTIMATED
            else -> NutritionDataQuality.RECORDED
        }

    fun displayValue(): String = when {
        knownCount == 0 -> "?"
        isComplete -> format(knownSum)
        else -> "≥${format(knownSum)} (미상 ${missingCount}건)"
    }

    fun provenanceLabel(): String = when {
        knownCount == 0 -> "값 없음"
        dataQuality == NutritionDataQuality.UNKNOWN && unknownProvenanceCount > 0 ->
            "출처 미상 ${unknownProvenanceCount}건"
        dataQuality == NutritionDataQuality.MIXED ->
            "기록 ${recordedCount}건 · 추정 ${estimatedCount}건 · 출처 미상 ${unknownProvenanceCount}건"
        dataQuality == NutritionDataQuality.ESTIMATED -> "추정 ${estimatedCount}건"
        else -> "기록 ${recordedCount}건"
    }

    private fun format(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

data class NutritionAnalysisTarget(
    val phase: String?,
    val dailyValues: Map<String, Double>
) {
    init {
        require(dailyValues.values.all { it.isFinite() && it > 0 }) {
            "Nutrition targets must be positive finite values."
        }
    }

    fun dailyValue(key: String): Double? = dailyValues[key.trim().lowercase(Locale.US)]
}

data class NutritionTargetComparison(
    val key: String,
    val unit: String,
    val actual: NutritionAnalysisMetric,
    val targetValue: Double?,
    val targetDays: Int,
    val ratio: Double?,
    val delta: Double?,
    val status: NutritionTargetStatus
)

data class NutritionAnalysisMeal(
    val id: String,
    val date: String,
    val title: String,
    val metrics: Map<String, NutritionAnalysisMetric>,
    val dataQuality: NutritionDataQuality
) {
    fun metric(key: String): NutritionAnalysisMetric? =
        metrics[key.trim().lowercase(Locale.US)]

    fun proteinMetric(): NutritionAnalysisMetric? = metric(NutritionProfile.PROTEIN_GRAMS)
}

data class NutritionProteinDistributionEntry(
    val mealId: String,
    val date: String,
    val title: String,
    val protein: NutritionAnalysisMetric,
    val shareOfKnownTotalPercent: Double?
)

data class NutritionProteinDistribution(
    val entries: List<NutritionProteinDistributionEntry>,
    val knownTotalGrams: Double,
    val knownMealCount: Int,
    val unknownMealCount: Int,
    val isComplete: Boolean
)

data class NutritionAnalysisReport(
    val startDate: String,
    val endDate: String,
    val calendarDayCount: Int,
    val recordedMealCount: Int,
    val recordedDays: Int,
    val metrics: Map<String, NutritionAnalysisMetric>,
    val target: NutritionAnalysisTarget?,
    val targetComparisons: Map<String, NutritionTargetComparison>,
    val proteinDistribution: NutritionProteinDistribution,
    val meals: List<NutritionAnalysisMeal>
) {
    fun metric(key: String): NutritionAnalysisMetric? =
        metrics[key.trim().lowercase(Locale.US)]

    fun comparison(key: String): NutritionTargetComparison? =
        targetComparisons[key.trim().lowercase(Locale.US)]

    val estimatedMealCount: Int
        get() = meals.count { it.dataQuality == NutritionDataQuality.ESTIMATED }
}
