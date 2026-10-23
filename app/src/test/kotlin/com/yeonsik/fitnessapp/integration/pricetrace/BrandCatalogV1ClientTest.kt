package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Base64

class BrandCatalogV1ClientTest {
    private val id = "11111111-1111-4111-8111-111111111111"
    private val parent = "22222222-2222-4222-8222-222222222222"
    private fun row() = JSONObject().put("brand_id", id).put("canonical_name", "구체 브랜드")
        .put("parent_brand_id", parent).put("parent_canonical_name", JSONObject.NULL)
        .put("matched_name", "별칭").put("match_kind", "alias")
        .put("logo_url", JSONObject.NULL).put("official_site_url", JSONObject.NULL)
    private fun body(row: JSONObject) = JSONArray().put(row).toString()
    private fun config(key: String = "sb_publishable_fixture") = SupabaseConfig("https://fixture.supabase.co", key,
        "signed-in-user", "", "USER_ACCESS_TOKEN_MUST_NOT_BE_SENT", "", "fixture")

    @Test fun strictRowsPreserveOrderAliasAndUnknownParentLabel() {
        val first = row()
        val root = row().put("brand_id", parent).put("canonical_name", "상위 브랜드")
            .put("parent_brand_id", JSONObject.NULL).put("match_kind", "canonical")
        val rows = BrandCatalogV1Client.parse(JSONArray().put(first).put(root).toString())
        assertEquals(listOf(id, parent), rows.map { it.selection.brandId })
        assertEquals("alias", rows.first().matchKind)
        assertEquals(parent, rows.first().selection.parentBrandId)
        assertNull(rows.first().selection.parentCanonicalName)
        assertNull(rows.last().selection.parentBrandId)
    }

    @Test fun rejectsUnknownMissingMalformedOrInconsistentFields() {
        val malformed = listOf(row().put("extra", true), row().apply { remove("matched_name") },
            row().put("brand_id", "1-1-1-1-1"), row().put("canonical_name", 7),
            row().put("canonical_name", "  "), row().put("match_kind", "fuzzy"),
            row().put("parent_brand_id", "bad"), row().put("parent_brand_id", JSONObject.NULL).put("parent_canonical_name", "부모"))
        malformed.forEach { invalid -> assertThrows(Exception::class.java) { BrandCatalogV1Client.parse(body(invalid)) } }
        assertNull(PriceTraceBrandSelection.fromSnapshot(null, null, null, null))
        assertThrows(IllegalArgumentException::class.java) { PriceTraceBrandSelection.fromSnapshot(id, null, null, null) }
    }

    @Test fun sendsOnlyExactAnonymousRpcArgumentsAndBoundedPage() {
        var sent: BrandCatalogV1Client.Request? = null
        val client = BrandCatalogV1Client { request -> sent = request; body(row()) }
        client.search(config(), "  별칭  ", 25, 50)
        assertEquals("https://fixture.supabase.co${BrandCatalogV1Client.RPC}", sent!!.url)
        val args = JSONObject(sent!!.body)
        assertEquals(setOf("p_query", "p_limit", "p_offset"), args.keys().asSequence().toSet())
        assertEquals("별칭", args.getString("p_query"))
        assertEquals(25, args.getInt("p_limit")); assertEquals(50, args.getInt("p_offset"))
        assertFalse(sent!!.headers.containsKey("Authorization"))
        assertFalse(sent.toString().contains("USER_ACCESS_TOKEN"))
        assertTrue(JSONObject(BrandCatalogV1Client.request(config(), " ", 20, 0).body).isNull("p_query"))
        assertThrows(IllegalArgumentException::class.java) { client.search(config(), "", 51, 0) }
        assertThrows(IllegalArgumentException::class.java) { client.search(config(), "", 25, -1) }
    }

    @Test fun rejectsPrivilegedKeysAndAcceptsLegacyAnonWithoutSession() {
        fun jwt(role: String) = "eyJhbGciOiJIUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"role\":\"$role\"}".toByteArray()) + ".fixture"
        listOf("sb_secret_fixture", jwt("service_role"), jwt("authenticated"), "unknown-key").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { BrandCatalogV1Client.request(config(key), "", 25, 0) }
        }
        BrandCatalogV1Client.request(config(jwt("anon")).withoutSessionIdentity(), "", 25, 0)
    }

    @Test fun transportErrorsAreRecoverableAndDoNotBecomeEmptySuccess() {
        val client = BrandCatalogV1Client { throw IOException("offline") }
        assertThrows(IOException::class.java) { client.search(config(), "", 25, 0) }
        assertThrows(IOException::class.java) { BrandCatalogV1Client { "[{\"brand_id\":3}]" }.search(config(), "", 25, 0) }
    }
}
