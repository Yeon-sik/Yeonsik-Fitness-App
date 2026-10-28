package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionProfile

data class NutritionFoodInput @JvmOverloads constructor(
    val name: String,
    val brand: String?,
    val basisAmount: Double,
    val basisUnit: String,
    val profile: NutritionProfile,
    val sourceType: String,
    val registrationKind: NutritionRegistrationKind = NutritionRegistrationKind.INGREDIENT,
    val packageAmount: Double? = null,
    val packageUnit: String? = null,
    val priceTraceBrand: PriceTraceBrandSelection? = null
) {
    val priceTraceBrandId: String? get() = priceTraceBrand?.brandId

    /** A direct registration may link only the canonical text the user selected. */
    fun validatedBrand(): String? {
        val text = brand?.trim()?.takeIf(String::isNotEmpty)
        require(priceTraceBrand == null || text == priceTraceBrand.canonicalName) {
            "연결한 PriceTrace 브랜드의 이름을 유지하거나 연결을 해제하세요."
        }
        return text
    }
}
