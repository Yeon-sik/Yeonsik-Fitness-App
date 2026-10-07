package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionUnit

/** The label's nutrition basis is independent of the amount eaten in a Meal record. */
data class ManualFoodDraft(
    val name: String = "",
    val brand: String = "",
    val basisAmount: String = "100",
    val basisUnit: String = NutritionUnit.GRAM,
    val calories: String = "",
    val carbs: String = "",
    val protein: String = "",
    val fat: String = "",
    val sodium: String = "",
    val sugars: String = "",
    val saturatedFat: String = ""
) {
    internal fun validated(): ManualFoodInput {
        require(name.isNotBlank()) { "식품명을 입력하세요." }
        val amount = number(basisAmount, "기준량")
        require(amount > 0) { "기준량을 0보다 크게 입력하세요." }
        val profile = NutritionProfile.builder()
            .value(NutritionProfile.CALORIES_KCAL, number(calories, "칼로리"))
            .value(NutritionProfile.CARBS_GRAMS, number(carbs, "탄수화물"))
            .value(NutritionProfile.PROTEIN_GRAMS, number(protein, "단백질"))
            .value(NutritionProfile.FAT_GRAMS, number(fat, "지방"))
            .value(NutritionProfile.SODIUM_MG, optional(sodium, "나트륨"))
            .value(NutritionProfile.SUGARS_GRAMS, optional(sugars, "당류"))
            .value(NutritionProfile.SATURATED_FAT_GRAMS, optional(saturatedFat, "포화지방"))
            .build()
        return ManualFoodInput(name.trim(), brand.trim().ifEmpty { null }, amount,
            NutritionUnit.requireSupported(basisUnit), profile)
    }

    private fun optional(value: String, label: String) =
        if (value.isBlank()) null else number(value, label)

    private fun number(value: String, label: String): Double {
        val parsed = value.trim().toDoubleOrNull()
        require(parsed != null && parsed.isFinite() && parsed >= 0) {
            "${label}에 0 이상 숫자를 입력하세요."
        }
        return parsed
    }
}

internal data class ManualFoodInput(
    val name: String,
    val brand: String?,
    val basisAmount: Double,
    val basisUnit: String,
    val profile: NutritionProfile
)
