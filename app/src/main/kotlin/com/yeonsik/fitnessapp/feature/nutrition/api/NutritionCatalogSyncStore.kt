package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionCatalogSyncSnapshot
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionComponentSyncRow
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodSyncRow
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionNutrientSyncRow
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionProductLinkSyncRow

/** Typed local store used by the Nutrition network integration. */
interface NutritionCatalogSyncStore {
    fun exportSyncSnapshot(ownerId: String): NutritionCatalogSyncSnapshot

    fun applyRemoteFoodRows(rows: List<NutritionFoodSyncRow>): Int

    fun applyRemoteNutrientRows(rows: List<NutritionNutrientSyncRow>): Int

    fun applyRemoteComponentRows(rows: List<NutritionComponentSyncRow>): Int

    fun applyRemoteProductLinkRows(rows: List<NutritionProductLinkSyncRow>): Int
}
