package com.yeonsik.fitnessapp.feature.nutrition.model

data class NutritionFoodSyncRow(
    val id: String,
    val ownerId: String?,
    val name: String,
    val brand: String?,
    val manufacturerName: String?,
    val brandName: String?,
    val subBrandName: String?,
    val productName: String?,
    val packageAmount: Double?,
    val packageUnit: String?,
    val packageCount: Long?,
    val kind: String,
    val category: String,
    val basisAmount: Double,
    val basisUnit: String,
    val prepState: String,
    val cookingMethod: String,
    val caloriesKcal: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val sodiumMg: Double?,
    val saturatedFatGrams: Double?,
    val sugarsGrams: Double?,
    val fiberGrams: Double?,
    val addedSugarsGrams: Double?,
    val transFatGrams: Double?,
    val cholesterolMg: Double?,
    val sourceType: String,
    val sourceReference: String?,
    val sourceVersion: String?,
    val dataVersion: Int,
    val revision: Int,
    val visibility: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?
)

data class NutritionNutrientSyncRow(
    val id: String,
    val ownerId: String?,
    val foodId: String,
    val nutrientCode: String,
    val amount: Double?,
    val unit: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?
)

data class NutritionComponentSyncRow(
    val id: String,
    val ownerId: String?,
    val parentFoodId: String,
    val childFoodId: String,
    val quantity: Double,
    val unit: String,
    val orderIndex: Long,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?
)

data class NutritionProductLinkSyncRow(
    val id: String,
    val ownerId: String,
    val nutritionFoodId: String,
    val catalogProductId: String,
    val standardProductId: String?,
    val status: String,
    val sourceType: String,
    val proposalReference: String?,
    val productContractVersion: String?,
    val catalogProductRevision: String?,
    val catalogContentAmount: Double?,
    val catalogContentUnit: String?,
    val catalogPackageCount: Long?,
    val revision: Int,
    val reviewedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?
)

data class NutritionCatalogSyncSnapshot(
    val foods: List<NutritionFoodSyncRow>,
    val nutrients: List<NutritionNutrientSyncRow>,
    val components: List<NutritionComponentSyncRow>,
    val productLinks: List<NutritionProductLinkSyncRow>
)
