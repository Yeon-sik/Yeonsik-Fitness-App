package com.yeonsik.fitnessapp.sync

import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.app.AppContainer
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.feature.settings.application.SettingsSessionCoordinator
import com.yeonsik.fitnessapp.feature.settings.ui.*
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SettingsAuthenticationStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: AuthTestContext
    private lateinit var shared: SupabaseConfigStore
    private lateinit var nutrition: NutritionSupabaseConfigStore
    private lateinit var priceTrace: PriceTraceSupabaseConfigStore
    private lateinit var graph: AppContainer
    private val executor = Executors.newSingleThreadExecutor()
    private var viewModel: SettingsViewModel? = null
    private var stateObserver: Observer<SettingsUiState>? = null
    private var eventObserver: Observer<SettingsEvent>? = null
    private var coordinatorCalls = 0

    @Before fun prepare() {
        val application = ApplicationProvider.getApplicationContext<android.content.Context>()
        context = AuthTestContext(application)
        shared = SupabaseConfigStore(context)
        nutrition = NutritionSupabaseConfigStore(context)
        priceTrace = PriceTraceSupabaseConfigStore(context)
        shared.saveConnection("https://shared.example.com", "sb_publishable_test")
        graph = AppContainer(application)
    }
    @After fun clean() {
        instrumentation.runOnMainSync {
            stateObserver?.let { viewModel?.uiState?.removeObserver(it) }
            eventObserver?.let { viewModel?.events?.removeObserver(it) }
        }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        graph.shutdownWorkoutWriteExecutor()
        context.clean()
    }

    @Test fun failedLoginPublishesAnErrorFromTheWorkerAndReenablesControls() {
        val vm = createViewModel(400, """{"error_code":"invalid_credentials","msg":"private server text"}""")
        awaitState(vm, "authentication failed") { vm.signIn(SettingsConnection.SHARED, "person@example.com", "password") }
        val terminal = vm.uiState.value!!
        assertFalse(terminal.isAccountOperationInProgress)
        assertFalse(terminal.sharedConfig.isConfigured)
        assertTrue(terminal.authenticationErrorDetail.contains("이메일 또는 비밀번호"))
        assertFalse(terminal.authenticationErrorDetail.contains("private server text"))
        assertEquals(terminal.authenticationErrorDetail, terminal.syncDetail)
        assertEquals(0, coordinatorCalls)
    }

    @Test fun successfulLoginLeavesAuthenticatingEvenWhenTheEventRefreshesTheScreen() {
        val vm = createViewModel(200, sessionResponse())
        awaitState(vm, "configured") { vm.signIn(SettingsConnection.SHARED, "person@example.com", "password") }
        val terminal = vm.uiState.value!!
        assertFalse(terminal.isAccountOperationInProgress)
        assertTrue(terminal.sharedConfig.isConfigured)
        assertEquals("configured", terminal.syncLabel)
        assertFalse(terminal.syncDetail.contains("로그인하는 중"))
        assertEquals("", terminal.authenticationErrorDetail)
        assertEquals(1, coordinatorCalls)
    }

    @Test fun signupConfirmationIsTerminalAndNeverAppliesAnAuthenticatedAccount() {
        val vm = createViewModel(200, """{"id":"new-owner","email":"person@example.com"}""")
        awaitState(vm, "confirmation required") { vm.signUp(SettingsConnection.SHARED, "person@example.com", "password123") }
        val terminal = vm.uiState.value!!
        assertFalse(terminal.isAccountOperationInProgress)
        assertTrue(terminal.syncDetail.contains("가입 확인 메일"))
        assertFalse(terminal.sharedConfig.isConfigured)
        assertEquals(0, coordinatorCalls)
    }

    @Test fun timeoutShowsADetailAndAllowsRetryingWithTheSameSavedConnection() {
        val vm = createViewModel(200, sessionResponse(), java.net.SocketTimeoutException())
        awaitState(vm, "authentication failed") { vm.signIn(SettingsConnection.SHARED, "person@example.com", "password") }
        assertFalse(vm.uiState.value!!.isAccountOperationInProgress)
        assertTrue(vm.uiState.value!!.authenticationErrorDetail.contains("지연"))
        assertEquals("https://shared.example.com", shared.load().supabaseUrl)
        awaitState(vm, "configured") { vm.signIn(SettingsConnection.SHARED, "person@example.com", "password") }
        assertTrue(vm.uiState.value!!.sharedConfig.isConfigured)
        assertEquals("", vm.uiState.value!!.authenticationErrorDetail)
    }

    private fun createViewModel(status: Int, response: String, error: Exception? = null): SettingsViewModel {
        val failOnce = java.util.concurrent.atomic.AtomicBoolean(error != null)
        val auth = SupabaseAuthManager(shared) { url ->
            if (error != null && failOnce.compareAndSet(true, false)) throw error
            AuthTestConnection(url, status, response)
        }
        val coordinator = object : SettingsSessionCoordinator {
            override fun apply(connection: SettingsConnection, config: SupabaseConfig, authenticated: Boolean) {
                coordinatorCalls++
            }
            override fun applySync(result: SyncApplicationService.Result) = Unit
        }
        return SettingsViewModel(SavedStateHandle(), MassUnitPreferences(context), ThemeModePreferences(context),
            shared, nutrition, priceTrace, auth, SupabaseAuthManager(nutrition), SupabaseAuthManager(priceTrace),
            graph.syncApplicationService, graph.localDataTransferApplicationService, coordinator, executor
        ).also { viewModel = it }
    }

    private fun awaitState(vm: SettingsViewModel, expected: String, action: () -> Unit) {
        val terminal = CountDownLatch(1)
        instrumentation.runOnMainSync {
            stateObserver?.let { vm.uiState.removeObserver(it) }
            eventObserver?.let { vm.events.removeObserver(it) }
        }
        stateObserver = Observer { state ->
            if (!state.isAccountOperationInProgress && state.syncLabel == expected) terminal.countDown()
        }
        // Mirrors ComposeAppScreen's refresh after consuming an account event.
        eventObserver = Observer { event -> if (event.consume()) vm.refresh() }
        instrumentation.runOnMainSync {
            vm.enter()
            vm.uiState.observeForever(stateObserver!!)
            vm.events.observeForever(eventObserver!!)
            action()
            assertTrue(vm.uiState.value!!.isAccountOperationInProgress)
        }
        assertTrue("Terminal authentication state was never delivered", terminal.await(8, TimeUnit.SECONDS))
        executor.submit {}.get(8, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
    }
}
