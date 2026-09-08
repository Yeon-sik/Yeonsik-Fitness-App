package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.data.NutritionFood

/** Read port for the local-first nutrition catalog. */
interface NutritionCatalogRepositoryApi {
    fun searchFoods(query: String): List<NutritionFood>
    fun findFoodById(foodId: String): NutritionFood?
}
