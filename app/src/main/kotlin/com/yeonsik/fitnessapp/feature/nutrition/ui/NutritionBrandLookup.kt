package com.yeonsik.fitnessapp.feature.nutrition.ui

import com.yeonsik.fitnessapp.data.BrandReadV1

/** The editor owns requests, result selection and local registration. */
fun interface NutritionBrandLookup {
    fun search(query: String): List<BrandReadV1>
}

data class NutritionBrandSearchState(
    val query: String = "",
    val results: List<BrandReadV1> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null
)
