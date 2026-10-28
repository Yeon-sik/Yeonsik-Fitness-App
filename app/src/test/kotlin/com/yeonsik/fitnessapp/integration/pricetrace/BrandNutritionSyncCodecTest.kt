package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.integration.nutrition.NutritionCatalogSyncClient
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodSyncRow
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BrandNutritionSyncCodecTest {
    private val fields = listOf("pricetrace_brand_id", "pricetrace_brand_name_snapshot",
        "pricetrace_parent_brand_id_snapshot", "pricetrace_parent_brand_name_snapshot")
    @Suppress("UNCHECKED_CAST")
    private fun parse(json: JSONObject): List<NutritionFoodSyncRow> = NutritionCatalogSyncClient::class.java
        .getDeclaredMethod("parseFoodRows", JSONArray::class.java).apply { isAccessible = true }
        .invoke(null, JSONArray().put(json)) as List<NutritionFoodSyncRow>
    private fun encode(rows: List<NutritionFoodSyncRow>) = NutritionCatalogSyncClient::class.java
        .getDeclaredMethod("toFoodJson", List::class.java).apply { isAccessible = true }.invoke(null, rows) as JSONArray

    @Test fun oldRowsReadAsNullAndAllFourNullsAreWrittenForExplicitClear() {
        val rows = parse(JSONObject().put("id", "food").put("name", "제품"))
        assertNull(rows.single().priceTraceBrand)
        assertNull(rows.single().priceTraceBrandId)
        val encoded = encode(rows).getJSONObject(0)
        fields.forEach { assertTrue(encoded.has(it)); assertTrue(encoded.isNull(it)) }
    }

    @Test fun selectedSubBrandRoundTripsAndRemoteNullClearsEverySnapshot() {
        val selection = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "canonical",
            "22222222-2222-4222-8222-222222222222", null)
        val initial = parse(JSONObject().put("id", "food").put("name", "제품").put("brand", "라벨 브랜드"))
        val encoded = encode(listOf(initial.single().copy(priceTraceBrand = selection, revision = 7))).getJSONObject(0)
        val decoded = parse(encoded).single()
        assertEquals(selection, decoded.priceTraceBrand)
        assertEquals(selection.brandId, decoded.priceTraceBrandId)
        assertEquals("라벨 브랜드", decoded.brand)
        assertEquals(7, decoded.revision)
        fields.forEach { encoded.put(it, JSONObject.NULL) }
        assertNull(parse(encoded).single().priceTraceBrand)
    }

    @Test fun incompleteOrNonTextBrandSnapshotFailsInsteadOfSilentlyLosingProvenance() {
        val base = JSONObject().put("id", "food").put("name", "제품")
        assertThrows(Exception::class.java) { parse(JSONObject(base.toString()).put("pricetrace_brand_name_snapshot", "이름만")) }
        assertThrows(Exception::class.java) { parse(JSONObject(base.toString())
            .put("pricetrace_brand_id", "11111111-1111-4111-8111-111111111111").put("pricetrace_brand_name_snapshot", 42)) }
    }

    @Test fun missingRemoteBrandColumnsFailWithAnActionableError() {
        val connection = object : java.net.HttpURLConnection(java.net.URL("https://fixture.supabase.co/rest/v1/nutrition_foods")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 400
            override fun getErrorStream() = """{"code":"PGRST204","message":"Could not find pricetrace_brand_id column"}""".byteInputStream()
        }
        val method = NutritionCatalogSyncClient::class.java.getDeclaredMethod("readResponseOrThrow",
            java.net.HttpURLConnection::class.java, IntArray::class.java).apply { isAccessible = true }
        val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { method.invoke(null, connection, intArrayOf(200)) }
        assertTrue(error.cause is java.io.IOException)
        assertTrue(error.cause!!.message!!.contains("DB 업데이트"))
        assertTrue(error.cause!!.message!!.contains("로컬 기록은 유지"))
    }
}
