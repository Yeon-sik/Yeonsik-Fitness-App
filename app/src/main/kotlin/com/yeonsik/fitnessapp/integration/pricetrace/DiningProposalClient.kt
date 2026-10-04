package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.config.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface DiningProposalRemote {
    fun submit(config: SupabaseConfig, kind: String, request: JSONObject): JSONObject
    fun read(config: SupabaseConfig, kind: String): List<JSONObject>
}

/** Only sanitized proposal facts cross this boundary. No Nutrition values or canonical writes. */
class DiningProposalClient : DiningProposalRemote {
    override fun submit(config: SupabaseConfig, kind: String, request: JSONObject): JSONObject =
        JSONObject(post(config, when (kind) {
            "merchant" -> "submit_merchant_identity_candidate_v1"
            "menu" -> "submit_restaurant_menu_candidate_v1"
            else -> error("지원하지 않는 PT 제안 종류입니다.")
        }, request))

    override fun read(config: SupabaseConfig, kind: String): List<JSONObject> {
        val array = JSONArray(post(config, when (kind) {
            "merchant" -> "get_my_dining_merchant_candidates_v1"
            "menu" -> "get_my_restaurant_menu_candidates_v1"
            else -> error("지원하지 않는 PT 제안 종류입니다.")
        }, JSONObject()))
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun post(config: SupabaseConfig, rpc: String, request: JSONObject): String {
        check(config.isConfigured) { "PT 계정 로그인이 필요합니다." }
        val connection = URL("${config.supabaseUrl.trimEnd('/')}/rest/v1/rpc/$rpc")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", config.supabaseAnonKey)
            connection.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Never expose tokens, submitted facts or SQL details in a UI error.
                throw IOException(if (code == 409) "같은 제안 키의 내용이 다릅니다. 기존 제안을 새로고침하세요."
                    else "PT 등록 제안 요청에 실패했습니다. (HTTP $code)")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
