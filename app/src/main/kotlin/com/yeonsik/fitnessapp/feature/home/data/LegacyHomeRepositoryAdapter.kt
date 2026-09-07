package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.data.NutritionTotals
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import com.yeonsik.fitnessapp.routine.RoutineRepository
import java.time.DayOfWeek
import java.time.LocalDate

class LegacyHomeRepositoryAdapter(
    private val fitness: FitnessRepository,
    private val routines: RoutineRepository
) : HomeRepositoryApi {
    override fun load(scope: AccountScope, today: String): HomeSnapshot {
        check(scope.ownerId == fitness.currentUserId()) {
            "The account changed while the home operation was pending."
        }
        val todayDate = LocalDate.parse(today)
        val currentWeek = todayDate.with(DayOfWeek.MONDAY)
        val previousWeek = currentWeek.minusWeeks(1)
        val dates = (0..13).map { currentWeek.minusWeeks(1).plusDays(it.toLong()) }
        val dayMetrics = dates.associate { date ->
            date.toString() to fitness.dayWorkoutMetrics(date.toString()).toFeatureModel()
        }
        val mealCounts = dates.associate { date ->
            date.toString() to fitness.mealCountForDate(date.toString())
        }
        val nutritionTotals = dates.associate { date ->
            date.toString() to fitness.mealNutritionTotalsForDate(date.toString()).toFeatureModel()
        }
        val routineRows = routines.routines().map { it.toFeatureModel() }
        val routineExercises = routineRows.associate { routine ->
            routine.id to routines.routineExercises(routine.id).map { it.toFeatureModel() }
        }
        val latestRoutineDates = routineRows.associate { routine ->
            routine.id to fitness.latestCompletedWorkoutDateForRoutine(routine.id, routine.name)
        }
        return HomeSnapshot(
            scope.ownerId,
            today,
            fitness.sessionsForDate(today),
            routines.activeRoutineId(),
            routineRows,
            routineExercises,
            latestRoutineDates,
            fitness.latestInProgressSessionId(),
            dayMetrics,
            mealCounts,
            nutritionTotals,
            fitness.nutritionGoal()?.toFeatureModel(),
            fitness.bodyMetricForDate(today)?.toFeatureModel(),
            fitness.bodyMetricEntriesForDate(today).map { it.toFeatureModel() },
            fitness.mealEntriesForDate(today).map { it.toFeatureModel() }
        )
    }

    private fun RoutineRepository.RoutineSummary.toFeatureModel() =
        RoutineSummary(id, name, exerciseCount)

    private fun FitnessRepository.DayWorkoutMetrics.toFeatureModel() =
        HomeDayWorkoutMetrics(sessionCount, totalSetCount, totalVolumeKg, totalDurationSeconds)

    private fun FitnessRepository.BodyMetricEntry.toFeatureModel() =
        HomeBodyMetric(id, date, weightKg, memo)

    private fun FitnessRepository.MealEntry.toFeatureModel() =
        HomeMealSummary(
            id, date, mealLabel, menu, calories, proteinGrams, carbsGrams, fatGrams,
            compositionCount, previewTitle, mealTime, mealKind, fulfillmentMode,
            storeName, branchName, menuName, nutritionStatus, macroRatio,
            macroRatioAccessibility, timeEditable, createdAt,
            previewSubtitle(), previewAccessibilityLabel()
        )

    private fun AthleteNutritionGoal.toFeatureModel() = HomeNutritionGoal(
        phase, caloriesKcal, proteinGrams, carbsGrams, fatGrams,
        fiberGrams, sodiumMg, waterMl
    )

    private fun NutritionTotals.toFeatureModel(): HomeNutritionTotals {
        val mapped = keys().associateWith { key ->
            val total = total(key)
            HomeNutritionTotal(total.knownSum(), total.knownCount(), total.missingCount())
        }
        return HomeNutritionTotals(itemCount(), mapped)
    }
}
