package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitness.shared.feature.cardio.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CardioStartViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val executor = Executors.newSingleThreadExecutor()
    @After fun tearDown() { executor.shutdownNow() }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var done = false
            main { done = condition() }
            if (done) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for the fixture state")
    }

    @Test fun startDraftRestoresSearchActivityAndEnvironmentAfterRecreation() {
        lateinit var holder: CardioStartViewModel
        main {
            holder = CardioStartViewModel(SavedStateHandle(mapOf(
                "cardio_start.owner" to "owner", "cardio_start.query" to "러닝",
                "cardio_start.activity" to "running", "cardio_start.environment" to "indoor"
            )), Repository(), executor)
            holder.enter(AccountScope("owner"))
        }
        await { holder.uiState.value?.loading == false }
        main {
            val state = holder.uiState.value!!
            assertEquals("러닝", state.query)
            assertEquals(CardioActivityType.RUNNING, state.selectedActivity)
            assertEquals(CardioEnvironment.INDOOR, state.environment)
            assertTrue(state.canStart)
        }
    }

    @Test fun changingOwnerDiscardsDraftAndIgnoresThePreviousOwnersDelayedRecentRead() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository = object : Repository() {
            override fun lastStartedAtByActivity(scope: AccountScope): Map<String, Long> {
                if (scope.ownerId == "old") { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)) }
                return if (scope.ownerId == "old") mapOf("walking" to 1L) else mapOf("cycling" to 2L)
            }
        }
        lateinit var holder: CardioStartViewModel
        main { holder = CardioStartViewModel(SavedStateHandle(), repository, executor); holder.enter(AccountScope("old")) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        main {
            holder.search("러닝"); holder.selectActivity(CardioActivityType.RUNNING)
            holder.selectEnvironment(CardioEnvironment.INDOOR); holder.enter(AccountScope("new"))
        }
        release.countDown()
        await { holder.uiState.value?.loading == false }
        main {
            val state = holder.uiState.value!!
            assertEquals("new", state.ownerId); assertEquals("", state.query)
            assertNull(state.selectedActivity); assertNull(state.environment)
            assertEquals(mapOf("cycling" to 2L), state.lastStartedAt)
        }
    }

    @Test fun recentReadDoesNotOverwriteEditsMadeWhileItWasLoading() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository = object : Repository() {
            override fun lastStartedAtByActivity(scope: AccountScope): Map<String, Long> {
                started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS))
                return mapOf("rowing" to 9L)
            }
        }
        lateinit var holder: CardioStartViewModel
        main { holder = CardioStartViewModel(SavedStateHandle(), repository, executor); holder.enter(AccountScope("owner")) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        main {
            holder.search("사이클"); holder.selectActivity(CardioActivityType.CYCLING)
            holder.selectEnvironment(CardioEnvironment.INDOOR)
        }
        release.countDown()
        await { holder.uiState.value?.loading == false }
        main {
            val state = holder.uiState.value!!
            assertEquals("사이클", state.query); assertEquals(CardioActivityType.CYCLING, state.selectedActivity)
            assertEquals(CardioEnvironment.INDOOR, state.environment); assertTrue(state.canStart)
        }
    }

    @Test fun pendingStartCannotBeWrittenToADifferentAccountAfterThePermissionCallback() {
        main {
            val holder = CardioSessionViewModel(SavedStateHandle(mapOf(
                "cardio_pending_start.owner" to "old", "cardio_pending_start.activity_type" to "running",
                "cardio_pending_start.environment" to "indoor", "cardio_pending_start.date" to "2026-10-04"
            )), Repository(), null, executor)
            holder.startAfterPermissions(AccountScope("new"))
            assertFalse(holder.hasPendingPermissionAction())
            assertNull(holder.actionState.value)
        }
    }

    @Test fun invalidEquipmentInputKeepsTheDraftAndDoesNotCompleteTheSession() {
        main {
            val holder = CardioSessionViewModel(SavedStateHandle(), Repository(), null, executor)
            val snapshot = CardioSessionSnapshot("record", "running", "실내 달리기", "paused",
                1000L, null, 10000L, 0.0, 0, "not_used", null, CardioEnvironment.INDOOR)
            holder.openHeartRateEditor(AccountScope("owner"), "record", true, snapshot)
            holder.updateDistanceInput("NaN"); holder.submitHeartRate(AccountScope("owner"), "")
            val editor = holder.heartRateEditorState.value as CardioHeartRateEditorUiState.Ready
            assertEquals("NaN", editor.distanceInput); assertNotNull(editor.distanceErrorMessage)
            assertNull(holder.actionState.value)
        }
    }

    private open class Repository : CardioRepositoryApi {
        override fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot? = null
    }
}
