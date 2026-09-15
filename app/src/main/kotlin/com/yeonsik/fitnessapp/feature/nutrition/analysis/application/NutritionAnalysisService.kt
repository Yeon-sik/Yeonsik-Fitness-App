package com.yeonsik.fitnessapp.feature.nutrition.analysis.application

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotItemRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotNutritionRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.feature.nutrition.analysis.api.NutritionAnalysisApi
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisMeal
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisMetric
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisTarget
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionDataQuality
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionProteinDistribution
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionProteinDistributionEntry
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionTargetComparison
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionTargetStatus
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs

/**
 * Composes Meal-owned intake snapshots with the Recovery-owned user target.
 *
 * <p>The service never re-resolves a catalog food. For complex meals it applies the immutable
 * intake fraction to the top-level menu and adds only components that carry their own consumed
 * fraction, avoiding double counting recipe/component detail.</p>
 */
class NutritionAnalysisService(
    private val meals: MealReadApi,
    private val recovery: RecoveryRepositoryApi
) : NutritionAnalysisApi {
    override fun analyze(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): NutritionAnalysisReport {
        require(scope.ownerId.isNotBlank()) { "Nutrition analysis owner is required." }
        val start = LocalDate.parse(startDate.trim())
        val end = LocalDate.parse(endDate.trim())
        require(!end.isBefore(start)) { "Nutrition analysis end date must not precede start date." }
        val snapshots = meals.mealSnapshots(scope, start.toString(), end.toString())
        val target = recovery.nutritionGoal(scope)?.toAnalysisTarget()
        return NutritionAnalysisCalculator.calculate(start, end, snapshots, target)
    }
}

/** Pure analysis calculation kept independent from Android, Room, and UI. */
internal object NutritionAnalysisCalculator {
    private val canonicalKeys = (
        NutritionProfile.PRIMARY_DISPLAY_ORDER + NutritionProfile.RECOMMENDED_TYPED_KEYS
        ).distinct()

    fun calculate(
        start: LocalDate,
        end: LocalDate,
        snapshots: List<MealSnapshotRead>,
        target: NutritionAnalysisTarget?
    ): NutritionAnalysisReport {
        val meals = snapshots
            .sortedWith(compareBy<MealSnapshotRead> { it.date }.thenBy { it.id })
            .map(::projectMeal)
        val keys = linkedSetOf<String>().apply {
            addAll(canonicalKeys)
            meals.forEach { addAll(it.metrics.keys) }
            target?.dailyValues?.keys?.let(::addAll)
        }
        val metrics = keys.associateWith { key ->
            aggregateMetric(key, meals, snapshots.size)
        }
        val comparisons = target?.dailyValues?.keys?.associateWith { key ->
            comparison(key, metrics.getValue(key), target, dayCount(start, end))
        }.orEmpty()
        val protein = proteinDistribution(meals, metrics[NutritionProfile.PROTEIN_GRAMS])
        return NutritionAnalysisReport(
            startDate = start.toString(),
            endDate = end.toString(),
            calendarDayCount = dayCount(start, end),
            recordedMealCount = meals.size,
            recordedDays = meals.map { it.date }.toSet().size,
            metrics = metrics,
            target = target,
            targetComparisons = comparisons,
            proteinDistribution = protein,
            meals = meals
        )
    }

    private fun projectMeal(snapshot: MealSnapshotRead): NutritionAnalysisMeal {
        val estimated = isEstimated(snapshot.metadata)
        val explicitUnknown = nutritionStatus(snapshot.metadata) == "unknown"
        val recordNutrition = effectiveNutrition(snapshot)
        val keys = linkedSetOf<String>().apply {
            addAll(canonicalKeys)
            addAll(recordNutrition.keys)
        }
        val metrics = keys.associateWith { key ->
            val value = recordNutrition[key]
            NutritionAnalysisMetric(
                key = key,
                unit = NutritionProfile.unitOf(key),
                knownSum = value?.knownSum ?: 0.0,
                knownCount = if (value?.hasKnown == true) 1 else 0,
                missingCount = if (value?.hasMissing == true) 1 else 0,
                estimatedCount = if (value?.hasKnown == true && estimated) 1 else 0,
                recordedCount = if (value?.hasKnown == true && !estimated && !explicitUnknown) 1 else 0,
                unknownProvenanceCount = if (explicitUnknown) 1 else 0
            )
        }
        val hasKnownValue = metrics.values.any { it.knownCount > 0 }
        val quality = when {
            !hasKnownValue || explicitUnknown -> NutritionDataQuality.UNKNOWN
            estimated -> NutritionDataQuality.ESTIMATED
            else -> NutritionDataQuality.RECORDED
        }
        return NutritionAnalysisMeal(
            id = snapshot.id,
            date = snapshot.date,
            title = mealTitle(snapshot),
            metrics = metrics,
            dataQuality = quality
        )
    }

    private fun effectiveNutrition(snapshot: MealSnapshotRead): Map<String, RecordNutrient> {
        val items = snapshot.items
        if (items.isEmpty()) {
            return recordNutrients(listOf(snapshot.nutrition.asNullableMap()))
        }
        val contributions = ArrayList<Map<String, Double?>>()
        items.forEach { item ->
            val factor = item.consumption?.consumedFraction ?: 1.0
            contributions += item.nutrition.asNullableMap().scaled(factor)
            item.components
                .filter { it.consumedFraction != null }
                .forEach { component ->
                    contributions += component.nutrition.asNullableMap().scaled(
                        component.consumedFraction ?: 1.0
                    )
                }
        }
        return recordNutrients(contributions.ifEmpty { listOf(snapshot.nutrition.asNullableMap()) })
    }

    private fun recordNutrients(
        contributions: List<Map<String, Double?>>
    ): Map<String, RecordNutrient> {
        val keys = linkedSetOf<String>().apply {
            addAll(canonicalKeys)
            contributions.forEach { addAll(it.keys) }
        }
        return keys.associateWith { key ->
            val values = contributions.map { it[key] }
            val known = values.filterNotNull()
            RecordNutrient(
                knownSum = known.sum(),
                hasKnown = known.isNotEmpty(),
                hasMissing = values.any { it == null }
            )
        }
    }

    private fun aggregateMetric(
        key: String,
        meals: List<NutritionAnalysisMeal>,
        mealCount: Int
    ): NutritionAnalysisMetric {
        var knownSum = 0.0
        var knownCount = 0
        var missingCount = 0
        var estimatedCount = 0
        var recordedCount = 0
        var unknownProvenanceCount = 0
        meals.forEach { meal ->
            val metric = meal.metric(key)
            if (metric == null) {
                missingCount++
            } else {
                knownSum += metric.knownSum
                knownCount += metric.knownCount
                missingCount += metric.missingCount
                estimatedCount += metric.estimatedCount
                recordedCount += metric.recordedCount
                unknownProvenanceCount += metric.unknownProvenanceCount
            }
        }
        return NutritionAnalysisMetric(
            key = key,
            unit = NutritionProfile.unitOf(key),
            knownSum = knownSum,
            knownCount = knownCount,
            missingCount = if (mealCount == 0) 0 else missingCount,
            estimatedCount = estimatedCount,
            recordedCount = recordedCount,
            unknownProvenanceCount = unknownProvenanceCount
        )
    }

    private fun comparison(
        key: String,
        actual: NutritionAnalysisMetric,
        target: NutritionAnalysisTarget,
        targetDays: Int
    ): NutritionTargetComparison {
        val targetValue = target.dailyValue(key)?.let { it * targetDays }
        val actualValue = actual.completeValue
        val ratio = if (actualValue != null && targetValue != null && targetValue > 0) {
            actualValue / targetValue
        } else {
            null
        }
        val delta = if (actualValue != null && targetValue != null) {
            actualValue - targetValue
        } else {
            null
        }
        val status = when {
            targetValue == null -> NutritionTargetStatus.NO_TARGET
            actualValue == null -> NutritionTargetStatus.UNKNOWN_ACTUAL
            abs(actualValue - targetValue) < 0.0001 -> NutritionTargetStatus.AT_TARGET
            actualValue < targetValue -> NutritionTargetStatus.BELOW_TARGET
            else -> NutritionTargetStatus.ABOVE_TARGET
        }
        return NutritionTargetComparison(
            key = key,
            unit = NutritionProfile.unitOf(key),
            actual = actual,
            targetValue = targetValue,
            targetDays = targetDays,
            ratio = ratio,
            delta = delta,
            status = status
        )
    }

    private fun proteinDistribution(
        meals: List<NutritionAnalysisMeal>,
        total: NutritionAnalysisMetric?
    ): NutritionProteinDistribution {
        val totalValue = total?.completeValue
        val entries = meals.map { meal ->
            val protein = meal.proteinMetric() ?: emptyMetric(NutritionProfile.PROTEIN_GRAMS)
            val mealProtein = protein.completeValue
            NutritionProteinDistributionEntry(
                mealId = meal.id,
                date = meal.date,
                title = meal.title,
                protein = protein,
                shareOfKnownTotalPercent = if (
                    totalValue != null && totalValue > 0 && mealProtein != null
                ) {
                    mealProtein / totalValue * 100.0
                } else {
                    null
                }
            )
        }
        return NutritionProteinDistribution(
            entries = entries,
            knownTotalGrams = total?.knownSum ?: 0.0,
            knownMealCount = entries.count { it.protein.knownCount > 0 },
            unknownMealCount = entries.count { it.protein.knownCount == 0 || !it.protein.isComplete },
            isComplete = total?.isComplete == true
        )
    }

    private fun mealTitle(snapshot: MealSnapshotRead): String {
        val metadata = runCatching { JSONObject(snapshot.metadata.ifBlank { "{}" }) }.getOrNull()
        return metadata?.optString("menu_name", "")?.trim().orEmpty().takeIf { it.isNotEmpty() }
            ?: snapshot.items.firstOrNull()?.foodName?.takeIf { it.isNotBlank() }
            ?: "식사 기록"
    }

    private fun nutritionStatus(metadata: String): String =
        metadataString(metadata, "nutrition_status")
            .orEmpty()
            .trim()
            .lowercase(Locale.US)

    private fun isEstimated(metadata: String): Boolean {
        if (nutritionStatus(metadata) == "estimated") {
            return true
        }
        return metadataBoolean(metadata, "estimated") == true
    }

    private fun metadataString(metadata: String, key: String): String? {
        val parsed = runCatching {
            JSONObject(metadata.ifBlank { "{}" }).optString(key, "")
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        if (parsed != null) return parsed
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(metadata)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun metadataBoolean(metadata: String, key: String): Boolean? {
        val parsed = runCatching {
            JSONObject(metadata.ifBlank { "{}" }).opt(key)
        }.getOrNull()
        when (parsed) {
            is Boolean -> return parsed
            is String -> parsed.trim().toBooleanStrictOrNull()?.let { return it }
        }
        val pattern = Regex(
            "\"${Regex.escape(key)}\"\\s*:\\s*(true|false)",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(metadata)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    private fun emptyMetric(key: String): NutritionAnalysisMetric = NutritionAnalysisMetric(
        key = key,
        unit = NutritionProfile.unitOf(key),
        knownSum = 0.0,
        knownCount = 0,
        missingCount = 1,
        estimatedCount = 0,
        recordedCount = 0
    )

    private fun dayCount(start: LocalDate, end: LocalDate): Int =
        ChronoUnit.DAYS.between(start, end).toInt() + 1

    private data class RecordNutrient(
        val knownSum: Double,
        val hasKnown: Boolean,
        val hasMissing: Boolean
    )
}

private fun MealSnapshotNutritionRead.asNullableMap(): Map<String, Double?> = linkedMapOf(
    NutritionProfile.CALORIES_KCAL to calories,
    NutritionProfile.PROTEIN_GRAMS to proteinGrams,
    NutritionProfile.CARBS_GRAMS to carbsGrams,
    NutritionProfile.FAT_GRAMS to fatGrams,
    NutritionProfile.SODIUM_MG to sodiumMg,
    NutritionProfile.SATURATED_FAT_GRAMS to saturatedFatGrams,
    NutritionProfile.SUGARS_GRAMS to sugarsGrams,
    NutritionProfile.FIBER_GRAMS to fiberGrams,
    NutritionProfile.ADDED_SUGARS_GRAMS to addedSugarsGrams,
    NutritionProfile.TRANS_FAT_GRAMS to transFatGrams,
    NutritionProfile.CHOLESTEROL_MG to cholesterolMg
).also { map -> map.putAll(micronutrients) }

private fun Map<String, Double?>.scaled(factor: Double): Map<String, Double?> =
    mapValues { (_, value) -> value?.let { it * factor } }

private fun AthleteNutritionGoal.toAnalysisTarget(): NutritionAnalysisTarget = NutritionAnalysisTarget(
    phase = phase,
    dailyValues = linkedMapOf(
        NutritionProfile.CALORIES_KCAL to caloriesKcal,
        NutritionProfile.PROTEIN_GRAMS to proteinGrams,
        NutritionProfile.CARBS_GRAMS to carbsGrams,
        NutritionProfile.FAT_GRAMS to fatGrams,
        NutritionProfile.FIBER_GRAMS to fiberGrams,
        NutritionProfile.SODIUM_MG to sodiumMg
    )
)
