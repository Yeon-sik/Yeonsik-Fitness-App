package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.ProductNutritionLink
import com.yeonsik.fitnessapp.data.ProductReadV1
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRecipeComponent
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodInput
import com.yeonsik.fitnessapp.feature.nutrition.model.IngredientCatalogBatch
import com.yeonsik.fitnessapp.feature.nutrition.model.IngredientImportPreview

/** Local-first nutrition catalog reads and explicit owner-scoped writes. */
interface NutritionCatalogRepositoryApi {
    /** Nutrition identity remains independent from the Fitness intake owner. */
    fun currentOwnerId(): String = "local-user"

    /** Registers a private catalog food without creating an intake record. */
    fun registerFood(ownerId: String, input: NutritionFoodInput): NutritionFood =
        throw UnsupportedOperationException("식품 등록을 지원하지 않습니다.")

    fun searchFoods(query: String): List<NutritionFood>
    fun findFoodById(foodId: String): NutritionFood?
    /** Nutrition and Fitness may use different accounts; writes capture the Nutrition owner. */
    fun activeOwnerId(): String? = null
    /** Explicit owner/visibility check for remote proposals; unknown implementations fail closed. */
    fun isPrivateDiningOutMenu(foodId: String, ownerId: String): Boolean = false

    /** Searches reusable packaged products without exposing storage rows to Meal UI. */
    fun searchPackagedFoods(query: String, limit: Int): List<NutritionFood> = emptyList()

    fun canEditPriceTraceBrand(foodId: String, ownerId: String): Boolean = false

    /** null은 명시적인 연결 해제다. 일반 영양 수정은 이 API를 호출하지 않는다. */
    fun setPriceTraceBrand(foodId: String, ownerId: String,
        selection: com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection?): NutritionFood =
        throw UnsupportedOperationException("브랜드 연결 변경을 지원하지 않습니다.")

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

    /** Existing reusable components; ordinary food search intentionally excludes these rows. */
    fun savedDiningOutComponents(storeName: String?, identity: DiningOutIdentity?, query: String?, limit: Int): List<NutritionFood> = emptyList()

    fun diningOutComponentsForMenu(menuFoodId: String?, groupType: String?): List<NutritionFood> = emptyList()

    /** Saves a private reusable food, preserving unknown optional nutrients as null. */
    fun saveManualFood(
        scope: AccountScope,
        name: String,
        brand: String?,
        basisAmount: Double,
        basisUnit: String,
        profile: NutritionProfile
    ): NutritionFood? = null

    fun saveManualFoodInput(scope: AccountScope, input: NutritionFoodInput): NutritionFood? =
        saveManualFood(scope, input.name, input.brand, input.basisAmount, input.basisUnit, input.profile)

    /** Saves the user's selected PriceTrace identity and nutrition together, without creating a Meal. */
    fun savePriceTraceProductInput(scope: AccountScope, input: NutritionFoodInput, product: ProductReadV1): NutritionFood? =
        throw UnsupportedOperationException("PriceTrace 제품 연결을 지원하지 않습니다.")

    fun saveRecipe(ownerId: String, name: String, items: List<com.yeonsik.fitnessapp.data.MealCompositionItem>,
        servings: Double, cookedWeightGrams: Double?): NutritionFood =
        throw UnsupportedOperationException("내 요리 저장을 지원하지 않습니다.")

    fun previewIngredientImport(ownerId: String, batch: IngredientCatalogBatch): IngredientImportPreview =
        throw UnsupportedOperationException("재료 파일 가져오기를 지원하지 않습니다.")

    fun applyIngredientImport(preview: IngredientImportPreview): IngredientImportPreview =
        throw UnsupportedOperationException("재료 파일 가져오기를 지원하지 않습니다.")

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

    /** Extended menu input; implementations must preserve a supplied trans-fat value. */
    fun saveDiningOutMenuWithNutrition(
        storeName: String, menuName: String, calories: Int?, proteinGrams: Double?,
        carbsGrams: Double?, fatGrams: Double?, sodiumMg: Double?, sugarsGrams: Double?,
        saturatedFatGrams: Double?, branchName: String?, identity: DiningOutIdentity?, transFatGrams: Double?
    ): NutritionFood? {
        require(transFatGrams == null) { "트랜스지방을 포함한 메뉴 저장을 지원하지 않습니다." }
        return saveDiningOutMenuWithNutrition(storeName, menuName, calories, proteinGrams,
            carbsGrams, fatGrams, sodiumMg, sugarsGrams, saturatedFatGrams, branchName, identity)
    }

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
