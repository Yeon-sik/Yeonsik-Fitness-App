package com.yeonsik.fitnessapp.feature.meal.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.MealRecordsRoomEntity
import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.MealRecordKind
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMeal
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMealInput
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToLong

/** Meal-owned aggregate; does not manufacture a reusable Nutrition or PriceTrace menu. */
class PhotoEstimateMealStorage(private val database: FitnessRoomDatabase) {
    private val dao = database.mealRoomDao()

    fun save(scope: AccountScope, date: String, time: String, input: PhotoEstimateMealInput,
        recordId: String?, expectedUpdatedAt: String?): String = database.runInTransaction<String> {
        val day = MealEntryPolicy.requireRecordDate(date, LocalDate.now())
        val eatenAt = MealEntryPolicy.eatenAt(day, time, ZoneId.systemDefault())
        val existing = recordId?.let { id ->
            val row = dao.visibleMealRecord(id, scope.ownerId)
            check(row != null && row.deviceId == "android-local" && PhotoEstimateMeal.isPhotoEstimate(row.metadata)) {
                "수정할 사진 추정 기록을 찾지 못했습니다."
            }
            check(row.updatedAt == expectedUpdatedAt && row.date == date) { "식단 기록이 변경되었습니다. 다시 열어 수정하세요." }
            row
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
        val metadata = JSONObject(existing?.metadata ?: "{}")
            .put("recording_method", PhotoEstimateMeal.METHOD)
            .put("nutrition_status", "estimated").put("estimated", true)
            .put("protein_known", input.proteinGrams != null)
            .put("eaten_at", eatenAt).put("meal_kind", MealRecordKind.FOOD)
        val title = PhotoEstimateMeal.title(date)
        val backfilled = MealEntryPolicy.isBackfilled(day, LocalDate.now())
        val record = existing?.copy(menu = title, calories = input.calories.roundToLong(),
            proteinGrams = input.proteinGrams ?: 0.0, carbsGrams = input.carbsGrams, fatGrams = input.fatGrams,
            metadata = metadata.toString(), updatedAt = now) ?: MealRecordsRoomEntity(
            id = UUID.randomUUID().toString(), userId = scope.ownerId, date = day.toString(), menu = title,
            mealKind = MealRecordKind.FOOD, fulfillmentMode = null, storeName = null, branchName = null,
            menuName = null, restaurantId = null, restaurantLocationId = null, restaurantMenuId = null,
            catalogProductId = null, compositionTemplateId = null, compositionTemplateRevision = null,
            nutritionCalculationContract = null, calories = input.calories.roundToLong(),
            proteinGrams = input.proteinGrams ?: 0.0, carbsGrams = input.carbsGrams, fatGrams = input.fatGrams,
            createdAt = now, isBackfilled = if (backfilled) 1 else 0, backfilledAt = now.takeIf { backfilled },
            backfillReason = "manual past meal entry".takeIf { backfilled }, updatedAt = now, deletedAt = null,
            deviceId = "android-local", sourceApp = "fitness", scope = "fitness", metadata = metadata.toString(),
            contractVersion = 1)
        if (existing == null) dao.insertRecord(record) else check(dao.updateMealRecord(record) == 1)
        record.id
    }

    fun move(scope: AccountScope, recordId: String, date: String, time: String): Boolean {
        val record = dao.visibleMealRecord(recordId, scope.ownerId) ?: return false
        check(record.deviceId == "android-local" && PhotoEstimateMeal.isPhotoEstimate(record.metadata))
        val day = MealEntryPolicy.requireRecordDate(date, LocalDate.now())
        val metadata = JSONObject(record.metadata).put("eaten_at", MealEntryPolicy.eatenAt(day, time, ZoneId.systemDefault()))
        return dao.updateMealRecord(record.copy(date = day.toString(), menu = PhotoEstimateMeal.title(date),
            metadata = metadata.toString(), updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString())) == 1
    }
}
