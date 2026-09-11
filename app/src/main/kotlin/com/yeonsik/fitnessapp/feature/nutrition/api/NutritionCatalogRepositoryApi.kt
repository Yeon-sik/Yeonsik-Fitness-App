package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.ProductReadV1

/** Read port for the local-first nutrition catalog. */
interface NutritionCatalogRepositoryApi {
    fun searchFoods(query: String): List<NutritionFood>
    fun findFoodById(foodId: String): NutritionFood?

    /** Searches reusable packaged products without exposing storage rows to Meal UI. */
    fun searchPackagedFoods(query: String, limit: Int): List<NutritionFood> = emptyList()

    /** Returns the current owner's reusable dining-out menu masters. */
    fun savedDiningOutMenus(): List<NutritionFood> = emptyList()

    /** Explicitly saves a reusable Nutrition dining-out master; it never creates a Meal record. */
    fun saveDiningOutMenuWithNutrition(
        storeName: String,
        menuName: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        sodiumMg: Double?,
        sugarsGrams: Double?,
        saturatedFatGrams: Double?,
        branchName: String?,
        identity: DiningOutIdentity?
    ): NutritionFood? = null

    /** Changes the repository's captured local owner for subsequent local reads/writes. */
    fun setUserId(userId: String) {}

    /** Stores exact PriceTrace product snapshots in the Nutrition-owned local cache. */
    fun cachePriceTraceProducts(products: List<ProductReadV1>) {}

    /** Applies the authoritative publication state returned by the Nutrition RPC. */
    fun applyPublicationVisibility(
        foodId: String,
        ownerId: String,
        visibility: String,
        updatedAt: String
    ): Boolean = false

    /** Applies the authoritative PriceTrace identity returned by the Nutrition RPC. */
    fun applySourceReference(
        foodId: String,
        ownerId: String,
        sourceReference: String,
        updatedAt: String
    ): Boolean = false
}
