package com.yeonsik.fitnessapp.feature.meal.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.model.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

/** Repository-internal snapshot restoration and replacement; never reads the live food catalog. */
class MealRecordEditingStorage(private val database: FitnessRoomDatabase) {
    private val dao = database.mealRoomDao()

    fun requireTarget(scope: AccountScope, request: MealRecordEditRequest): MealRecordsRoomEntity {
        val record = dao.visibleMealRecord(request.recordId, scope.ownerId)
            ?.takeIf { it.deviceId == "android-local" } ?: error("수정할 식단 기록을 찾지 못했습니다.")
        check(record.updatedAt == request.expectedUpdatedAt) { "식단 기록이 변경되었습니다. 다시 열어 수정하세요." }
        return record
    }

    fun load(scope: AccountScope, recordId: String): MealRecordEditSnapshot? = database.runInTransaction<MealRecordEditSnapshot?> {
        val record = dao.visibleMealRecord(recordId, scope.ownerId)
            ?.takeIf { it.deviceId == "android-local" } ?: return@runInTransaction null
        if (PhotoEstimateMeal.isPhotoEstimate(record.metadata)) {
            return@runInTransaction MealRecordEditSnapshot(record.id, record.date, record.menu,
                MealEntryPolicy.displayMealTime(json(record.metadata).optString("eaten_at")), record.updatedAt,
                emptyList(), PhotoEstimateMealInput(record.calories.toDouble(),
                    PhotoEstimateMeal.recordedProtein(record.metadata, record.proteinGrams), record.carbsGrams, record.fatGrams))
        }
        val nutrients = dao.visibleMealItemNutrients(recordId, scope.ownerId).groupBy { it.mealRecordItemId }
        val consumptions = dao.visibleMealConsumptions(recordId, scope.ownerId).associateBy { it.mealRecordItemId }
        val rows = dao.visibleMealItems(recordId, scope.ownerId)
        val items = rows.map { row ->
            require(row.quantity.isFinite() && row.quantity > 0) { "기록한 음식의 섭취량을 확인할 수 없습니다." }
            val dining = row.foodKindSnapshot == NutritionFood.KIND_EXTERNAL_MENU ||
                row.foodKindSnapshot == null && MealRecordKind.isDiningOut(record.mealKind)
            val unit = if (dining) NutritionUnit.SERVING else row.basisUnitSnapshot ?: row.unit
            val quantity = if (dining) row.quantity else NutritionUnit.convert(row.quantity, row.unit, unit)
            val basis = if (dining) 1.0 else row.basisAmountSnapshot?.takeIf { it > 0 } ?: quantity
            val food = NutritionFood.builder().id("meal-record-item:${row.id}").ownerId(scope.ownerId)
                .name(row.foodNameSnapshot).brand(row.brandSnapshot)
                .manufacturerName(row.manufacturerNameSnapshot).brandName(row.brandNameSnapshot)
                .subBrandName(row.subBrandNameSnapshot).productName(row.productNameSnapshot)
                .packageAmount(row.packageAmountSnapshot).packageUnit(row.packageUnitSnapshot)
                .packageCount(row.packageCountSnapshot?.toInt())
                .kind(if (dining) NutritionFood.KIND_EXTERNAL_MENU else row.foodKindSnapshot)
                .basis(basis, unit).prepState(row.prepStateSnapshot)
                .profile(profile(row, nutrients[row.id].orEmpty()).scaled(basis / quantity))
                .source(row.sourceTypeSnapshot, row.sourceReferenceSnapshot).sourceVersion(row.sourceVersionSnapshot)
                .dataVersion(row.foodDataVersionSnapshot?.toInt() ?: NutritionFood.DATA_VERSION_MACROS_ONLY).build()
            MealRecordEditItem(row.id, food, quantity, consumptions[row.id]?.consumedFraction ?: 1.0, dining, row.foodId)
        }.ifEmpty {
            // Old summary-only records have no catalog identity or recoverable portion basis.
            val profile = NutritionProfile.builder().value("calories_kcal", record.calories.toDouble())
                .value("protein_grams", record.proteinGrams).value("carbs_grams", record.carbsGrams)
                .value("fat_grams", record.fatGrams).build()
            listOf(MealRecordEditItem(null, NutritionFood.builder().id("meal-record:${record.id}")
                .ownerId(scope.ownerId).name(record.menu).kind(NutritionFood.KIND_INGREDIENT)
                .basis(1.0, NutritionUnit.SERVING).profile(profile).source("legacy_meal_snapshot", null)
                .dataVersion(NutritionFood.DATA_VERSION_MACROS_ONLY).build(), 1.0))
        }
        val eatenAt = json(record.metadata).optString("eaten_at")
        val time = runCatching { OffsetDateTime.parse(eatenAt).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
        MealRecordEditSnapshot(record.id, record.date, record.menu, time, record.updatedAt, items)
    }

    /** Must run in the same transaction as insertion of the replacement items. */
    fun beginReplacement(scope: AccountScope, request: MealRecordEditRequest,
        generated: MealRecordsRoomEntity): Replacement {
        val record = requireTarget(scope, request)
        check(!PhotoEstimateMeal.isPhotoEstimate(record.metadata)) { "사진 추정 기록 전용 화면에서 수정하세요." }
        val replacement = Replacement(record, generated.updatedAt)
        val originalIds = (request.foodItemIds + request.menuItemIds).filterNotNull()
        require(originalIds.distinct().size == originalIds.size && originalIds.all { it in replacement.items }) {
            "수정할 음식 구성을 다시 확인하세요."
        }
        val metadata = json(record.metadata)
        val nextMetadata = json(generated.metadata)
        nextMetadata.keys().forEach { key -> metadata.put(key, nextMetadata.get(key)) }
        listOf("meal_type", "meal_label").forEach { key ->
            val label = json(record.metadata).optString(key)
            if (label.isNotBlank()) metadata.put(key, label)
        }
        metadata.put("meal_kind", generated.mealKind)
        listOf("diner_count", "consumed_fraction", "share_method", "confidence").forEach { metadata.remove(it) }
        if (generated.nutritionCalculationContract == null) metadata.remove("consumption_scope")
        val dining = MealRecordKind.isDiningOut(generated.mealKind)
        mapOf("store_name" to generated.storeName, "restaurant_name" to generated.storeName,
            "branch_name" to generated.branchName, "menu_name" to generated.menuName,
            "restaurant_id" to generated.restaurantId, "restaurant_location_id" to generated.restaurantLocationId,
            "restaurant_menu_id" to generated.restaurantMenuId, "catalog_product_id" to generated.catalogProductId)
            .forEach { (key, value) -> if (value == null) metadata.remove(key) else metadata.put(key, value) }
        if (dining) {
            metadata.put("estimated", true)
            metadata.put("composition_version", "3")
        }
        val sameComposition = originalIds.toSet() == replacement.items.keys
        if (!sameComposition) listOf("composition_template_id", "composition_template_revision", "option_count")
            .forEach { metadata.remove(it) }
        check(dao.updateMealRecord(generated.copy(
            id = record.id, date = record.date, createdAt = record.createdAt,
            isBackfilled = record.isBackfilled, backfilledAt = record.backfilledAt, backfillReason = record.backfillReason,
            fulfillmentMode = record.fulfillmentMode.takeIf { dining },
            compositionTemplateId = record.compositionTemplateId.takeIf { sameComposition },
            compositionTemplateRevision = record.compositionTemplateRevision.takeIf { sameComposition },
            sourceApp = record.sourceApp, scope = record.scope, metadata = metadata.toString()
        )) == 1)
        dao.tombstoneMealItems(record.id, record.userId, generated.updatedAt, generated.updatedAt)
        dao.tombstoneMealNutrients(record.id, record.userId, generated.updatedAt, generated.updatedAt)
        dao.tombstoneMealComponents(record.id, record.userId, generated.updatedAt, generated.updatedAt)
        dao.tombstoneMealComponentNutrients(record.id, record.userId, generated.updatedAt, generated.updatedAt)
        dao.tombstoneMealConsumptions(record.id, record.userId, generated.updatedAt, generated.updatedAt)
        return replacement
    }

    inner class Replacement internal constructor(private val record: MealRecordsRoomEntity, private val timestamp: String) {
        internal val items = dao.visibleMealItems(record.id, record.userId).associateBy { it.id }
        private val nutrients = dao.visibleMealItemNutrients(record.id, record.userId).groupBy { it.mealRecordItemId }
        private val components = dao.visibleMealComponents(record.id, record.userId).groupBy { it.mealRecordItemId }
        private val componentNutrients = dao.visibleMealComponentNutrients(record.id, record.userId).groupBy { it.mealRecordItemComponentId }
        private val consumptions = dao.visibleMealConsumptions(record.id, record.userId).associateBy { it.mealRecordItemId }

        fun preserve(originalId: String?, newId: String, dining: Boolean) {
            var row = dao.visibleMealItems(record.id, record.userId).first { it.id == newId }
            if (originalId == null) {
                require(row.foodId?.startsWith("meal-record-item:") != true) { "원래 음식의 연결을 확인할 수 없습니다. 식단을 다시 여세요." }
                if (row.foodId?.startsWith("meal-record:") == true) check(dao.updateMealItem(row.copy(foodId = null)) == 1)
                return
            }
            val original = items.getValue(originalId)
            val ratio = NutritionUnit.convert(row.quantity, row.unit, original.unit) / original.quantity
            val sameIdentity = row.foodNameSnapshot == original.foodNameSnapshot && row.brandSnapshot == original.brandSnapshot &&
                (!dining || listOf("branch_name", "restaurant_id", "restaurant_location_id", "restaurant_menu_id", "catalog_product_id")
                    .all { sourceValue(row.sourceReferenceSnapshot, it) == sourceValue(original.sourceReferenceSnapshot, it) })
            if (!dining) require(sameIdentity) { "원래 음식과 다른 항목입니다. 음식을 다시 추가하세요." }
            row = row.copy(foodId = if (sameIdentity) original.foodId else row.foodId,
                sourceTypeSnapshot = if (sameIdentity) original.sourceTypeSnapshot else row.sourceTypeSnapshot,
                sourceReferenceSnapshot = if (sameIdentity) original.sourceReferenceSnapshot else row.sourceReferenceSnapshot,
                sourceVersionSnapshot = if (sameIdentity) original.sourceVersionSnapshot else row.sourceVersionSnapshot,
                foodDataVersionSnapshot = if (sameIdentity) original.foodDataVersionSnapshot else row.foodDataVersionSnapshot,
                fiberGrams = if (dining && sameIdentity) original.fiberGrams?.times(ratio) else row.fiberGrams,
                addedSugarsGrams = if (dining && sameIdentity) original.addedSugarsGrams?.times(ratio) else row.addedSugarsGrams,
                cholesterolMg = if (dining && sameIdentity) original.cholesterolMg?.times(ratio) else row.cholesterolMg)
            val nextProfile = profile(row, emptyList())
            val sameProfile = profile(original, emptyList()).scaled(ratio).asMap().all { (key, value) ->
                val next = nextProfile.value(key)
                if (value == null || next == null) value == next else abs(value - next) <= 0.000001 * maxOf(1.0, abs(value))
            }
            if (sameIdentity && sameProfile) row = row.copy(
                compositionTemplateId = original.compositionTemplateId,
                compositionTemplateRevisionSnapshot = original.compositionTemplateRevisionSnapshot,
                portionBasisSnapshot = original.portionBasisSnapshot,
                nominalServingsSnapshot = original.nominalServingsSnapshot?.times(ratio))
            if (dining && sameIdentity && !sameProfile) row = row.copy(
                sourceTypeSnapshot = "manual_estimate", sourceVersionSnapshot = null)
            check(dao.updateMealItem(row) == 1)
            if (!sameIdentity) return
            val newCodes = dao.visibleMealItemNutrients(record.id, record.userId).filter { it.mealRecordItemId == newId }
                .map { it.nutrientCode }.toSet()
            nutrients[originalId].orEmpty().filter { it.nutrientCode !in newCodes }.forEach { nutrient ->
                dao.insertNutrient(nutrient.copy(id = UUID.randomUUID().toString(), mealRecordItemId = newId,
                    amount = nutrient.amount * ratio, createdAt = timestamp, updatedAt = timestamp, deletedAt = null,
                    deviceId = "android-local"))
            }
            if (sameProfile) components[originalId].orEmpty().forEach { component ->
                val id = UUID.randomUUID().toString()
                dao.insertComponent(component.copy(id = id, mealRecordItemId = newId, quantity = component.quantity * ratio,
                    calories = component.calories?.times(ratio), proteinGrams = component.proteinGrams?.times(ratio),
                    carbsGrams = component.carbsGrams?.times(ratio), fatGrams = component.fatGrams?.times(ratio),
                    sodiumMg = component.sodiumMg?.times(ratio), saturatedFatGrams = component.saturatedFatGrams?.times(ratio),
                    sugarsGrams = component.sugarsGrams?.times(ratio), fiberGrams = component.fiberGrams?.times(ratio),
                    addedSugarsGrams = component.addedSugarsGrams?.times(ratio), transFatGrams = component.transFatGrams?.times(ratio),
                    cholesterolMg = component.cholesterolMg?.times(ratio), createdAt = timestamp, updatedAt = timestamp, deletedAt = null,
                    deviceId = "android-local"))
                componentNutrients[component.id].orEmpty().forEach { nutrient ->
                    dao.insertComponentNutrient(nutrient.copy(id = UUID.randomUUID().toString(), mealRecordItemId = newId,
                        mealRecordItemComponentId = id, amount = nutrient.amount * ratio,
                        createdAt = timestamp, updatedAt = timestamp, deletedAt = null, deviceId = "android-local"))
                }
            }
            if (dining) consumptions[originalId]?.let { originalConsumption ->
                dao.visibleMealConsumptions(record.id, record.userId).firstOrNull { it.mealRecordItemId == newId }?.let { next ->
                    check(dao.updateMealConsumption(next.copy(consumerScope = originalConsumption.consumerScope,
                        dinerCount = originalConsumption.dinerCount, shareMethod = originalConsumption.shareMethod,
                        confidence = originalConsumption.confidence)) == 1)
                }
            }
        }
    }

    private fun profile(row: MealRecordItemsRoomEntity, nutrients: List<MealRecordItemNutrientsRoomEntity>): NutritionProfile {
        val builder = NutritionProfile.builder().value("calories_kcal", row.calories).value("protein_grams", row.proteinGrams)
            .value("carbs_grams", row.carbsGrams).value("fat_grams", row.fatGrams).value("sodium_mg", row.sodiumMg)
            .value("saturated_fat_grams", row.saturatedFatGrams).value("sugars_grams", row.sugarsGrams)
            .value("fiber_grams", row.fiberGrams).value("added_sugars_grams", row.addedSugarsGrams)
            .value("trans_fat_grams", row.transFatGrams).value("cholesterol_mg", row.cholesterolMg)
        nutrients.forEach { builder.value(it.nutrientCode, it.amount) }
        return builder.build()
    }

    private fun json(value: String?): JSONObject = runCatching { JSONObject(value ?: "{}") }.getOrElse { JSONObject() }
    private fun sourceValue(value: String?, key: String): String = json(value).optString(key).takeUnless { it == "null" }.orEmpty()
}
