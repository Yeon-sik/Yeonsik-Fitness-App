package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Isolated SQLite only: never touch the installed user's catalog or meal records. */
class NutritionProductRegistrationTest {
    private lateinit var room: FitnessRoomDatabase
    private lateinit var catalog: NutritionCatalogRepository
    private val scope = AccountScope("product-registration-owner")

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitnessRoomDatabase::class.java).build()
        catalog = NutritionCatalogRepository(room, scope.ownerId)
    }
    @After fun close() { room.close() }

    @Test fun exactProductSavesIdentityHierarchyAndNutritionWithoutCreatingAMeal() {
        val selected = product("81111111-1111-4111-8111-111111111111", 100.0)
        val saved = requireNotNull(catalog.savePriceTraceProductInput(scope, input(), selected))
        val loaded = catalog.findFoodById(saved.id)!!
        val link = catalog.approvedProductLink(saved.id)!!
        assertEquals(selected.catalogProductId, link.catalogProductId)
        assertEquals(selected.standardProductId, link.standardProductId)
        assertEquals(selected.revision, link.catalogProductRevision)
        assertEquals("PT 제조회사", loaded.manufacturerName)
        assertEquals("PT 서브브랜드", loaded.subBrandName)
        assertEquals("pricetrace_manual", loaded.sourceType)
        assertFalse(loaded.isDiningOutMenu())
        assertEquals(400.0, loaded.profile.value(NutritionProfile.CALORIES_KCAL)!!, 0.0)
        assertNull(loaded.profile.sodiumMg())
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, "2026-10-22"))
    }

    @Test fun groupedStandardDoesNotGuessAnExactPackageWhenTheBasisIsAmbiguous() {
        val grouped = ProductReadV1.search(listOf(product("81111111-1111-4111-8111-111111111111", 130.0),
            product("83333333-3333-4333-8333-333333333333", 210.0)), "", 20).single()
        val saved = requireNotNull(catalog.savePriceTraceProductInput(scope, input(), grouped))
        assertNull(catalog.approvedProductLink(saved.id))
        assertEquals("pricetrace_standard", saved.sourceType)
        assertEquals("standardProductId:${grouped.standardProductId}", saved.sourceReference)
    }

    @Test fun selectedProductAndManualEstimateKeepSeparateMeanings() {
        val saved = requireNotNull(catalog.savePriceTraceProductInput(scope, input().copy(sourceType = "manual_estimate"),
            product("81111111-1111-4111-8111-111111111111", 100.0)))
        assertEquals("manual_product_estimate", saved.sourceType)
        assertEquals("manual_estimate", org.json.JSONObject(saved.sourceReference).getString("nutrition_source"))
        assertNotNull(catalog.approvedProductLink(saved.id))
    }

    @Test fun invalidMetadataWrongOwnerOrChangedIdentityCannotCreateAFood() {
        val selected = product("81111111-1111-4111-8111-111111111111", 100.0)
        assertThrows(IllegalStateException::class.java) { catalog.savePriceTraceProductInput(AccountScope("other-owner"), input(), selected) }
        assertThrows(IllegalArgumentException::class.java) { catalog.savePriceTraceProductInput(scope, input().copy(name = "다른 제품"), selected) }
        val incomplete = ProductReadV1(selected.catalogProductId, selected.standardProductId, selected.name, selected.brand,
            null, null, null, 100.0, "g", 1)
        assertThrows(IllegalArgumentException::class.java) { catalog.savePriceTraceProductInput(scope, input(), incomplete) }
        assertTrue(catalog.searchPackagedFoods("PT 라벨", 20).isEmpty())
    }

    private fun input() = NutritionFoodInput("PT 라벨", "PT 브랜드", 100.0, "g",
        NutritionProfile.ofMacros(400.0, 20.0, 30.0, 20.0), "manual", NutritionRegistrationKind.PRODUCT, 100.0, "g")
    private fun product(id: String, amount: Double) = ProductReadV1(id, "82222222-2222-4222-8222-222222222222", "PT 라벨", "PT 브랜드",
        "PT 제조회사", "PT 서브브랜드", null, null, null, amount, "g", 1, "sha256:" + "a".repeat(64))
}
