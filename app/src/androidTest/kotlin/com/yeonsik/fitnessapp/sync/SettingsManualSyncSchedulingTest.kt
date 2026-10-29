package com.yeonsik.fitnessapp.sync

import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.app.AppContainer
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutSummaryRepository
import com.yeonsik.fitnessapp.feature.settings.application.SettingsSessionCoordinator
import com.yeonsik.fitnessapp.feature.settings.ui.*
import com.yeonsik.fitnessapp.integration.sync.ManualSyncRunner
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService
import com.yeonsik.fitnessapp.integration.personalos.FitnessSummaryPublisher
import com.yeonsik.fitnessapp.integration.personalos.LegacyFitnessSyncAdapter
import com.yeonsik.fitnessapp.integration.personalos.SupabaseSyncManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SettingsManualSyncSchedulingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: AuthTestContext
    private lateinit var graph: AppContainer
    private lateinit var shared: SupabaseConfigStore
    private lateinit var nutrition: NutritionSupabaseConfigStore
    private val executor = Executors.newSingleThreadExecutor()
    private var vm: SettingsViewModel? = null
    private var observer: Observer<SettingsUiState>? = null

    @Before fun prepare() {
        val application = ApplicationProvider.getApplicationContext<android.content.Context>()
        context = AuthTestContext(application)
        shared = SupabaseConfigStore(context)
        shared.saveConnection("https://shared.example", "test-key")
        shared.saveSession("fixture-owner", "fixture@example.com", "test-access", "test-refresh")
        nutrition = NutritionSupabaseConfigStore(context)
        nutrition.saveConnection("https://nutrition.example", "test-key")
        graph = AppContainer(application)
    }

    @After fun clean() {
        instrumentation.runOnMainSync { observer?.let { vm?.uiState?.removeObserver(it) } }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        graph.shutdownWorkoutWriteExecutor()
        context.clean()
    }

    @Test fun rapidCallsAreRejectedBeforeTheyEnterTheExecutorAndFailureAllowsRetry() {
        val waiting = CountDownLatch(1)
        val queued = CountDownLatch(1)
        executor.execute { queued.countDown(); waiting.await(5, TimeUnit.SECONDS) }
        assertTrue(queued.await(5, TimeUnit.SECONDS))
        val calls = AtomicInteger()
        val model = model(ManualSyncRunner { _, _, progress ->
            calls.incrementAndGet()
            progress.accept("영양 카탈로그를 확인하고 있습니다.")
            throw IOException("fixture failure")
        })
        val terminal = observeFailure(model)
        try {
            instrumentation.runOnMainSync {
                repeat(12) { model.runManualSync() }
                assertTrue(model.uiState.value!!.isManualSyncing)
                model.signOut(SettingsConnection.SHARED)
            }
        } finally { waiting.countDown() }
        assertTrue(terminal.await(8, TimeUnit.SECONDS))
        executor.submit {}.get(5, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
        assertEquals(1, calls.get())
        assertTrue(shared.load().isConfigured)
        assertFalse(model.uiState.value!!.isManualSyncing)
        val retried = observeFailure(model)
        instrumentation.runOnMainSync { model.runManualSync() }
        assertTrue(retried.await(8, TimeUnit.SECONDS))
        assertEquals(2, calls.get())
    }

    @Test fun aQueuedRunDoesNotUseAChangedNutritionConnection() {
        val waiting = CountDownLatch(1)
        val entered = CountDownLatch(1)
        executor.execute { entered.countDown(); waiting.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        var captured: String? = null
        val model = model(ManualSyncRunner { _, startingNutrition, _ ->
            captured = startingNutrition.supabaseUrl
            throw IOException("fixture failure")
        })
        val terminal = observeFailure(model)
        try {
            instrumentation.runOnMainSync { model.runManualSync() }
            nutrition.saveConnection("https://different.example", "different-key")
        } finally { waiting.countDown() }
        assertTrue(terminal.await(8, TimeUnit.SECONDS))
        assertNull(captured)
        assertEquals("https://different.example", nutrition.load().supabaseUrl)
    }

    @Test fun aQueuedRunLoadsNewTokensWithinItsStartingScope() {
        val waiting = CountDownLatch(1)
        val entered = CountDownLatch(1)
        executor.execute { entered.countDown(); waiting.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        var capturedToken: String? = null
        val model = model(ManualSyncRunner { startingShared, _, _ ->
            capturedToken = startingShared.accessToken
            throw IOException("fixture failure")
        })
        val terminal = observeFailure(model)
        try {
            instrumentation.runOnMainSync { model.runManualSync() }
            shared.saveSession("fixture-owner", "fixture@example.com", "new-test-access", "new-test-refresh")
        } finally { waiting.countDown() }
        assertTrue(terminal.await(8, TimeUnit.SECONDS))
        assertEquals("new-test-access", capturedToken)
    }

    @Test fun serviceRejectsConcurrentCallersAndReleasesItsGuardAfterFailure() {
        val database = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        val auth = SupabaseAuthManager(shared) { url ->
            attempts.incrementAndGet()
            AuthTestConnection(url, 401, "{\"error_code\":\"invalid_credentials\"}") {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        val service = SyncApplicationService(auth, SupabaseSyncManager(LegacyFitnessSyncAdapter(database),
            WorkoutSummaryRepository(database), FitnessSummaryPublisher()), graph.nutritionIntegrationService)
        try {
            val first = executor.submit<Exception?> {
                runCatching { service.run(shared.load(), nutrition.load()) }.exceptionOrNull() as? Exception
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            try {
                service.run(shared.load(), nutrition.load())
                fail("Expected a concurrent call to be rejected")
            } catch (busy: IllegalStateException) {
                assertEquals("이미 동기화가 진행 중입니다.", busy.message)
            }
            release.countDown()
            assertNotNull(first.get(5, TimeUnit.SECONDS))
            assertNotNull(runCatching { service.run(shared.load(), nutrition.load()) }.exceptionOrNull())
            assertEquals(2, attempts.get())
        } finally {
            release.countDown()
            database.close()
        }
    }

    private fun model(runner: ManualSyncRunner): SettingsViewModel {
        val coordinator = object : SettingsSessionCoordinator {
            override fun apply(connection: SettingsConnection, config: SupabaseConfig, authenticated: Boolean) = Unit
            override fun applySync(result: SyncApplicationService.Result) = Unit
        }
        return SettingsViewModel(SavedStateHandle(), MassUnitPreferences(context), ThemeModePreferences(context),
            shared, nutrition, PriceTraceSupabaseConfigStore(context), SupabaseAuthManager(shared),
            SupabaseAuthManager(nutrition), SupabaseAuthManager(PriceTraceSupabaseConfigStore(context)),
            runner, graph.localDataTransferApplicationService, coordinator, executor).also {
            vm = it
            instrumentation.runOnMainSync { it.enter() }
        }
    }

    private fun observeFailure(model: SettingsViewModel): CountDownLatch {
        val terminal = CountDownLatch(1)
        var started = false
        instrumentation.runOnMainSync {
            observer?.let { model.uiState.removeObserver(it) }
            observer = Observer { state ->
                if (state.isManualSyncing) started = true
                if (started && !state.isManualSyncing && state.syncLabel == "sync failed") terminal.countDown()
            }
            model.uiState.observeForever(observer!!)
        }
        return terminal
    }
}
