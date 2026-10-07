package com.yeonsik.fitnessapp.integration.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodSyncRow
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionCatalogSyncCompatibilityTest {
    @Test fun foodPayloadOmitsLocalPackagingAndKeepsSupportedNutritionFields() {
        val serializer = NutritionCatalogSyncClient::class.java.getDeclaredMethod(
            "toFoodJson", List::class.java
        ).apply { isAccessible = true }
        val rows = listOf(food(), food().copy(packageAmount = null, packageUnit = null, packageCount = null))
        val payload = serializer.invoke(null, rows) as JSONArray
        for (index in 0 until payload.length()) {
            val row = payload.getJSONObject(index)
            assertFalse(row.has("package_amount"))
            assertFalse(row.has("package_unit"))
            assertFalse(row.has("package_count"))
            assertEquals("Fixture maker", row.getString("manufacturer_name"))
            assertEquals("Fixture brand", row.getString("brand_name"))
            assertEquals("Fixture line", row.getString("sub_brand_name"))
            assertEquals("Fixture product", row.getString("product_name"))
            assertEquals(100.0, row.getDouble("basis_amount"), 0.0)
            assertEquals("g", row.getString("basis_unit"))
            assertEquals(10.0, row.getDouble("protein_grams"), 0.0)
            assertEquals("private", row.getString("visibility"))
        }
    }

    @Test fun newerRemoteRowsAndTombstonesPreserveLocalPackageMetadata() {
        withCatalog { database, catalog ->
            assertEquals(1, catalog.applyRemoteFoodRows(listOf(food())))
            val remote = food().copy(
                name = "Updated fixture",
                packageAmount = null,
                packageUnit = null,
                packageCount = null,
                proteinGrams = 20.0,
                revision = 2,
                updatedAt = "2026-10-07T01:00:00Z"
            )
            assertEquals(1, catalog.applyRemoteFoodRows(listOf(remote)))
            val updated = database.nutritionRoomDao().foodForSync("fixture-food")!!
            assertEquals("Updated fixture", updated.name)
            assertEquals(20.0, updated.proteinGrams!!, 0.0)
            assertEquals(500.0, updated.packageAmount!!, 0.0)
            assertEquals("g", updated.packageUnit)
            assertEquals(2L, updated.packageCount)

            val tombstone = remote.copy(revision = 3, deletedAt = "2026-10-07T02:00:00Z")
            assertEquals(1, catalog.applyRemoteFoodRows(listOf(tombstone)))
            assertEquals(1, catalog.applyRemoteFoodRows(listOf(tombstone.copy(revision = 4))))
            val deleted = database.nutritionRoomDao().foodForSync("fixture-food")!!
            assertEquals(tombstone.deletedAt, deleted.deletedAt)
            assertEquals(500.0, deleted.packageAmount!!, 0.0)
            assertEquals("g", deleted.packageUnit)
            assertEquals(2L, deleted.packageCount)
        }
    }

    @Test fun newRemoteFoodKeepsUnknownPackagingNull() {
        withCatalog { database, catalog ->
            val remote = food().copy(packageAmount = null, packageUnit = null, packageCount = null)
            assertEquals(1, catalog.applyRemoteFoodRows(listOf(remote)))
            val inserted = database.nutritionRoomDao().foodForSync("fixture-food")!!
            assertNull(inserted.packageAmount)
            assertNull(inserted.packageUnit)
            assertNull(inserted.packageCount)
            assertEquals(100.0, inserted.basisAmount, 0.0)
        }
    }

    private fun withCatalog(test: (FitnessRoomDatabase, NutritionCatalogRepository) -> Unit) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        try {
            test(database, NutritionCatalogRepository(database, "fixture-owner"))
        } finally {
            database.close()
        }
    }

    private fun food() = NutritionFoodSyncRow(
        id = "fixture-food", ownerId = "fixture-owner", name = "Fixture food", brand = "Fixture brand",
        manufacturerName = "Fixture maker", brandName = "Fixture brand", subBrandName = "Fixture line",
        productName = "Fixture product", packageAmount = 500.0, packageUnit = "g", packageCount = 2L,
        kind = "ingredient", category = "other", basisAmount = 100.0, basisUnit = "g",
        prepState = "unspecified", cookingMethod = "unspecified", caloriesKcal = 100.0,
        proteinGrams = 10.0, carbsGrams = 15.0, fatGrams = 2.0, sodiumMg = null,
        saturatedFatGrams = null, sugarsGrams = null, fiberGrams = null, addedSugarsGrams = null,
        transFatGrams = null, cholesterolMg = null, sourceType = "manual", sourceReference = null,
        sourceVersion = null, dataVersion = 1, revision = 1, visibility = "private",
        createdAt = "2026-10-07T00:00:00Z", updatedAt = "2026-10-07T00:00:00Z", deletedAt = null
    )
}
