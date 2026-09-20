package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitness.shared.feature.routine.api.RoutineRepositoryApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReadRepositoryTest {
    @Test
    fun requestedDateIsUsedForTodayReads() {
        val requested = mutableListOf<String>()
        val reads = object : HomeReadSources {
            override fun sessionsForDate(scope: AccountScope, date: String): List<String> {
                requested += "sessions:$date"
                return listOf("record-$date")
            }
            override fun dayMetrics(scope: AccountScope, date: String) = HomeDayWorkoutMetrics(0, 0, 0.0, 0)
            override fun latestInProgress(scope: AccountScope): String? = null
            override fun latestRoutineDate(scope: AccountScope, routineId: String, routineName: String): String? = null
            override fun mealCount(scope: AccountScope, date: String): Int = 0
            override fun mealTotals(scope: AccountScope, date: String) = HomeNutritionTotals(0, emptyMap())
            override fun meals(scope: AccountScope, date: String): List<HomeMealSummary> {
                requested += "meals:$date"
                return emptyList()
            }
            override fun bodyMetric(scope: AccountScope, date: String) = null
            override fun bodyMetrics(scope: AccountScope, date: String) = emptyList<com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric>()
            override fun nutritionGoal(scope: AccountScope) = null
        }
        val routines = object : RoutineRepositoryApi {
            override fun ensureActiveRoutine(scope: AccountScope): String = "routine"
        }
        val repository = HomeReadRepository(reads, routines)

        val today = repository.load(AccountScope("owner"), "2026-09-14")
        val past = repository.load(AccountScope("owner"), "2026-09-13")

        assertEquals("2026-09-14", today.today)
        assertEquals(listOf("record-2026-09-14"), today.todaySessions)
        assertEquals("2026-09-13", past.today)
        assertEquals(listOf("record-2026-09-13"), past.todaySessions)
        assertTrue(requested.contains("meals:2026-09-14"))
        assertTrue(requested.contains("meals:2026-09-13"))
    }
}
