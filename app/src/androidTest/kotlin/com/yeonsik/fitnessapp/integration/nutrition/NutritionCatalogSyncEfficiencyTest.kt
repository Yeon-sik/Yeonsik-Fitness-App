package com.yeonsik.fitnessapp.integration.nutrition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.ProductNutritionLink
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Real Room merge policy, synthetic HTTP only. No production account or endpoint is used. */
class NutritionCatalogSyncEfficiencyTest {
    private lateinit var database: FitnessRoomDatabase
    private lateinit var catalog: NutritionCatalogRepository
    private val server = CatalogServer()
    private val client = NutritionCatalogSyncClient { url -> server.connection(url) }

    @Before fun prepare() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnessRoomDatabase::class.java).build()
        catalog = NutritionCatalogRepository(database, OWNER)
    }
    @After fun clean() { database.close() }

    @Test fun unchangedAuthenticatedCatalogNeedsOnlySevenPagedReadsAndNoWrites() {
        catalog.applyRemoteFoodRows(listOf(food()))
        server.foods["food"] = jsonFood()
        val result = client.sync(config(), catalog)
        assertEquals(0, result.pushedRows)
        assertEquals(7, server.requests.size)
        assertTrue(server.requests.all { it.requestMethod == "GET" })
        assertEquals(7, client.sync(config(), catalog).let { server.requests.size - 7 })
    }

    @Test fun oneChangedFoodUsesTheFirstSnapshotAndRefreshesOnlyItsId() {
        catalog.applyRemoteFoodRows(listOf(food().copy(revision = 2, proteinGrams = 20.0, updatedAt = LATER)))
        server.foods["food"] = jsonFood()
        val result = client.sync(config(), catalog)
        assertEquals(1, result.pushedRows)
        assertEquals(9, server.requests.size)
        assertEquals(1, server.requests.count { it.requestMethod == "PATCH" })
        val refresh = server.requests.last().query()
        assertEquals("in.(\"food\")", refresh["id"])
        assertEquals(20.0, database.nutritionRoomDao().foodForSync("food")!!.proteinGrams!!, 0.0)
        assertFalse(server.requests.single { it.requestMethod == "PATCH" }.bodyJson().has("package_amount"))
    }

    @Test fun lostConditionalWritePullsTheWinningTombstoneAndPreservesPackaging() {
        catalog.applyRemoteFoodRows(listOf(food().copy(revision = 2, updatedAt = LATER)))
        server.foods["food"] = jsonFood()
        server.beforePatch = {
            server.foods["food"] = jsonFood().put("revision", 3).put("updated_at", LATER)
                .put("deleted_at", LATER).put("protein_grams", 30.0)
        }
        val result = client.sync(config(), catalog)
        assertEquals(0, result.pushedRows)
        assertEquals(9, server.requests.size)
        val winner = database.nutritionRoomDao().foodForSync("food")!!
        assertEquals(3, winner.revision)
        assertEquals(LATER, winner.deletedAt)
        assertEquals(500.0, winner.packageAmount!!, 0.0)
        assertEquals("g", winner.packageUnit)
        assertEquals(2L, winner.packageCount)
    }

    @Test fun duplicatePublicAndOwnedFoodsAreMergedOnceUsingTheNewerVersion() {
        server.foods["food"] = jsonFood().put("visibility", "public")
        val result = client.sync(config(), catalog)
        assertEquals(7, server.requests.size)
        assertEquals(1, result.pulledRows)
        assertEquals("public", database.nutritionRoomDao().foodForSync("food")!!.visibility)
        assertEquals(0, result.pushedRows)
    }

    @Test fun aNewPrivateFoodIsInsertedAndThenOnlyItsRemoteRowIsRefreshed() {
        catalog.applyRemoteFoodRows(listOf(food()))
        assertEquals(1, client.sync(config(), catalog).pushedRows)
        assertEquals(9, server.requests.size)
        assertEquals(1, server.requests.count { it.requestMethod == "POST" })
        assertEquals(0, client.sync(config(), catalog).pushedRows)
        assertEquals(16, server.requests.size)
    }

    @Test fun retiringALinkUpdatesTheSessionBeforeItsReplacementIsInserted() {
        val old = link("old", 2, LATER)
        val replacement = link("new", 1, null)
        server.links["old"] = JSONObject().put("id", "old").put("owner_id", OWNER)
            .put("nutrition_food_id", "fixture-food").put("catalog_product_id", "product-old")
            .put("status", "approved").put("source_type", ProductNutritionLink.SOURCE_MANUAL).put("revision", 1)
            .put("created_at", TIME).put("updated_at", TIME).put("deleted_at", JSONObject.NULL)
        val store = object : NutritionCatalogSyncStore {
            override fun exportSyncSnapshot(ownerId: String) = NutritionCatalogSyncSnapshot(
                emptyList(), emptyList(), emptyList(), listOf(old, replacement))
            override fun applyRemoteFoodRows(rows: List<NutritionFoodSyncRow>) = 0
            override fun applyRemoteNutrientRows(rows: List<NutritionNutrientSyncRow>) = 0
            override fun applyRemoteComponentRows(rows: List<NutritionComponentSyncRow>) = 0
            override fun applyRemoteProductLinkRows(rows: List<NutritionProductLinkSyncRow>) = 0
        }
        assertEquals(2, client.sync(config(), store).pushedRows)
        assertEquals(LATER, server.links["old"]!!.getString("deleted_at"))
        assertEquals("approved", server.links["new"]!!.getString("status"))
        assertEquals(11, server.requests.size)
        assertEquals(2, server.requests.count { it.requestMethod == "GET" && it.url.path.endsWith("product_nutrition_links") })
    }

    @Test fun ownerPaginationIsRetainedBeyondFiveHundredRows() {
        repeat(501) { server.foods["food-$it"] = jsonFood("food-$it") }
        client.sync(config(), catalog)
        assertEquals(501, database.nutritionRoomDao().syncFoods(OWNER).size)
        assertEquals(8, server.requests.size)
        assertTrue(server.requests.all { it.requestMethod == "GET" })
    }

    @Test fun publicConnectionDoesNotReadOrPushOwnerRows() {
        server.foods["public"] = jsonFood("public").put("owner_id", JSONObject.NULL).put("visibility", "public")
        val publicConfig = SupabaseConfig("https://nutrition.example", "test-key", "", "", "", "",
            SupabaseConfig.LOCAL_SETTINGS_SOURCE)
        client.sync(publicConfig, catalog)
        assertEquals(3, server.requests.size)
        assertTrue(server.requests.none { it.query()["owner_id"]?.startsWith("eq.") == true })
    }

    @Test fun targetedRefreshQuotesSpecialIdsAndBatchesLargeChangeSets() {
        val ids = (0 until 50).map { "food-$it" } + "food,\"quoted\\id"
        catalog.applyRemoteFoodRows(ids.map { food(it).copy(revision = 2, updatedAt = LATER) })
        ids.forEach { server.foods[it] = jsonFood(it) }
        assertEquals(51, client.sync(config(), catalog).pushedRows)
        val refreshes = server.requests.filter { it.query()["id"]?.startsWith("in.(") == true }
        assertEquals(2, refreshes.size)
        assertEquals(60, server.requests.size) // 7 initial reads, 51 writes, 2 targeted reads.
    }

    private fun config() = SupabaseConfig("https://nutrition.example", "test-key", OWNER,
        "fixture@example.com", "test-access", "test-refresh", SupabaseConfig.LOCAL_SETTINGS_SOURCE)

    private fun jsonFood(id: String = "food") = JSONObject().put("id", id).put("owner_id", OWNER)
        .put("name", "Fixture food").put("kind", "ingredient").put("category", "other")
        .put("basis_amount", 100).put("basis_unit", "g").put("prep_state", "unspecified")
        .put("cooking_method", "unspecified").put("source_type", "manual").put("visibility", "private")
        .put("revision", 1).put("data_version", 1).put("protein_grams", 10)
        .put("created_at", TIME).put("updated_at", TIME).put("deleted_at", JSONObject.NULL)

    private fun food(id: String = "food") = NutritionFoodSyncRow(
        id = id, ownerId = OWNER, name = "Fixture food", brand = null, manufacturerName = null,
        brandName = null, subBrandName = null, productName = null, packageAmount = 500.0,
        packageUnit = "g", packageCount = 2L, kind = "ingredient", category = "other", basisAmount = 100.0,
        basisUnit = "g", prepState = "unspecified", cookingMethod = "unspecified", caloriesKcal = null,
        proteinGrams = 10.0, carbsGrams = null, fatGrams = null, sodiumMg = null, saturatedFatGrams = null,
        sugarsGrams = null, fiberGrams = null, addedSugarsGrams = null, transFatGrams = null,
        cholesterolMg = null, sourceType = "manual", sourceReference = null, sourceVersion = null,
        dataVersion = 1, revision = 1, visibility = "private", createdAt = TIME, updatedAt = TIME, deletedAt = null)

    private fun link(id: String, revision: Int, deletedAt: String?) = NutritionProductLinkSyncRow(
        id, OWNER, "fixture-food", "product-$id", null, "approved", ProductNutritionLink.SOURCE_MANUAL, null, null,
        null, null, null, null, revision, null, TIME, LATER, deletedAt)

    private class CatalogServer {
        val requests = mutableListOf<FixtureConnection>()
        val foods = linkedMapOf<String, JSONObject>()
        val links = linkedMapOf<String, JSONObject>()
        var beforePatch: (() -> Unit)? = null

        fun connection(url: URL) = FixtureConnection(url) { connection ->
            val query = connection.query()
            val tableRows = when (url.path) {
                "/rest/v1/nutrition_foods" -> foods
                "/rest/v1/product_nutrition_links" -> links
                else -> return@FixtureConnection "[]"
            }
            when (connection.requestMethod) {
                "GET" -> {
                    var rows = tableRows.values.toList()
                    query["visibility"]?.let { value -> rows = rows.filter { it.optString("visibility") == value.removePrefix("eq.") } }
                    query["owner_id"]?.let { value -> rows = rows.filter { it.optString("owner_id") == value.removePrefix("eq.") } }
                    query["id"]?.let { value ->
                        val ids = JSONArray("[" + value.removePrefix("in.(").removeSuffix(")") + "]")
                        val selected = (0 until ids.length()).map { ids.getString(it) }.toSet()
                        rows = rows.filter { it.getString("id") in selected }
                    }
                    JSONArray(rows.sortedBy { it.getString("id") }.drop(query["offset"]!!.toInt())
                        .take(query["limit"]!!.toInt())).toString()
                }
                "PATCH" -> {
                    beforePatch?.also { beforePatch = null }?.invoke()
                    val id = query["id"]!!.removePrefix("eq.")
                    val remote = tableRows[id]!!
                    if (query["revision"] != "eq.${remote.getInt("revision")}") return@FixtureConnection "[]"
                    val patch = connection.bodyJson()
                    patch.keys().forEach { remote.put(it, patch.get(it)) }
                    JSONArray().put(remote).toString()
                }
                "POST" -> {
                    val inserted = JSONArray()
                    val rows = JSONArray(connection.bodyText())
                    repeat(rows.length()) { index ->
                        val row = rows.getJSONObject(index)
                        if (!tableRows.containsKey(row.getString("id"))) {
                            tableRows[row.getString("id")] = row
                            inserted.put(row)
                        }
                    }
                    inserted.toString()
                }
                else -> error("Unexpected fixture request: ${connection.requestMethod}")
            }
        }.also { requests.add(it) }
    }

    private class FixtureConnection(url: URL, private val response: (FixtureConnection) -> String) : HttpURLConnection(url) {
        private val body = ByteArrayOutputStream()
        private var responseBody: String? = null
        override fun setRequestMethod(value: String) { method = value }
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getOutputStream() = body
        override fun getResponseCode(): Int { responseBody = response(this); return 200 }
        override fun getInputStream() = ByteArrayInputStream(responseBody!!.toByteArray(Charsets.UTF_8))
        fun bodyText() = body.toString("UTF-8")
        fun bodyJson() = JSONObject(bodyText())
        fun query(): Map<String, String> = url.query.split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
    }

    companion object {
        private const val OWNER = "fixture-owner"
        private const val TIME = "2026-10-20T00:00:00Z"
        private const val LATER = "2026-10-20T01:00:00Z"
    }
}
