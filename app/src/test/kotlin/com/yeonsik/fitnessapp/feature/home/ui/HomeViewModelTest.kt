package com.yeonsik.fitnessapp.feature.home.ui

import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun inProgressSnapshotDoesNotCauseAnotherHomeLoad() {
        val loadCount = AtomicInteger()
        val loaded = CountDownLatch(1)
        val loadedSnapshot = AtomicReference<HomeSnapshot>()
        val executor = Executors.newSingleThreadExecutor()
        val snapshot = HomeSnapshot(
            ownerId = "owner",
            today = "2026-09-12",
            todaySessions = emptyList(),
            activeRoutineId = null,
            routines = emptyList(),
            routineExercises = emptyMap(),
            latestRoutineDates = emptyMap(),
            inProgressSessionId = "session-in-progress",
            dayMetrics = emptyMap(),
            mealCounts = emptyMap(),
            mealNutritionTotals = emptyMap(),
            nutritionGoal = null,
            todayWeight = null,
            todayBodyMetrics = emptyList(),
            todayMeals = emptyList()
        )
        val viewModel = HomeViewModel(
            SavedStateHandle(),
            object : HomeRepositoryApi {
                override fun load(scope: AccountScope, today: String): HomeSnapshot {
                    loadCount.incrementAndGet()
                    loadedSnapshot.set(snapshot)
                    loaded.countDown()
                    return snapshot
                }
            },
            executor
        )

        try {
            viewModel.enter(AccountScope("owner"), "2026-09-12")

            assertTrue(loaded.await(1, TimeUnit.SECONDS))
            assertEquals(1, loadCount.get())
            assertEquals("session-in-progress", loadedSnapshot.get().inProgressSessionId)
        } finally {
            executor.shutdownNow()
        }
    }
}
