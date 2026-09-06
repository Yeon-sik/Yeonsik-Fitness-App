package com.yeonsik.fitnessapp.feature.home.model

import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.data.NutritionTotals
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance
import com.yeonsik.fitnessapp.routine.RoutineRepository

data class HomeSnapshot(
    val ownerId: String,
    val today: String,
    val todaySessions: List<String>,
    val activeRoutineId: String?,
    val routines: List<RoutineRepository.RoutineSummary>,
    val routineExercises: Map<String, List<RoutineExerciseInstance>>,
    val latestRoutineDates: Map<String, String?>,
    val inProgressSessionId: String?,
    val dayMetrics: Map<String, FitnessRepository.DayWorkoutMetrics>,
    val mealCounts: Map<String, Int>,
    val mealNutritionTotals: Map<String, NutritionTotals>,
    val nutritionGoal: AthleteNutritionGoal?,
    val todayWeight: FitnessRepository.BodyMetricEntry?,
    val todayBodyMetrics: List<FitnessRepository.BodyMetricEntry>,
    val todayMeals: List<FitnessRepository.MealEntry>
)
