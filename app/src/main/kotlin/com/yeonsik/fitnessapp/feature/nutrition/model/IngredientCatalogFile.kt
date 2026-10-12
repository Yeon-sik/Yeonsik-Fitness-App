package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionUnit
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

data class IngredientCatalogEntry(
    val externalId: String,
    val name: String,
    val basisAmount: Double,
    val basisUnit: String,
    val prepState: String,
    val profile: NutritionProfile
) {
    fun contentHash(): String = sha256(JSONObject().apply {
        put("name", name); put("basis_amount", basisAmount); put("basis_unit", basisUnit); put("prep_state", prepState)
        put("nutrients", JSONObject(profile.asMap().toSortedMap()))
    }.toString())
}

data class IngredientCatalogBatch(val catalogId: String, val revision: String, val entries: List<IngredientCatalogEntry>) {
    fun foodId(ownerId: String, entry: IngredientCatalogEntry): String =
        UUID.nameUUIDFromBytes("ingredient-file\u0000$ownerId\u0000$catalogId\u0000${entry.externalId}".toByteArray(StandardCharsets.UTF_8)).toString()

    fun food(ownerId: String, entry: IngredientCatalogEntry): NutritionFood = NutritionFood.builder()
        .id(foodId(ownerId, entry)).ownerId(ownerId).name(entry.name).kind(NutritionFood.KIND_INGREDIENT)
        .category(NutritionFood.CATEGORY_OTHER).basis(entry.basisAmount, entry.basisUnit).prepState(entry.prepState)
        .profile(entry.profile).source("user_file", JSONObject().apply {
            put("format", IngredientCatalogFile.FORMAT); put("catalog_id", catalogId); put("entry_id", entry.externalId)
            put("revision", revision); put("content_sha256", entry.contentHash())
        }.toString()).dataVersion(if (entry.profile.hasAllRequired()) NutritionFood.DATA_VERSION_REQUIRED_SEVEN else NutritionFood.DATA_VERSION_MACROS_ONLY)
        .build()
}

data class IngredientImportPreview(
    val ownerId: String,
    val batch: IngredientCatalogBatch,
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int
) {
    val changedCount: Int get() = newCount + updatedCount
}

/** One bounded, versioned format. Zero is known; absent optional nutrients remain unknown. */
object IngredientCatalogFile {
    const val FORMAT = "fitness.ingredient-catalog.v1"
    const val MAX_BYTES = 1_048_576
    const val MAX_ENTRIES = 2_000

    fun parse(text: String): IngredientCatalogBatch {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "재료 파일은 1MB 이하로 선택하세요." }
        val root = try { JSONObject(text.removePrefix("\uFEFF")) } catch (error: Exception) {
            throw IllegalArgumentException("JSON 형식을 확인하세요. 재료 파일 예시와 같은 구조가 필요합니다.", error)
        }
        require(root.optString("format") == FORMAT) { "지원하는 파일 형식은 $FORMAT 입니다." }
        val catalogId = identifier(root.opt("catalog_id"), "catalog_id")
        val revision = identifier(root.opt("revision"), "revision")
        val rows = root.optJSONArray("foods") ?: throw IllegalArgumentException("foods 배열이 필요합니다.")
        require(rows.length() in 1..MAX_ENTRIES) { "재료는 1개 이상 2,000개 이하로 제공하세요." }
        val ids = mutableSetOf<String>()
        val entries = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: throw IllegalArgumentException("${index + 1}번째 재료의 구조를 확인하세요.")
            try {
                val id = identifier(row.opt("id"), "id")
                require(ids.add(id)) { "중복된 id: $id" }
                val name = (row.opt("name") as? String)?.trim().orEmpty()
                require(name.isNotBlank() && name.length <= 120) { "name은 1~120자 이름이어야 합니다." }
                val basis = number(row, "basis_amount", required = true)!!
                require(basis > 0) { "basis_amount는 0보다 커야 합니다." }
                val unit = row.optString("basis_unit")
                require(unit == NutritionUnit.GRAM || unit == NutritionUnit.MILLILITER) { "basis_unit은 g 또는 ml로 지정하세요." }
                val prep = row.optString("prep_state")
                require(prep in listOf(NutritionFood.PREP_RAW, NutritionFood.PREP_COOKED, NutritionFood.PREP_AS_SERVED)) {
                    "prep_state는 raw, cooked, as_served 중 하나로 지정하세요."
                }
                val nutrients = row.optJSONObject("nutrients") ?: throw IllegalArgumentException("nutrients가 필요합니다.")
                val profile = NutritionProfile.builder()
                listOf(NutritionProfile.CALORIES_KCAL, NutritionProfile.CARBS_GRAMS, NutritionProfile.PROTEIN_GRAMS,
                    NutritionProfile.FAT_GRAMS, NutritionProfile.SODIUM_MG, NutritionProfile.SUGARS_GRAMS,
                    NutritionProfile.SATURATED_FAT_GRAMS).forEachIndexed { nutrientIndex, key ->
                    profile.value(key, number(nutrients, key, required = nutrientIndex < 4))
                }
                IngredientCatalogEntry(id, name, basis, unit, prep, profile.build())
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("${index + 1}번째 재료: ${error.message}", error)
            }
        }
        return IngredientCatalogBatch(catalogId, revision, entries)
    }

    private fun identifier(raw: Any?, label: String): String {
        val value = (raw as? String)?.trim().orEmpty()
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))) { "${label}은 영문·숫자·점·밑줄·하이픈으로 1~80자 지정하세요." }
        return value
    }

    private fun number(json: JSONObject, key: String, required: Boolean): Double? {
        if (!json.has(key) || json.isNull(key)) {
            require(!required) { "$key 값이 필요합니다." }
            return null
        }
        val value = (json.opt(key) as? Number)?.toDouble()
        require(value != null && value.isFinite() && value >= 0.0) { "$key 값은 0 이상의 숫자여야 합니다." }
        return value
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

