package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi

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

/** Composes feature read APIs into Home models without opening or querying the database. */
class FeatureHomeReadSources(
    private val workouts: WorkoutReadApi,
    private val meals: MealReadApi,
    private val body: BodyMetricsReadApi,
    private val development: DevelopmentReadApi
) : HomeReadSources {
    override fun sessionsForDate(scope: AccountScope, date: String): List<String> =
        workouts.sessionsForDate(scope, date)

    override fun dayMetrics(scope: AccountScope, date: String): HomeDayWorkoutMetrics =
        workouts.dayMetrics(scope, date).let {
            HomeDayWorkoutMetrics(
                it.sessionCount,
                it.totalSetCount,
                it.totalVolumeKg,
                it.totalDurationSeconds
            )
        }

    override fun latestInProgress(scope: AccountScope): String? = workouts.latestInProgress(scope)

    override fun latestRoutineDate(
        scope: AccountScope,
        routineId: String,
        routineName: String
    ): String? = workouts.latestRoutineDate(scope, routineId, routineName)

    override fun mealCount(scope: AccountScope, date: String): Int = meals.mealCount(scope, date)

    override fun mealTotals(scope: AccountScope, date: String): HomeNutritionTotals {
        val source = meals.mealTotals(scope, date)
        return HomeNutritionTotals(
            source.itemCount,
            source.totals.mapValues { (_, value) ->
                HomeNutritionTotal(value.sum, value.known, value.missing)
            }
        )
    }

    override fun meals(scope: AccountScope, date: String): List<HomeMealSummary> =
        meals.meals(scope, date).map { meal ->
            HomeMealSummary(
                meal.id,
                meal.date,
                meal.mealLabel,
                meal.menu,
                meal.calories,
                meal.proteinGrams,
                meal.carbsGrams,
                meal.fatGrams,
                meal.compositionCount,
                meal.previewTitle,
                meal.mealTime,
                meal.mealKind,
                meal.fulfillmentMode,
                meal.storeName,
                meal.branchName,
                meal.menuName,
                meal.nutritionStatus,
                meal.macroRatio,
                meal.macroRatioAccessibility,
                meal.timeEditable,
                meal.createdAt,
                meal.subtitle,
                meal.accessibilityLabel
            )
        }

    override fun bodyMetric(scope: AccountScope, date: String): HomeBodyMetric? =
        bodyMetrics(scope, date).firstOrNull()

    override fun bodyMetrics(scope: AccountScope, date: String): List<HomeBodyMetric> =
        body.bodyMetrics(scope, date).map { metric ->
            HomeBodyMetric(metric.id, metric.date, metric.weightKg, metric.memo)
        }

    override fun nutritionGoal(scope: AccountScope): HomeNutritionGoal? =
        development.nutritionGoal(scope)?.let { goal ->
            HomeNutritionGoal(
                goal.phase,
                goal.caloriesKcal,
                goal.proteinGrams,
                goal.carbsGrams,
                goal.fatGrams,
                goal.fiberGrams,
                goal.sodiumMg,
                goal.waterMl
            )
        }
}
