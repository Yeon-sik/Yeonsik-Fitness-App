package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.ProductNutritionLink
import com.yeonsik.fitnessapp.data.ProductReadV1
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRecipeComponent

/** Read port for the local-first nutrition catalog. */
interface NutritionCatalogRepositoryApi {
    fun searchFoods(query: String): List<NutritionFood>
    fun findFoodById(foodId: String): NutritionFood?

    /** Searches reusable packaged products without exposing storage rows to Meal UI. */
    fun searchPackagedFoods(query: String, limit: Int): List<NutritionFood> = emptyList()

    /** Searches the verified food catalog while preserving its source/provenance metadata. */
    fun searchVerifiedFoods(query: String, limit: Int): List<NutritionFood> = emptyList()

    /** Returns all package variants for one canonical finished product. */
    fun packagedFoodVariants(product: NutritionFood): List<NutritionFood> = emptyList()

    /** Returns saved recipe/catalog entries owned by the current nutrition account. */
    fun savedRecipes(): List<NutritionFood> = emptyList()

    /** Returns recipe components without exposing the concrete repository implementation. */
    fun recipeComponentModels(recipeId: String): List<NutritionRecipeComponent> = emptyList()

    /** Returns the stable local/PriceTrace identity used for package grouping. */
    fun canonicalPackagedProductKey(food: NutritionFood): String = "unresolved|product"

    /** Returns the current approved PriceTrace link, if one exists. */
    fun approvedProductLink(nutritionFoodId: String): ProductNutritionLink? = null

    /** Returns owner-scoped PriceTrace suggestions awaiting a decision. */
    fun pendingProductLinkSuggestions(nutritionFoodId: String): List<ProductNutritionLink> = emptyList()

    /** Links a selected exact PriceTrace product; name-only matching is never accepted. */
    fun linkProduct(
        nutritionFoodId: String,
        product: ProductReadV1
    ): ProductNutritionLink? = null

    /** Approves only the exact product represented by the selected suggestion. */
    fun approveProductSuggestion(
        suggestionId: String,
        product: ProductReadV1
    ): ProductNutritionLink? = null

    fun rejectProductSuggestion(suggestionId: String): Boolean = false

    /** Soft-unlinks PriceTrace metadata without changing the Nutrition food or meal snapshots. */
    fun unlinkProduct(nutritionFoodId: String): Boolean = false

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
