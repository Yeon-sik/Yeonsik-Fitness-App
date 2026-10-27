package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodInput
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionConsistencyPolicy

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
    val saturatedFat: String = "",
    val registrationKind: NutritionRegistrationKind = NutritionRegistrationKind.INGREDIENT,
    val packageAmount: String = "",
    val packageUnit: String = NutritionUnit.GRAM,
    val transFat: String = "",
    val priceTraceBrand: PriceTraceBrandSelection? = null
) {
    internal fun validated(): NutritionFoodInput {
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
            .value(NutritionProfile.TRANS_FAT_GRAMS, optional(transFat, "트랜스지방"))
            .build()
        NutritionConsistencyPolicy.validate(profile)
        val kind = registrationKind
        val packAmount = if (kind == NutritionRegistrationKind.PRODUCT) number(packageAmount, "제품 1개 용량")
            .also { require(it > 0) { "제품 1개 용량을 0보다 크게 입력하세요." } } else null
        val packUnit = if (packAmount != null) NutritionUnit.requireSupported(packageUnit) else null
        require(packUnit == null || NutritionUnit.areCompatible(packUnit, NutritionUnit.requireSupported(basisUnit))) {
            "제품 용량 단위가 기준량과 호환되지 않습니다. g와 ml는 서로 변환할 수 없습니다."
        }
        val selectedBrand = priceTraceBrand.takeIf { kind == NutritionRegistrationKind.PRODUCT }
        return NutritionFoodInput(name.trim(), selectedBrand?.canonicalName ?: brand.trim().ifEmpty { null }, amount,
            NutritionUnit.requireSupported(basisUnit), profile, "manual", kind, packAmount, packUnit, selectedBrand)
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
