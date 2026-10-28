package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.config.SupabaseConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class BrandReadV1ClientTest {
    private val body = """{"contract_version":"brand_read.v1","brands":[{"brand_id":"11111111-1111-4111-8111-111111111111","canonical_name":"농심","matched_alias":"Nongshim"}]}"""
    @Test fun usesBrandRpcAndExplicitQueryWithoutSendingUserToken() {
        val sent = ByteArrayOutputStream()
        var request: HttpURLConnection? = null
        val client = BrandReadV1Client(SupabaseConfig("https://fixture.supabase.co", "sb_publishable_fixture", "owner", "", "private-user-token", "", "fixture")) { url ->
            object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
                override fun getOutputStream() = sent
            }.also { request = it }
        }
        assertEquals("농심", client.searchBrands(" Nongshim ").single().canonicalName)
        assertEquals(BrandReadV1Client.RPC, request!!.url.path)
        assertEquals("Nongshim", JSONObject(sent.toString()).getString("p_query"))
        assertEquals(20, JSONObject(sent.toString()).getInt("p_limit"))
        assertNull(request!!.getRequestProperty("Authorization"))
    }
    @Test fun blankQueriesDoNotNeedConnectionAndWrongContractsAreFailures() {
        val client = BrandReadV1Client(SupabaseConfig.empty()) { _: URL -> throw AssertionError("Must not connect") }
        assertTrue(client.searchBrands("  ").isEmpty())
        assertThrows(IOException::class.java) { BrandReadV1Client.parse(body.replace("brand_read.v1", "brand_read.v2")) }
    }
}
