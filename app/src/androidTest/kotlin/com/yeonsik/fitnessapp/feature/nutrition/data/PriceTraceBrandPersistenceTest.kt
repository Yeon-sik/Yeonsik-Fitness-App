package com.yeonsik.fitnessapp.feature.nutrition.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.backup.LocalDataBackupService
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitness.shared.core.account.AccountScope
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PriceTraceBrandPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val selected = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "외부 브랜드",
        "22222222-2222-4222-8222-222222222222", null)
    private fun room() = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
    private inline fun <T> FitnessRoomDatabase.useRoom(block: (FitnessRoomDatabase) -> T): T = try { block(this) } finally { close() }
    private fun input(brand: PriceTraceBrandSelection? = null, kind: NutritionRegistrationKind = NutritionRegistrationKind.PRODUCT) =
        NutritionFoodInput("라벨 제품", brand?.canonicalName ?: "라벨 브랜드", 100.0, "g", NutritionProfile.ofMacros(100.0, 10.0, 10.0, 2.0),
            "manual", kind, 100.0, "g", brand)

    @Test fun explicitSetChangeClearAreAtomicOwnerPrivateProductWritesAndPreserveIdentity() {
        room().useRoom { db ->
            val repo = NutritionCatalogRepository(db, "owner")
            val inserted = requireNotNull(repo.saveManualFoodInput(AccountScope("owner"), input()))
            db.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET manufacturer_name='제조사',brand_name='라벨 대표'," +
                "sub_brand_name='라벨 서브',product_name='라벨 제품명' WHERE id=?", arrayOf(inserted.id))
            val food = requireNotNull(repo.findFoodById(inserted.id))
            val key = repo.canonicalPackagedProductKey(food)
            val linked = repo.setPriceTraceBrand(food.id, "owner", selected)
            assertEquals(selected, linked.priceTraceBrand)
            assertEquals(food.revision + 1, linked.revision)
            assertEquals(food.brand, linked.brand); assertEquals(food.name, linked.name)
            assertEquals(food.manufacturerName, linked.manufacturerName); assertEquals(food.brandName, linked.brandName)
            assertEquals(food.subBrandName, linked.subBrandName); assertEquals(food.productName, linked.productName)
            assertTrue(repo.applySourceReference(food.id, "owner", "{\"nutrition_source\":\"label\"}", "2030-01-01T00:00:00Z"))
            assertEquals(selected, repo.findFoodById(food.id)!!.priceTraceBrand)
            assertEquals(key, repo.canonicalPackagedProductKey(linked))
            assertNull(repo.approvedProductLink(food.id))
            val root = selected.copy(parentBrandId = null, parentCanonicalName = null)
            val changed = repo.setPriceTraceBrand(food.id, "owner", root)
            assertEquals(root, changed.priceTraceBrand)
            val cleared = repo.setPriceTraceBrand(food.id, "owner", null)
            assertNull(cleared.priceTraceBrand)
            assertEquals(food.revision + 3, cleared.revision)
            val row = db.nutritionRoomDao().foodForSync(food.id)!!
            assertNull(row.priceTraceBrandId); assertNull(row.priceTraceBrandNameSnapshot)
            assertNull(row.priceTraceParentBrandIdSnapshot); assertNull(row.priceTraceParentBrandNameSnapshot)
            repo.setUserId("other")
            assertThrows(IllegalStateException::class.java) { repo.setPriceTraceBrand(food.id, "owner", selected) }
            repo.setUserId("owner")
            db.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET visibility='public' WHERE id=?", arrayOf(food.id))
            assertFalse(repo.canEditPriceTraceBrand(food.id, "owner"))
            assertThrows(IllegalStateException::class.java) { repo.setPriceTraceBrand(food.id, "owner", selected) }
            val ingredient = requireNotNull(repo.saveManualFoodInput(AccountScope("owner"), input(kind = NutritionRegistrationKind.INGREDIENT)))
            assertThrows(IllegalStateException::class.java) { repo.setPriceTraceBrand(ingredient.id, "owner", selected) }
        }
    }

    @Test fun draftRegistrationAndSyncPreserveSnapshotsAndExistingRevisionPolicy() {
        room().useRoom { source -> room().useRoom { target ->
            val repo = NutritionCatalogRepository(source, "owner")
            val remote = NutritionCatalogRepository(target, "owner")
            val food = requireNotNull(repo.saveManualFoodInput(AccountScope("owner"), input(selected)))
            assertEquals(selected, repo.findFoodById(food.id)!!.priceTraceBrand)
            val first = repo.exportSyncSnapshot("owner").foods.single()
            assertEquals(selected, first.priceTraceBrand)
            assertEquals(1, remote.applyRemoteFoodRows(listOf(first)))
            assertEquals(selected, remote.findFoodById(food.id)!!.priceTraceBrand)
            repo.setPriceTraceBrand(food.id, "owner", null)
            val cleared = repo.exportSyncSnapshot("owner").foods.single()
            assertEquals(1, remote.applyRemoteFoodRows(listOf(cleared)))
            assertNull(remote.findFoodById(food.id)!!.priceTraceBrand)
            assertEquals(0, remote.applyRemoteFoodRows(listOf(first)))
            assertNull(remote.findFoodById(food.id)!!.priceTraceBrand)
            val ingredient = requireNotNull(repo.saveManualFoodInput(AccountScope("owner"), input(selected, NutritionRegistrationKind.INGREDIENT)))
            assertEquals(selected.brandId, ingredient.priceTraceBrandId)
            assertEquals(selected.canonicalName, ingredient.brand)
            assertEquals(selected.canonicalName, ingredient.brandName)
            assertEquals("manual", ingredient.sourceType)
            assertNull(repo.approvedProductLink(ingredient.id))
            assertThrows(IllegalArgumentException::class.java) {
                repo.saveManualFoodInput(AccountScope("owner"), input(selected).copy(brand = "다른 이름"))
            }
        } }
    }

    @Test fun backupRoundTripsNewFieldsAndOldBackupMissingColumnsRestoresNullWithoutPartialImport() {
        room().useRoom { source -> room().useRoom { restored -> room().useRoom { legacy ->
            val repo = NutritionCatalogRepository(source, "owner")
            val food = requireNotNull(repo.saveManualFoodInput(AccountScope("owner"), input(selected)))
            val output = ByteArrayOutputStream()
            LocalDataBackupService(source, context, "records-owner", "owner").writeBackup(output)
            val backup = JSONObject(output.toString("UTF-8"))
            val row = backup.getJSONObject("tables").getJSONArray("nutrition_foods").getJSONObject(0)
            assertEquals(selected.brandId, row.getString("pricetrace_brand_id"))
            LocalDataBackupService(restored, context, "records-owner", "owner").restoreBackup(ByteArrayInputStream(output.toByteArray()))
            assertEquals(selected, NutritionCatalogRepository(restored, "owner").findFoodById(food.id)!!.priceTraceBrand)
            val malformed = JSONObject(backup.toString())
            malformed.getJSONObject("tables").getJSONArray("nutrition_foods").getJSONObject(0).remove("pricetrace_brand_id")
            assertThrows(IllegalArgumentException::class.java) {
                LocalDataBackupService(restored, context, "records-owner", "owner").restoreBackup(
                    ByteArrayInputStream(malformed.toString().toByteArray()))
            }
            assertEquals(selected, NutritionCatalogRepository(restored, "owner").findFoodById(food.id)!!.priceTraceBrand)
            listOf("pricetrace_brand_id", "pricetrace_brand_name_snapshot", "pricetrace_parent_brand_id_snapshot",
                "pricetrace_parent_brand_name_snapshot").forEach(row::remove)
            backup.put("databaseVersion", 56)
            LocalDataBackupService(legacy, context, "records-owner", "owner").restoreBackup(ByteArrayInputStream(backup.toString().toByteArray()))
            assertNull(NutritionCatalogRepository(legacy, "owner").findFoodById(food.id)!!.priceTraceBrand)
        } } }
    }
}
