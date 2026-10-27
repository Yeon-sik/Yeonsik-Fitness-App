package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.feature.meal.api.*
import com.yeonsik.fitnessapp.feature.meal.model.*
import com.yeonsik.fitnessapp.feature.nutrition.api.*
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandRow
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.*

class MealDiningSearchViewModelTest {
    private val scope = AccountScope("fitness-owner")
    private val date = "2026-10-27"
    private val store = ViewModelStore()
    private val catalog = Catalog()
    private val remote = Remote()
    private val connection = Connection()
    private lateinit var vm: MealViewModel
    private val firstLocation = PriceTraceDiningLocation("22222222-2222-4222-8222-222222222222", "강남점")
    private val secondLocation = PriceTraceDiningLocation("66666666-6666-4666-8666-666666666666", "시청점")
    private val target = PriceTraceDiningTarget("11111111-1111-4111-8111-111111111111", "맥도날드", listOf(firstLocation),
        "33333333-3333-4333-8333-333333333333", "빅맥", "44444444-4444-4444-8444-444444444444")

    @After fun cleanup() { main { store.clear() } }

    @Test fun typingStartsDebouncedMenuQueryAndOneLocationAppliesExactIdentityWithOneSelection() {
        open(delay = 100)
        remote.reply = { listOf(target) }
        main { vm.updateDiningSearchQuery("  빅맥  "); assertTrue(state().diningSearch.loading); assertTrue(remote.queries.isEmpty()) }
        await { state().diningSearch.rows == listOf(target) }
        main {
            assertEquals(listOf("빅맥"), remote.queries.toList())
            assertEquals("", state().draft.restaurantId)
            vm.selectDiningTarget(target)
            assertIdentity(target, firstLocation)
            assertNull(state().diningSearch.pendingTarget)
            assertTrue(state().diningSearch.rows.isEmpty())
        }
    }

    @Test fun multipleLocationsAskForBranchAndImmediatelyApplyOnlyTheSelectedVerifiedLocation() {
        val many = target.copy(locations = listOf(firstLocation, secondLocation))
        open(); remote.reply = { listOf(many) }; search("맥도날드")
        main {
            vm.selectDiningTarget(many)
            assertEquals(many, state().diningSearch.pendingTarget)
            assertEquals("", state().draft.restaurantId)
            vm.selectDiningBranch("not-a-returned-location")
            assertEquals("", state().draft.restaurantId)
            vm.selectDiningBranch(secondLocation.restaurantLocationId)
            assertIdentity(many, secondLocation)
            assertNull(state().diningSearch.pendingTarget)
        }
    }

    @Test fun missingVerifiedLocationDoesNotInventIdentityOrRemoveManualEntry() {
        val missing = target.copy(locations = emptyList())
        open(); remote.reply = { listOf(missing) }; search("빅맥")
        main {
            vm.selectDiningTarget(missing)
            assertEquals("", state().draft.restaurantId)
            assertEquals("", state().draft.restaurantLocationId)
            assertEquals("", state().draft.restaurantMenuId)
            assertNotNull(state().diningSearch.error)
            vm.updateStore("내가 확인한 가게"); vm.updateMenu("직접 메뉴"); vm.updateCalories("450")
            assertEquals("직접 메뉴", state().draft.menu)
            assertEquals("450", state().draft.calories)
            assertTrue(state().diningOut)
        }
    }

    @Test fun queryChangeCancelsPendingBranchAndNeverAppliesAnOldSelection() {
        val many = target.copy(locations = listOf(firstLocation, secondLocation))
        open(); remote.reply = { listOf(many) }; search("old")
        main {
            vm.selectDiningTarget(many)
            vm.updateDiningSearchQuery("new")
            assertNull(state().diningSearch.pendingTarget)
            vm.selectDiningBranch(firstLocation.restaurantLocationId)
            vm.selectDiningTarget(many)
            assertEquals("", state().draft.restaurantMenuId)
        }
    }

    @Test fun lookupFailureAndNoResultsAreDistinctAndNeverBecomeMealValidationErrors() {
        open(); remote.reply = { throw IOException("offline") }; search("실패", expectError = true)
        main {
            assertEquals("offline", state().diningSearch.error)
            assertNull(state().error)
            assertTrue(state().diningSearch.searched)
            vm.updateStore("수동 식당"); vm.updateMenu("수동 메뉴")
        }
        remote.reply = { emptyList() }; search("없는 메뉴")
        main {
            assertNull(state().diningSearch.error)
            assertTrue(state().diningSearch.rows.isEmpty())
            assertEquals("수동 메뉴", state().draft.menu)
            assertNull(state().error)
        }
    }

    @Test fun olderNonCancellableResponseCannotReplaceANewerMenuQuery() {
        open()
        val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        remote.reply = { query ->
            if (query == "old") { started.countDown(); awaitIgnoringInterrupts(release); finished.countDown(); listOf(target) }
            else emptyList()
        }
        try {
            main { vm.updateDiningSearchQuery("old") }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            search("new")
            release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            main { assertEquals("new", state().diningSearch.query); assertTrue(state().diningSearch.rows.isEmpty()) }
        } finally { release.countDown() }
    }

    @Test fun closingAndChangingDateFitnessOwnerOrNutritionOwnerRejectLateResponses() {
        for (change in listOf("close", "date", "owner", "nutrition-owner")) {
            if (::vm.isInitialized) main { store.clear() }
            open()
            val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
            remote.reply = { started.countDown(); awaitIgnoringInterrupts(release); finished.countDown(); listOf(target) }
            try {
                main { vm.updateDiningSearchQuery("pending") }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                main {
                    when (change) {
                        "close" -> vm.closeDraft()
                        "date" -> vm.enter(scope, "2026-10-26")
                        "owner" -> vm.enter(AccountScope("another-owner"), date)
                        else -> { catalog.owner = "another-nutrition-owner"; vm.enter(scope, date) }
                    }
                }
                release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                main { assertTrue(state().diningSearch.rows.isEmpty()); assertNull(state().diningSearch.pendingTarget) }
            } finally { release.countDown() }
        }
    }

    @Test fun configurationChangeRejectsBothLateResultsAndAnAlreadyRenderedTarget() {
        open(); remote.reply = { listOf(target) }; search("빅맥")
        main {
            connection.changeScope()
            vm.selectDiningTarget(target)
            assertEquals("", state().draft.restaurantId)
        }
        await { state().diningSearch.rows.isEmpty() }
        val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        remote.reply = { started.countDown(); awaitIgnoringInterrupts(release); finished.countDown(); listOf(target) }
        try {
            main { vm.updateDiningSearchQuery("pending") }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            main { connection.changeScope() }
            release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            main { assertTrue(state().diningSearch.rows.isEmpty()) }
        } finally { release.countDown() }
    }

    @Test fun ordinaryDiningSearchNeverMutatesPublicationDirectoryState() {
        open()
        lateinit var before: PriceTraceUiState
        main { before = vm.priceTraceState.value!! }
        remote.reply = { listOf(target) }; search("빅맥")
        main { vm.selectDiningTarget(target); assertSame(before, vm.priceTraceState.value) }
    }

    private fun open(delay: Long = 0) {
        catalog.owner = "nutrition-owner"; remote.queries.clear()
        val config = SupabaseConfig.empty()
        val integration = NutritionIntegrationService(catalog, proxy<NutritionCatalogSyncStore>(),
            ProductReadV1Client(config), RestaurantMenuReadV1Client(config), SupabaseAuthManager(null), SupabaseAuthManager(null))
        main {
            vm = MealViewModel(SavedStateHandle(), proxy<MealRecordRepositoryApi>(), catalog, integration,
                searchDelayMillis = delay, manualBrandCatalog = connection, diningCatalog = remote)
            store.put("meal", vm); vm.enter(scope, date); vm.chooseDiningOut()
        }
    }
    private fun search(query: String, expectError: Boolean = false) {
        main { vm.updateDiningSearchQuery(query) }
        await { state().diningSearch.searched && !state().diningSearch.loading &&
            state().diningSearch.query == query && (!expectError || state().diningSearch.error != null) }
    }
    private fun assertIdentity(target: PriceTraceDiningTarget, location: PriceTraceDiningLocation) {
        val draft = state().draft
        assertEquals(target.restaurantId, draft.restaurantId); assertEquals(target.restaurantName, draft.store)
        assertEquals(location.restaurantLocationId, draft.restaurantLocationId); assertEquals(location.branchName.orEmpty(), draft.branch)
        assertEquals(target.restaurantMenuId, draft.restaurantMenuId); assertEquals(target.menuName, draft.menu)
        assertEquals(target.catalogProductId, draft.catalogProductId)
    }
    private fun state() = vm.uiState.value as MealUiState.Ready
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) { var done = false; main { done = predicate() }; if (done) return; Thread.sleep(20) }
        fail("Timed out waiting for Dining search")
    }
    private fun awaitIgnoringInterrupts(latch: CountDownLatch) { while (latch.count > 0) try { latch.await() } catch (_: InterruptedException) { } }
    private inline fun <reified T> proxy(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) { "equals" -> proxy === args?.firstOrNull(); "hashCode" -> System.identityHashCode(proxy); "toString" -> "Meal fixture"; else -> null }
    } as T
    private class Catalog : NutritionCatalogRepositoryApi {
        @Volatile var owner = "nutrition-owner"
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = emptyList<NutritionFood>()
        override fun savedDiningOutMenus() = emptyList<NutritionFood>()
        override fun findFoodById(foodId: String): NutritionFood? = null
    }
    private class Remote : PriceTraceDiningCatalog {
        val queries = CopyOnWriteArrayList<String>()
        @Volatile var reply: (String) -> List<PriceTraceDiningTarget> = { emptyList() }
        override fun searchDiningTargets(query: String): List<PriceTraceDiningTarget> { queries.add(query); return reply(query) }
    }
    private class Connection : PriceTraceBrandCatalog {
        private var scope = 0L
        private val listeners = CopyOnWriteArrayList<Runnable>()
        override fun brandCatalogScope() = scope
        override fun isBrandCatalogConfigured() = false
        override fun searchPriceTraceBrands(query: String, limit: Int, offset: Int) = emptyList<PriceTraceBrandRow>()
        override fun observeBrandCatalogScope(listener: Runnable): AutoCloseable { listeners.add(listener); return AutoCloseable { listeners.remove(listener) } }
        fun changeScope() { scope++; listeners.forEach(Runnable::run) }
    }
}
