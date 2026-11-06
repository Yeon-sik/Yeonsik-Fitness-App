package com.yeonsik.fitnessapp.integration.nutrition

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.feature.information.model.InformationRecordCodec
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class NutritionMealPage(val meals: List<JsonObject>, val nextCursor: String?)

/** GET-only intake. Credentials and local profile IDs never enter the imported payload. */
class NutritionMealReviewClient(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    fun pull(config: SupabaseConfig, beforeId: String? = null): NutritionMealPage {
        require(config.isConfigured) { "설정에서 Nutrition 계정에 로그인한 뒤 다시 가져오세요." }
        val base = URI(config.supabaseUrl)
        require(base.scheme == "https" && !base.host.isNullOrBlank() && base.userInfo == null &&
            base.rawQuery == null && base.rawFragment == null) { "Nutrition 연결 주소를 확인하세요." }
        require(Regex("[0-9a-fA-F-]{36}").matches(config.userId)) { "Nutrition 계정 ID를 확인하세요." }
        // The verified-import FK is unique: PostgREST returns an object (or null), not an array.
        val selection = "*,meal_record_items(*,meal_record_item_nutrients(*)),meal_verified_imports(*)"
        val query = buildString {
            append("select=").append(encode(selection))
            append("&owner_id=eq.").append(encode(config.userId))
            append("&order=id.desc&limit=").append(PAGE_SIZE)
            if (beforeId != null) {
                require(beforeId.isNotBlank() && beforeId.length <= 200)
                append("&id=lt.").append(encode("\"" + beforeId.replace("\\", "\\\\").replace("\"", "\\\"") + "\""))
            }
        }
        val connection = open(URL(config.supabaseUrl.trimEnd('/') + "/rest/v1/meal_records?" + query))
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", config.supabaseAnonKey)
            connection.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                200, 206 -> Unit
                401 -> error("Nutrition 로그인이 만료되었습니다. 설정의 Nutrition 계정에서 다시 로그인한 뒤 가져오세요. 기존 기록과 초안은 유지됩니다.")
                403 -> error("Nutrition 식사 자료를 읽을 권한이 없습니다. 설정의 Nutrition 계정을 확인하세요.")
                else -> error("Nutrition 식사 자료를 읽지 못했습니다 (HTTP $code). 연결과 식사 수신 계약을 확인한 뒤 다시 시도하세요.")
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE_BYTES) { "원격 식사 응답이 너무 큽니다. 자료를 분할한 뒤 다시 가져오세요." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return decodePage(text, config.userId, beforeId)
        } finally { connection.disconnect() }
    }

    companion object {
        const val PAGE_SIZE = 10
        const val MAX_RESPONSE_BYTES = 12 * 1_048_576
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        fun decodePage(text: String, owner: String, beforeId: String? = null): NutritionMealPage {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES)
            // Enforce the same no-loss guard before parsing the raw remote snapshot too.
            InformationRecordCodec.validateStructureBeforeParsing(text)
            val rows = Json.parseToJsonElement(text) as? JsonArray ?: error("원격 식사 응답 형식이 올바르지 않습니다.")
            require(rows.size <= PAGE_SIZE)
            val meals = rows.map { normalizeMeal(it.jsonObject, owner) }
            val ids = meals.map { it.requiredText("id") }
            require(ids.distinct().size == ids.size) { "원격 식사 ID가 중복되었습니다." }
            // Server controls collation, so only enforce the cursor does not repeat itself locally.
            require(beforeId == null || beforeId !in ids) { "원격 식사 페이지가 반복되었습니다." }
            return NutritionMealPage(meals, ids.lastOrNull().takeIf { ids.size == PAGE_SIZE })
        }

        private fun normalizeMeal(meal: JsonObject, owner: String): JsonObject {
            require(meal.requiredText("owner_id") == owner) { "다른 계정의 식사 자료는 가져올 수 없습니다." }
            val id = meal.requiredText("id").also { require(it.length <= 200) }
            require(meal.requiredText("scope") == "fitness") { "Fitness 소유 식사 자료만 가져올 수 있습니다." }
            val items = meal["meal_record_items"] as? JsonArray ?: error("원격 식사 항목이 누락되었습니다.")
            val normalizedItems = items.map { raw ->
                val item = raw.jsonObject
                requireChild(item, owner, id)
                val itemId = item.requiredText("id")
                val nutrients = item["meal_record_item_nutrients"] as? JsonArray ?: error("원격 영양 항목이 누락되었습니다.")
                val normalizedNutrients = nutrients.map { value -> value.jsonObject.also {
                    requireChild(it, owner, id)
                    require(it.requiredText("meal_record_item_id") == itemId) { "영양 항목의 식사 연결이 다릅니다." }
                } }
                JsonObject(item + ("meal_record_item_nutrients" to sortedRows(normalizedNutrients)))
            }
            val receipt = meal["meal_verified_imports"] ?: error("원격 수신 출처가 누락되었습니다.")
            if (receipt != JsonNull) requireChild(receipt.jsonObject, owner, id)
            return JsonObject(meal + ("meal_record_items" to sortedRows(normalizedItems)))
        }

        private fun requireChild(row: JsonObject, owner: String, meal: String) {
            require(row.requiredText("owner_id") == owner && row.requiredText("meal_record_id") == meal) {
                "다른 계정 또는 식사의 하위 자료가 섞여 있습니다."
            }
        }
        private fun sortedRows(rows: List<JsonObject>): JsonArray {
            require(rows.map { it.requiredText("id") }.distinct().size == rows.size) { "원격 하위 자료 ID가 중복되었습니다." }
            return JsonArray(rows.sortedBy { it.requiredText("id") })
        }
        internal fun JsonObject.requiredText(key: String): String {
            val value = get(key) as? JsonPrimitive
            require(value != null && value.isString && value.content.isNotBlank()) { "원격 자료의 $key 값을 확인하세요." }
            return value.content
        }
    }
}
