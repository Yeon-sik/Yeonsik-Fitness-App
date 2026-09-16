package com.yeonsik.fitnessapp.feature.supplement.model

data class SupplementPlanDraft(
    val typeCode: String,
    val brandName: String,
    val productForm: String,
    val purposeCode: String,
    val servingAmount: Double,
    val servingUnit: String,
    val activeIngredientAmount: Double?,
    val activeIngredientUnit: String,
    val ingredientDetails: String,
    val timingLabels: List<String>,
    val instructions: String = ""
)

data class SupplementPlanSaveResult(
    val itemId: String,
    val scheduleId: String,
    val effectiveFrom: String,
    val startsTomorrow: Boolean
)

data class SupplementHistoryEntry(
    val id: String,
    val date: String,
    val status: String,
    val typeName: String,
    val brandName: String,
    val doseAmount: Double,
    val doseUnit: String,
    val timingLabel: String,
    val doseIndex: Int,
    val takenAt: String?,
    val recordSource: String,
    val createdAt: String,
    val activeIngredientAmount: Double?,
    val activeIngredientUnit: String,
    val ingredientDetails: String
)

data class SupplementEffectCheckin(
    val date: String,
    val effectScore: Int,
    val adverseEffects: String,
    val note: String
)
data class SupplementProgress(
    val planned: Int,
    val taken: Int,
    val skipped: Int
) {
    val unrecorded: Int get() = maxOf(0, planned - taken - skipped)
}

data class SupplementAdherence(
    val planned: Int,
    val taken: Int,
    val skipped: Int
) {
    val unrecorded: Int get() = maxOf(0, planned - taken - skipped)
    fun adherencePercent(): Int = if (planned == 0) 0 else kotlin.math.round(taken * 100f / planned).toInt()
}
