package com.yeonsik.fitnessapp.feature.home.model

/** Read-only values rendered by the home feature. */
data class HomeDayWorkoutMetrics(
    @JvmField val sessionCount: Int,
    @JvmField val totalSetCount: Int,
    @JvmField val totalVolumeKg: Double,
    @JvmField val totalDurationSeconds: Int
)

data class HomeBodyMetric(
    @JvmField val id: String,
    @JvmField val date: String,
    @JvmField val weightKg: Double,
    @JvmField val memo: String
)

data class HomeMealSummary(
    @JvmField val id: String,
    @JvmField val date: String,
    @JvmField val mealLabel: String,
    @JvmField val menu: String,
    @JvmField val calories: Int,
    @JvmField val proteinGrams: Double,
    @JvmField val carbsGrams: Double,
    @JvmField val fatGrams: Double,
    @JvmField val compositionCount: Int,
    @JvmField val previewTitle: String,
    @JvmField val mealTime: String,
    @JvmField val mealKind: String,
    @JvmField val fulfillmentMode: String?,
    @JvmField val storeName: String,
    @JvmField val branchName: String,
    @JvmField val menuName: String,
    @JvmField val nutritionStatus: String,
    @JvmField val macroRatio: String,
    @JvmField val macroRatioAccessibility: String,
    @JvmField val timeEditable: Boolean,
    @JvmField val createdAt: String?,
    private val subtitle: String,
    private val accessibilityLabel: String
) {
    fun previewSubtitle(): String = subtitle
    fun previewAccessibilityLabel(): String = accessibilityLabel
}

data class HomeNutritionGoal(
    @JvmField val phase: String,
    @JvmField val caloriesKcal: Double,
    @JvmField val proteinGrams: Double,
    @JvmField val carbsGrams: Double,
    @JvmField val fatGrams: Double,
    @JvmField val fiberGrams: Double,
    @JvmField val sodiumMg: Double,
    @JvmField val waterMl: Int
)

data class HomeNutritionTotal(
    private val sum: Double,
    private val known: Int,
    private val missing: Int
) {
    fun knownSum(): Double = sum
    fun knownCount(): Int = known
    fun missingCount(): Int = missing
    fun isComplete(): Boolean = missing == 0 && known > 0
    fun describedValue(): String {
        if (known == 0) return "?"
        if (isComplete()) return trim(sum)
        return "≥" + trim(sum) + " (미상 " + missing + "건)"
    }

    private fun trim(value: Double): String =
        if (value == java.lang.Math.rint(value)) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", value)
}

class HomeNutritionTotals(
    @JvmField val itemCount: Int,
    totals: Map<String, HomeNutritionTotal>
) {
    private val totalsByKey = totals.toMap()

    fun itemCount(): Int = itemCount

    fun total(key: String?): HomeNutritionTotal =
        totalsByKey[key?.trim()?.lowercase() ?: ""] ?: HomeNutritionTotal(0.0, 0, itemCount)
}
