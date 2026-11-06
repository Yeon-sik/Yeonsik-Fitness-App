package com.yeonsik.fitnessapp.integration.nutrition

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.feature.information.model.InformationRecordCodec
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class NutritionMealReviewClientTest {
    private val owner = "12345678-1234-4234-8234-1234567890ab"
    private val config = SupabaseConfig("https://nutrition.example", "public-test-key", owner, "owner@example.test", "token", "refresh", "test")
    private val source = NutritionMealSource(config.supabaseUrl, owner)
    private fun meal(id: String = "meal-1") = """{"id":"$id","owner_id":"$owner","scope":"fitness","menu":"원문 식사","eaten_at":"2026-11-06T20:00:00+09:00","calories":null,"protein_grams":12345678901234567890.0010,"deleted_at":null,"meal_record_items":[{"id":"item-1","owner_id":"$owner","meal_record_id":"$id","calories":null,"meal_record_item_nutrients":[{"id":"nutrient-1","owner_id":"$owner","meal_record_id":"$id","meal_record_item_id":"item-1","amount":0.0010}]}],"meal_verified_imports":{"id":"receipt-1","owner_id":"$owner","meal_record_id":"$id","request_payload":{"evidence":"출처 원문"}}}"""
    private fun page(text: String = meal()) = NutritionMealReviewClient.decodePage("[$text]", owner)

    @Test fun onlyOwnerFilteredGetIncludesWholeSnapshotAndSendsNoMutation() {
        val connection = FakeConnection(URL("https://nutrition.example"), "[${meal()}]")
        var requested: URL? = null
        val result = NutritionMealReviewClient { requested = it; connection }.pull(config)
        assertEquals("GET", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.doOutput)
        assertEquals("Bearer token", connection.getRequestProperty("Authorization"))
        val query = URLDecoder.decode(requested!!.query, "UTF-8")
        assertTrue(query.contains("owner_id=eq.$owner"))
        assertTrue(query.contains("meal_record_items(*,meal_record_item_nutrients(*))"))
        assertTrue(query.contains("meal_verified_imports(*)"))
        assertTrue(connection.closed)
        assertEquals(1, result.meals.size)
    }

    @Test fun nullablePrecisionProvenanceAndContentRevisionSurvive() {
        val snapshot = page().meals.single()
        val first = NutritionMealReviewEnvelope.create(source, snapshot, "2026-11-06T12:00:00Z")
        val roundtrip = InformationRecordCodec.decode(InformationRecordCodec.pretty(first))
        val saved = roundtrip.payload.getValue("fields").jsonArray[1].jsonObject.getValue("value").jsonObject
        assertEquals(JsonNull, saved["calories"])
        assertEquals("12345678901234567890.0010", saved["protein_grams"]!!.jsonPrimitive.content)
        assertTrue(first.canonical.contains("출처 원문"))
        assertTrue(first.canonical.contains("0.0010"))
        assertEquals(first.id, NutritionMealReviewEnvelope.id(source, snapshot))
        assertNotEquals(first.id, NutritionMealReviewEnvelope.id(source.copy(owner = "other"), snapshot))
        assertNotEquals(first.id, NutritionMealReviewEnvelope.id(source.copy(project = "https://another.example"), snapshot))
        assertNotEquals(first.id, NutritionMealReviewEnvelope.id(source, JsonObject(snapshot + ("calories" to JsonPrimitive(0)))))
    }

    @Test fun rejectForeignOwnersWrongParentsMissingRowsAndDuplicateIds() {
        val raw = meal()
        listOf(raw.replaceFirst(owner, "different"),
            raw.replace("\"meal_record_id\":\"meal-1\"", "\"meal_record_id\":\"other\""),
            raw.replace("\"meal_record_item_id\":\"item-1\"", "\"meal_record_item_id\":\"other\""),
            raw.replace("\"meal_record_items\"", "\"missing_items\""),
            raw.replace("\"scope\":\"fitness\"", "\"scope\":\"healthtrace\""))
            .forEach { invalid -> assertThrows(Exception::class.java) { page(invalid) } }
        assertThrows(Exception::class.java) { page("$raw,$raw") }
    }

    @Test fun fullPageHasCursorAndSmallerPageFinishesWithoutOffsetLoss() {
        val text = (1..NutritionMealReviewClient.PAGE_SIZE).reversed().joinToString(",", "[", "]") { meal("meal-$it") }
        val result = NutritionMealReviewClient.decodePage(text, owner)
        assertEquals("meal-1", result.nextCursor)
        assertNull(page().nextCursor)
        val connection = FakeConnection(URL(config.supabaseUrl), "[]")
        var requested: URL? = null
        NutritionMealReviewClient { requested = it; connection }.pull(config, "meal-1")
        assertTrue(URLDecoder.decode(requested!!.query, "UTF-8").contains("id=lt.\"meal-1\""))
        assertThrows(Exception::class.java) { NutritionMealReviewClient.decodePage("[${meal()}]", owner, "meal-1") }
    }

    @Test fun authenticationFailuresRedirectsAndHugeNestingFailWithoutEchoingRemoteBody() {
        listOf(401, 403, 302, 500).forEach { status ->
            val connection = FakeConnection(URL(config.supabaseUrl), "secret response", status)
            val failure = assertThrows(IllegalStateException::class.java) { NutritionMealReviewClient { connection }.pull(config) }
            assertFalse(failure.message!!.contains("secret response"))
            if (status == 401) assertTrue(failure.message!!.contains("다시 로그인"))
            assertTrue(connection.closed)
        }
        assertThrows(IllegalArgumentException::class.java) { NutritionMealReviewClient.decodePage("[".repeat(50_000) + "]".repeat(50_000), owner) }
    }

    @Test fun childRowResponseOrderDoesNotInventARevision() {
        val snapshot = page().meals.single()
        val item = snapshot.getValue("meal_record_items").jsonArray.single().jsonObject
        val second = JsonObject(item + mapOf("id" to JsonPrimitive("item-2"), "meal_record_item_nutrients" to JsonArray(emptyList())))
        val firstOrder = JsonObject(snapshot + ("meal_record_items" to JsonArray(listOf(item, second))))
        val reverseOrder = JsonObject(snapshot + ("meal_record_items" to JsonArray(listOf(second, item))))
        val first = page(firstOrder.toString()).meals.single()
        val reversed = page(reverseOrder.toString()).meals.single()
        assertEquals(NutritionMealReviewEnvelope.id(source, first), NutritionMealReviewEnvelope.id(source, reversed))
    }

    @Test fun duplicateRawRemoteKeysAreRejectedBeforeWrappingTheSnapshot() {
        val original = meal()
        for (duplicate in listOf(
            original.replaceFirst("\"menu\":", "\"menu\":\"discarded\",\"menu\":"),
            original.replace("\"amount\":", "\"amount\":1,\"amount\":"),
            original.replace("\"amount\":", "\"\\u0061mount\":1,\"amount\":"))) {
            assertThrows(IllegalArgumentException::class.java) { page(duplicate) }
        }
    }

    private class FakeConnection(url: URL, private val response: String, private val status: Int = 200) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = response.byteInputStream()
    }
}
