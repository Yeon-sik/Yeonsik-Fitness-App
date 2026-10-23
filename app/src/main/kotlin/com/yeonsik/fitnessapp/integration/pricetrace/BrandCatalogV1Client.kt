package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandRow
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

/** 공개 Brand RPC 전용이다. Nutrition/PriceTrace 사용자 access token을 사용하지 않는다. */
class BrandCatalogV1Client @JvmOverloads constructor(private val transport: Transport = HttpTransport()) {
    data class Request(val url: String, val headers: Map<String, String>, val body: String)
    fun interface Transport { fun post(request: Request): String }

    @Throws(Exception::class)
    fun search(config: SupabaseConfig, query: String?, limit: Int, offset: Int): List<PriceTraceBrandRow> {
        val request = request(config, query, limit, offset)
        val body = try { transport.post(request) } catch (error: Exception) {
            throw IOException("PriceTrace 브랜드를 불러오지 못했습니다. 연결을 확인하고 다시 시도하세요.", error)
        }
        return try { parse(body).also { require(it.size <= limit) } } catch (error: Exception) {
            throw IOException("PriceTrace 브랜드 응답 형식을 확인하지 못했습니다.", error)
        }
    }

    companion object {
        const val RPC = "/rest/v1/rpc/get_public_brand_catalog_v1"
        private val FIELDS = setOf("brand_id", "canonical_name", "parent_brand_id", "parent_canonical_name",
            "matched_name", "match_kind", "logo_url", "official_site_url")

        @JvmStatic
        fun request(config: SupabaseConfig, query: String?, limit: Int, offset: Int): Request {
            require(config.isConnectionConfigured) { "설정에서 PriceTrace 공개 DB 연결을 먼저 설정하세요." }
            val uri = URI(config.supabaseUrl)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
            requirePublicKey(config.supabaseAnonKey)
            require(limit in 20..50 && offset >= 0) { "Invalid Brand page" }
            val args = JSONObject().put("p_query", query?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
                .put("p_limit", limit).put("p_offset", offset)
            // Publishable keys are apikey values, not JWT bearer tokens.
            return Request(config.supabaseUrl.trimEnd('/') + RPC, mapOf("apikey" to config.supabaseAnonKey,
                "Content-Type" to "application/json; charset=utf-8", "Accept" to "application/json"), args.toString())
        }

        @JvmStatic
        fun requirePublicKey(key: String) {
            require(!key.startsWith("sb_secret_")) { "PriceTrace 공개 키만 사용할 수 있습니다." }
            if (key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length) return
            val parts = key.split('.')
            require(parts.size == 3) { "PriceTrace 공개 키만 사용할 수 있습니다." }
            val role = runCatching { JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).getString("role") }.getOrNull()
            require(role == "anon") { "PriceTrace anon/publishable 키만 사용할 수 있습니다." }
        }

        @JvmStatic
        fun parse(body: String): List<PriceTraceBrandRow> {
            val rows = JSONArray(body)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                require(row.keys().asSequence().toSet() == FIELDS) { "Unexpected Brand wire fields" }
                val selection = PriceTraceBrandSelection(text(row, "brand_id"), text(row, "canonical_name"),
                    nullableText(row, "parent_brand_id"), nullableText(row, "parent_canonical_name"))
                val kind = text(row, "match_kind")
                require(kind == "canonical" || kind == "alias")
                PriceTraceBrandRow(selection, text(row, "matched_name"), kind,
                    nullableText(row, "logo_url"), nullableText(row, "official_site_url"))
            }
        }

        private fun text(row: JSONObject, key: String): String {
            val value = row.get(key)
            require(value is String && value.isNotBlank()) { "Invalid Brand $key" }
            return value
        }
        private fun nullableText(row: JSONObject, key: String): String? = if (row.isNull(key)) null else text(row, key)
    }

    private class HttpTransport : Transport {
        override fun post(request: Request): String {
            val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                request.headers.forEach(connection::setRequestProperty)
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) throw IOException("Brand RPC HTTP ${connection.responseCode}")
                return connection.inputStream.use {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 1_048_576) { "Brand response too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } finally { connection.disconnect() }
        }
    }
}
