package com.yeonsik.fitnessapp.feature.home.model

import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary

data class HomeSnapshot(
    val ownerId: String,
    val today: String,
    val todaySessions: List<String>,
    val activeRoutineId: String?,
    val routines: List<RoutineSummary>,
    val routineExercises: Map<String, List<RoutineExerciseInstance>>,
    val latestRoutineDates: Map<String, String?>,
    val inProgressSessionId: String?,
    val dayMetrics: Map<String, HomeDayWorkoutMetrics>,
    val mealCounts: Map<String, Int>,
    val mealNutritionTotals: Map<String, HomeNutritionTotals>,
    val nutritionGoal: HomeNutritionGoal?,
    val todayWeight: HomeBodyMetric?,
    val todayBodyMetrics: List<HomeBodyMetric>,
    val todayMeals: List<HomeMealSummary>
)
