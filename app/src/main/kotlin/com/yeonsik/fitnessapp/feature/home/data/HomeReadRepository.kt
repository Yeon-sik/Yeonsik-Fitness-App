package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import com.yeonsik.fitnessapp.routine.RoutineRepository
import java.time.DayOfWeek
import java.time.LocalDate

/** Composes feature read ports into the Home snapshot; it never writes through another feature. */
class HomeReadRepository(
    private val reads: HomeReadSources,
    private val routines: RoutineRepository
) : HomeRepositoryApi {
    override fun load(scope: AccountScope, today: String): HomeSnapshot {
        val todayDate = LocalDate.parse(today)
        val currentWeek = todayDate.with(DayOfWeek.MONDAY)
        val dates = (0..13).map { currentWeek.minusWeeks(1).plusDays(it.toLong()) }
        val dayMetrics = dates.associate { date -> date.toString() to reads.dayMetrics(scope, date.toString()) }
        val mealCounts = dates.associate { date -> date.toString() to reads.mealCount(scope, date.toString()) }
        val nutritionTotals = dates.associate { date -> date.toString() to reads.mealTotals(scope, date.toString()) }
        val routineRows = routines.routines().map { it.toFeatureModel() }
        val routineExercises = routineRows.associate { routine ->
            routine.id to routines.routineExercises(routine.id).map { it.toFeatureModel() }
        }
        val latestRoutineDates = routineRows.associate { routine ->
            routine.id to reads.latestRoutineDate(scope, routine.id, routine.name)
        }
        return HomeSnapshot(
            scope.ownerId,
            today,
            reads.sessionsForDate(scope, today),
            routines.activeRoutineId(),
            routineRows,
            routineExercises,
            latestRoutineDates,
            reads.latestInProgress(scope),
            dayMetrics,
            mealCounts,
            nutritionTotals,
            reads.nutritionGoal(scope),
            reads.bodyMetric(scope, today),
            reads.bodyMetrics(scope, today),
            reads.meals(scope, today)
        )
    }

    private fun RoutineRepository.RoutineSummary.toFeatureModel() =
        RoutineSummary(id, name, exerciseCount)

}
