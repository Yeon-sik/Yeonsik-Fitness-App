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
