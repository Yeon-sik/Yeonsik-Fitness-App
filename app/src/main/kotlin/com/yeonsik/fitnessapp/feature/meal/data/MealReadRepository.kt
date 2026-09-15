package com.yeonsik.fitnessapp.feature.meal.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.MealRoomDao
import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.MealRecordKind
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotal
import com.yeonsik.fitnessapp.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.model.MealReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotComponentRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotConsumptionRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotItemRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotNutritionRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotRead
import org.json.JSONObject

/** Meal-owned read adapter for the Home projection. */
class MealReadRepository(roomDatabase: FitnessRoomDatabase) : MealReadApi {
    private val mealDao: MealRoomDao = roomDatabase.mealRoomDao()

    override fun mealCount(scope: AccountScope, date: String): Int =
        mealDao.mealCountForDate(scope.ownerId, date).toInt()

    override fun mealTotals(scope: AccountScope, date: String): MealReadNutritionTotals {
        val sums = linkedMapOf(
            "calories_kcal" to DoubleArray(3),
            "protein_grams" to DoubleArray(3),
            "carbs_grams" to DoubleArray(3),
            "fat_grams" to DoubleArray(3)
        )
        val rows = mealDao.visibleMealReadRows(scope.ownerId, date)
        rows.forEach { row ->
            accumulate(sums.getValue("calories_kcal"), row.calories.toDouble(), false)
            accumulate(sums.getValue("protein_grams"), row.proteinGrams, false)
            accumulate(sums.getValue("carbs_grams"), row.carbsGrams, true)
            accumulate(sums.getValue("fat_grams"), row.fatGrams, true)
        }
        return MealReadNutritionTotals(
            rows.size,
            sums.mapValues { (_, value) ->
                MealReadNutritionTotal(value[0], value[1].toInt(), value[2].toInt())
            }
        )
    }

    override fun meals(scope: AccountScope, date: String): List<MealReadSummary> =
        mealDao.visibleMealReadRows(scope.ownerId, date)
            .mapIndexed { index, row -> row.toReadSummary(index) }
            .sortedWith(compareBy<MealReadSummary> { it.mealTime == "시간 미기록" }.thenBy { it.mealTime })

    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
        mealDao.visibleMealRecordedDays(scope.ownerId, startDate, endDate)

    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> =
        mealDao.visibleMealDates(scope.ownerId, startDate, endDate)

    override fun nutritionSummary(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): MealNutritionReadSummary {
        val rows = mealDao.visibleMealReadRowsBetween(scope.ownerId, startDate, endDate)
        val protein = rows.sumOf { it.proteinGrams ?: 0.0 }
        val recordedDays = rows.map { it.date }.toSet().size
        val estimatedMealCount = rows.count { row ->
            val metadata = row.metadata
            metadata.contains("\"estimated\":true") ||
                metadata.contains("\"nutrition_status\":\"estimated\"")
        }
        return MealNutritionReadSummary(protein, recordedDays, rows.size, estimatedMealCount)
    }

    override fun mealSnapshot(scope: AccountScope, recordId: String): MealSnapshotRead? {
        val normalizedId = recordId.trim()
        if (normalizedId.isEmpty()) return null
        val record = mealDao.visibleMealRecord(normalizedId, scope.ownerId) ?: return null
        val items = mealDao.visibleMealItems(normalizedId, scope.ownerId)
        val itemNutrients = mealDao.visibleMealItemNutrients(normalizedId, scope.ownerId)
            .groupBy { it.mealRecordItemId }
        val components = mealDao.visibleMealComponents(normalizedId, scope.ownerId)
            .groupBy { it.mealRecordItemId }
        val componentNutrients = mealDao.visibleMealComponentNutrients(normalizedId, scope.ownerId)
            .groupBy { it.mealRecordItemComponentId }
        val consumptions = mealDao.visibleMealConsumptions(normalizedId, scope.ownerId)
            .groupBy { it.mealRecordItemId }

        return MealSnapshotRead(
            record.id,
            record.date,
            record.mealKind,
            record.metadata,
            items.map { item ->
                MealSnapshotItemRead(
                    id = item.id,
                    foodId = item.foodId,
                    foodName = item.foodNameSnapshot,
                    brand = item.brandSnapshot,
                    manufacturerName = item.manufacturerNameSnapshot,
                    brandName = item.brandNameSnapshot,
                    subBrandName = item.subBrandNameSnapshot,
                    productName = item.productNameSnapshot,
                    packageAmount = item.packageAmountSnapshot,
                    packageUnit = item.packageUnitSnapshot,
                    packageCount = item.packageCountSnapshot,
                    foodKind = item.foodKindSnapshot,
                    quantity = item.quantity,
                    unit = item.unit,
                    basisAmount = item.basisAmountSnapshot,
                    basisUnit = item.basisUnitSnapshot,
                    prepState = item.prepStateSnapshot,
                    sourceType = item.sourceTypeSnapshot,
                    sourceReference = item.sourceReferenceSnapshot,
                    sourceVersion = item.sourceVersionSnapshot,
                    foodDataVersion = item.foodDataVersionSnapshot,
                    templateId = item.compositionTemplateId,
                    templateRevision = item.compositionTemplateRevisionSnapshot,
                    portionBasis = item.portionBasisSnapshot,
                    nominalServings = item.nominalServingsSnapshot,
                    nutrition = itemNutrition(item, itemNutrients[item.id].orEmpty()),
                    components = components[item.id].orEmpty().map { component ->
                        MealSnapshotComponentRead(
                            id = component.id,
                            foodId = component.foodId,
                            foodName = component.foodNameSnapshot,
                            brand = component.brandSnapshot,
                            foodKind = component.foodKindSnapshot,
                            quantity = component.quantity,
                            unit = component.unit,
                            basisAmount = component.basisAmountSnapshot,
                            basisUnit = component.basisUnitSnapshot,
                            prepState = component.prepStateSnapshot,
                            groupKey = component.compositionGroupKeySnapshot,
                            groupType = component.compositionGroupTypeSnapshot,
                            provisionType = component.provisionTypeSnapshot,
                            role = component.compositionRoleSnapshot,
                            memberId = component.compositionMemberIdSnapshot,
                            consumedFraction = component.consumedFraction,
                            sourceType = component.sourceTypeSnapshot,
                            sourceReference = component.sourceReferenceSnapshot,
                            sourceVersion = component.sourceVersionSnapshot,
                            foodDataVersion = component.foodDataVersionSnapshot,
                            nutrition = componentNutrition(
                                component,
                                componentNutrients[component.id].orEmpty()
                            )
                        )
                    },
                    consumption = consumptions[item.id].orEmpty().firstOrNull()?.let { consumption ->
                        MealSnapshotConsumptionRead(
                            dinerCount = consumption.dinerCount,
                            consumedFraction = consumption.consumedFraction,
                            shareMethod = consumption.shareMethod,
                            confidence = consumption.confidence
                        )
                    }
                )
            }
        )
    }

    private fun itemNutrition(
        item: com.yeonsik.fitnessapp.core.database.MealRecordItemsRoomEntity,
        nutrients: List<com.yeonsik.fitnessapp.core.database.MealRecordItemNutrientsRoomEntity>
    ): MealSnapshotNutritionRead = MealSnapshotNutritionRead(
        calories = item.calories,
        proteinGrams = item.proteinGrams,
        carbsGrams = item.carbsGrams,
        fatGrams = item.fatGrams,
        sodiumMg = item.sodiumMg,
        saturatedFatGrams = item.saturatedFatGrams,
        sugarsGrams = item.sugarsGrams,
        fiberGrams = item.fiberGrams,
        addedSugarsGrams = item.addedSugarsGrams,
        transFatGrams = item.transFatGrams,
        cholesterolMg = item.cholesterolMg,
        micronutrients = nutrients.associate { it.nutrientCode to it.amount }
    )

    private fun componentNutrition(
        component: com.yeonsik.fitnessapp.core.database.MealRecordItemComponentsRoomEntity,
        nutrients: List<com.yeonsik.fitnessapp.core.database.MealRecordItemComponentNutrientsRoomEntity>
    ): MealSnapshotNutritionRead = MealSnapshotNutritionRead(
        calories = component.calories,
        proteinGrams = component.proteinGrams,
        carbsGrams = component.carbsGrams,
        fatGrams = component.fatGrams,
        sodiumMg = component.sodiumMg,
        saturatedFatGrams = component.saturatedFatGrams,
        sugarsGrams = component.sugarsGrams,
        fiberGrams = component.fiberGrams,
        addedSugarsGrams = component.addedSugarsGrams,
        transFatGrams = component.transFatGrams,
        cholesterolMg = component.cholesterolMg,
        micronutrients = nutrients.associate { it.nutrientCode to it.amount }
    )

    private fun MealRoomDao.MealReadRow.toReadSummary(index: Int): MealReadSummary {
        val mealKind = MealRecordKind.normalize(firstNonBlank(mealKind, metadataValue(metadata, "meal_kind")))
        val store = firstNonBlank(storeName, metadataValue(metadata, "store_name"))
        val rawBranch = firstNonBlank(branchName, metadataValue(metadata, "branch_name"))
        val branch = if (MealRecordKind.isDiningOut(mealKind)) {
            MealEntryPolicy.resolveDiningOutBranchName(store, rawBranch)
        } else {
            rawBranch
        }
        val resolvedMenuName = firstNonBlank(menuName, metadataValue(metadata, "menu_name"))
        val eatenAt = metadataValue(metadata, "eaten_at")
        val previewTitle = if (MealRecordKind.isDiningOut(mealKind)) {
            MealEntryPolicy.previewDiningOutTitle(store, branch, resolvedMenuName)
        } else {
            MealEntryPolicy.previewTitle(firstFoodName, compositionCount, menu)
        }
        val nutritionStatus = firstNonBlank(
            metadataValue(metadata, "nutrition_status"),
            if (MealRecordKind.isDiningOut(mealKind)) "unknown" else "recorded"
        )
        val mealTime = MealEntryPolicy.displayMealTime(eatenAt)
        val ratio = MealEntryPolicy.macroRatioLabel(carbsGrams, proteinGrams, fatGrams)
        val ratioAccessibility = MealEntryPolicy.macroRatioAccessibilityLabel(
            carbsGrams, proteinGrams, fatGrams
        )
        return MealReadSummary(
            id,
            date,
            MealEntryPolicy.labelForIndex(index),
            menu,
            calories.toInt(),
            proteinGrams ?: 0.0,
            carbsGrams ?: 0.0,
            fatGrams ?: 0.0,
            compositionCount,
            previewTitle,
            mealTime,
            mealKind,
            fulfillmentMode,
            store,
            branch,
            resolvedMenuName,
            nutritionStatus,
            ratio,
            ratioAccessibility,
            deviceId == "android-local",
            createdAt,
            if (MealRecordKind.isDiningOut(mealKind)) "$mealTime · 외식" else "$mealTime · $ratio",
            "$previewTitle, $mealTime, $ratioAccessibility"
        )
    }

    private fun accumulate(target: DoubleArray, value: Double?, nullable: Boolean) {
        if (nullable && value == null) {
            target[2]++
        } else {
            target[0] += value ?: 0.0
            target[1]++
        }
    }

    private fun metadataValue(metadata: String?, key: String): String = try {
        JSONObject(metadata ?: "{}").optString(key, "")
    } catch (_: Exception) {
        ""
    }

    private fun firstNonBlank(first: String?, second: String?): String =
        first?.takeIf { it.isNotBlank() } ?: second.orEmpty()
}
