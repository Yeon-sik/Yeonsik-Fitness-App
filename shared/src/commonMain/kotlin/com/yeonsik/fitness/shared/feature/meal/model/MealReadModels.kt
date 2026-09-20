package com.yeonsik.fitness.shared.feature.meal.model

data class MealReadNutritionTotal(
    val sum: Double,
    val known: Int,
    val missing: Int
)

data class MealReadNutritionTotals(
    val itemCount: Int,
    val totals: Map<String, MealReadNutritionTotal>
)

data class MealReadSummary(
    val id: String,
    val date: String,
    val mealLabel: String,
    val menu: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val compositionCount: Int,
    val previewTitle: String,
    val mealTime: String,
    val mealKind: String,
    val fulfillmentMode: String?,
    val storeName: String,
    val branchName: String,
    val menuName: String,
    val nutritionStatus: String,
    val macroRatio: String,
    val macroRatioAccessibility: String,
    val timeEditable: Boolean,
    val createdAt: String?,
    val subtitle: String,
    val accessibilityLabel: String
)

data class MealNutritionReadSummary(
    val proteinGrams: Double,
    val recordedDays: Int,
    val mealCount: Int,
    val estimatedMealCount: Int
)

/** Immutable read projection rebuilt only from Meal-owned intake snapshot rows. */
data class MealSnapshotRead(
    val id: String,
    val date: String,
    val mealKind: String,
    val metadata: String,
    val items: List<MealSnapshotItemRead>,
    /** Record-level fallback for legacy rows without item snapshots. */
    val nutrition: MealSnapshotNutritionRead = MealSnapshotNutritionRead(
        calories = null,
        proteinGrams = null,
        carbsGrams = null,
        fatGrams = null,
        sodiumMg = null,
        saturatedFatGrams = null,
        sugarsGrams = null,
        fiberGrams = null,
        addedSugarsGrams = null,
        transFatGrams = null,
        cholesterolMg = null,
        micronutrients = emptyMap()
    )
)

data class MealSnapshotItemRead(
    val id: String,
    val foodId: String?,
    val foodName: String,
    val brand: String?,
    val manufacturerName: String?,
    val brandName: String?,
    val subBrandName: String?,
    val productName: String?,
    val packageAmount: Double?,
    val packageUnit: String?,
    val packageCount: Long?,
    val foodKind: String?,
    val quantity: Double,
    val unit: String,
    val basisAmount: Double?,
    val basisUnit: String?,
    val prepState: String?,
    val sourceType: String?,
    val sourceReference: String?,
    val sourceVersion: String?,
    val foodDataVersion: Long?,
    val templateId: String?,
    val templateRevision: Long?,
    val portionBasis: String?,
    val nominalServings: Double?,
    val nutrition: MealSnapshotNutritionRead,
    val components: List<MealSnapshotComponentRead>,
    val consumption: MealSnapshotConsumptionRead?
)

data class MealSnapshotComponentRead(
    val id: String,
    val foodId: String?,
    val foodName: String,
    val brand: String?,
    val foodKind: String?,
    val quantity: Double,
    val unit: String,
    val basisAmount: Double?,
    val basisUnit: String?,
    val prepState: String?,
    val groupKey: String?,
    val groupType: String?,
    val provisionType: String?,
    val role: String?,
    val memberId: String?,
    val consumedFraction: Double?,
    val sourceType: String?,
    val sourceReference: String?,
    val sourceVersion: String?,
    val foodDataVersion: Long?,
    val nutrition: MealSnapshotNutritionRead
)

data class MealSnapshotConsumptionRead(
    val dinerCount: Long,
    val consumedFraction: Double,
    val shareMethod: String,
    val confidence: String
)

data class MealSnapshotNutritionRead(
    val calories: Double?,
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
    val micronutrients: Map<String, Double>
)
