package com.yeonsik.fitnessapp.feature.home.data

import android.content.Context
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.MealRecordKind
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRoomStorage
import org.json.JSONObject

/** Read-only ports used by the home application read model. */
interface HomeReadSources {
    fun sessionsForDate(scope: AccountScope, date: String): List<String>
    fun dayMetrics(scope: AccountScope, date: String): HomeDayWorkoutMetrics
    fun latestInProgress(scope: AccountScope): String?
    fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String?
    fun mealCount(scope: AccountScope, date: String): Int
    fun mealTotals(scope: AccountScope, date: String): HomeNutritionTotals
    fun meals(scope: AccountScope, date: String): List<HomeMealSummary>
    fun bodyMetric(scope: AccountScope, date: String): HomeBodyMetric?
    fun bodyMetrics(scope: AccountScope, date: String): List<HomeBodyMetric>
    fun nutritionGoal(scope: AccountScope): HomeNutritionGoal?
}

/** Room-managed, feature-scoped read implementation; no compatibility repository calls. */
class RoomHomeReadSources(
    roomDatabase: FitnessRoomDatabase,
    context: Context
) : HomeReadSources {
    private val workout = WorkoutRoomStorage(roomDatabase, context)
    private val database = roomDatabase.openHelper.writableDatabase

    override fun sessionsForDate(scope: AccountScope, date: String): List<String> =
        workout.sessionsForDate(scope, date)

    override fun dayMetrics(scope: AccountScope, date: String): HomeDayWorkoutMetrics {
        val metrics = workout.dayMetrics(scope, date)
        return HomeDayWorkoutMetrics(
            metrics.sessionCount,
            metrics.totalSetCount,
            metrics.totalVolumeKg,
            metrics.totalDurationSeconds
        )
    }

    override fun latestInProgress(scope: AccountScope): String? = workout.latestInProgress(scope)

    override fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String? =
        workout.latestCompletedForRoutine(scope, routineId, routineName)

    override fun mealCount(scope: AccountScope, date: String): Int =
        database.query(
            "SELECT COUNT(*) FROM meal_records WHERE user_id = ? AND deleted_at IS NULL " +
                "AND scope IN ('fitness', 'both') AND date = ?",
            arrayOf(scope.ownerId, date)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    override fun mealTotals(scope: AccountScope, date: String): HomeNutritionTotals {
        val sums = linkedMapOf(
            "calories_kcal" to doubleArrayOf(0.0, 0.0, 0.0),
            "protein_grams" to doubleArrayOf(0.0, 0.0, 0.0),
            "carbs_grams" to doubleArrayOf(0.0, 0.0, 0.0),
            "fat_grams" to doubleArrayOf(0.0, 0.0, 0.0)
        )
        var itemCount = 0
        database.query(
            "SELECT calories, protein_grams, carbs_grams, fat_grams FROM meal_records " +
                "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both') AND date = ?",
            arrayOf(scope.ownerId, date)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                itemCount++
                accumulate(sums["calories_kcal"]!!, cursor, 0, false)
                accumulate(sums["protein_grams"]!!, cursor, 1, false)
                accumulate(sums["carbs_grams"]!!, cursor, 2, true)
                accumulate(sums["fat_grams"]!!, cursor, 3, true)
            }
        }
        return HomeNutritionTotals(
            itemCount,
            sums.mapValues { (_, value) -> HomeNutritionTotal(value[0], value[1].toInt(), value[2].toInt()) }
        )
    }

    override fun meals(scope: AccountScope, date: String): List<HomeMealSummary> {
        val result = mutableListOf<HomeMealSummary>()
        database.query(
            "SELECT r.id, r.date, r.menu, r.calories, r.protein_grams, r.carbs_grams, " +
                "r.fat_grams, r.metadata, r.created_at, r.meal_kind, r.fulfillment_mode, " +
                "r.store_name, r.branch_name, r.menu_name, " +
                "(SELECT i.food_name_snapshot FROM meal_record_items i WHERE i.meal_record_id = r.id " +
                "AND i.user_id = r.user_id AND i.deleted_at IS NULL ORDER BY i.order_index, i.id LIMIT 1), " +
                "(SELECT COUNT(*) FROM meal_record_items i WHERE i.meal_record_id = r.id " +
                "AND i.user_id = r.user_id AND i.deleted_at IS NULL), r.device_id " +
                "FROM meal_records r WHERE r.user_id = ? AND r.deleted_at IS NULL " +
                "AND r.scope IN ('fitness', 'both') AND r.date = ? ORDER BY r.created_at, r.id",
            arrayOf(scope.ownerId, date)
        ).use { cursor ->
            var index = 0
            while (cursor.moveToNext()) {
                val metadata = cursor.getString(7)
                val mealKind = MealRecordKind.normalize(
                    firstNonBlank(cursor.getString(9), metadataValue(metadata, "meal_kind"))
                )
                val store = firstNonBlank(cursor.getString(11), metadataValue(metadata, "store_name"))
                val rawBranch = firstNonBlank(cursor.getString(12), metadataValue(metadata, "branch_name"))
                val branch = if (MealRecordKind.isDiningOut(mealKind))
                    MealEntryPolicy.resolveDiningOutBranchName(store, rawBranch) else rawBranch
                val menuName = firstNonBlank(cursor.getString(13), metadataValue(metadata, "menu_name"))
                val count = cursor.getInt(15)
                val eatenAt = metadataValue(metadata, "eaten_at")
                val previewTitle = if (MealRecordKind.isDiningOut(mealKind))
                    MealEntryPolicy.previewDiningOutTitle(store, branch, menuName)
                else MealEntryPolicy.previewTitle(cursor.getString(14), count, cursor.getString(2))
                val nutritionStatus = firstNonBlank(
                    metadataValue(metadata, "nutrition_status"),
                    if (MealRecordKind.isDiningOut(mealKind)) "unknown" else "recorded"
                )
                val carbs = nullableDouble(cursor, 5)
                val protein = nullableDouble(cursor, 4)
                val fat = nullableDouble(cursor, 6)
                val mealTime = MealEntryPolicy.displayMealTime(eatenAt)
                val ratio = MealEntryPolicy.macroRatioLabel(carbs, protein, fat)
                result += HomeMealSummary(
                    cursor.getString(0), cursor.getString(1), MealEntryPolicy.labelForIndex(index++),
                    cursor.getString(2).orEmpty(), cursor.getInt(3), protein ?: 0.0, carbs ?: 0.0,
                    fat ?: 0.0, count, previewTitle, mealTime, mealKind,
                    cursor.getString(10), store, branch, menuName, nutritionStatus, ratio,
                    MealEntryPolicy.macroRatioAccessibilityLabel(carbs, protein, fat),
                    cursor.getString(16) == "android-local", cursor.getString(8),
                    if (MealRecordKind.isDiningOut(mealKind)) "$mealTime · 외식" else "$mealTime · $ratio",
                    "$previewTitle, $mealTime, ${MealEntryPolicy.macroRatioAccessibilityLabel(carbs, protein, fat)}"
                )
            }
        }
        return result.sortedWith(compareBy<HomeMealSummary> { it.mealTime == "시간 미기록" }.thenBy { it.mealTime })
    }

    override fun bodyMetric(scope: AccountScope, date: String): HomeBodyMetric? = bodyMetrics(scope, date).firstOrNull()

    override fun bodyMetrics(scope: AccountScope, date: String): List<HomeBodyMetric> {
        val result = mutableListOf<HomeBodyMetric>()
        database.query(
            "SELECT id, date, weight_kg, metadata FROM weight_records WHERE user_id = ? " +
                "AND deleted_at IS NULL AND scope IN ('fitness', 'both') AND date = ? " +
                "ORDER BY date DESC, updated_at DESC LIMIT 20",
            arrayOf(scope.ownerId, date)
        ).use { cursor ->
            while (cursor.moveToNext()) result += HomeBodyMetric(
                cursor.getString(0), cursor.getString(1), cursor.getDouble(2), metadataValue(cursor.getString(3), "memo")
            )
        }
        return result
    }

    override fun nutritionGoal(scope: AccountScope): HomeNutritionGoal? = database.query(
        "SELECT phase, calories_kcal, protein_grams, carbs_grams, fat_grams, fiber_grams, sodium_mg, water_ml " +
            "FROM nutrition_goals WHERE user_id = ? LIMIT 1",
        arrayOf(scope.ownerId)
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else HomeNutritionGoal(
            cursor.getString(0), cursor.getDouble(1), cursor.getDouble(2), cursor.getDouble(3),
            cursor.getDouble(4), cursor.getDouble(5), cursor.getDouble(6), cursor.getInt(7)
        )
    }

    private fun accumulate(target: DoubleArray, cursor: android.database.Cursor, index: Int, nullable: Boolean) {
        if (nullable && cursor.isNull(index)) target[2]++ else {
            target[0] += if (cursor.isNull(index)) 0.0 else cursor.getDouble(index)
            target[1]++
        }
    }

    private fun nullableDouble(cursor: android.database.Cursor, index: Int): Double? =
        if (cursor.isNull(index)) null else cursor.getDouble(index)

    private fun metadataValue(metadata: String?, key: String): String = try {
        JSONObject(metadata ?: "{}").optString(key, "")
    } catch (_: Exception) { "" }

    private fun firstNonBlank(first: String?, second: String?): String =
        first?.takeIf { it.isNotBlank() } ?: second.orEmpty()
}
