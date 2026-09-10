package com.yeonsik.fitnessapp.feature.nutrition.api

/**
 * Write boundary used by local backup restore for Nutrition-owned catalog maintenance.
 *
 * <p>The backup integration must not reach the Nutrition DAO or repository implementation
 * directly. Production implementations reconcile through the Nutrition Room DAO.</p>
 */
interface NutritionCatalogBackupApi {
    fun reconcileVerifiedFoodCatalog()
}
