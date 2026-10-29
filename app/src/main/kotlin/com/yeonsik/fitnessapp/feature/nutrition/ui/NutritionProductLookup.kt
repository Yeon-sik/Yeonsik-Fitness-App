package com.yeonsik.fitnessapp.feature.nutrition.ui

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.ProductReadV1
import org.json.JSONArray
import org.json.JSONObject

/** Read-only product lookup; selection and registration remain owned by the editor. */
interface NutritionProductLookup {
    fun search(query: String): List<ProductReadV1>
    fun nutrition(product: ProductReadV1): ProductNutritionPreview? = null
}

data class ProductNutritionPreview(val basisAmount: Double, val basisUnit: String, val profile: NutritionProfile)

data class NutritionProductPickerState(
    val query: String = "",
    val results: List<ProductReadV1> = emptyList(),
    val selected: ProductReadV1? = null,
    val manualFields: Map<FoodEntryField, String> = emptyMap(),
    val manualUnit: String = "",
    val manualPackageAmount: String = "",
    val searched: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

/** Save only contract values, including every child of a grouped standard-product result. */
internal object NutritionProductDraftCodec {
    fun encode(product: ProductReadV1): String = JSONArray().apply {
        product.exactCatalogVariants().forEach { item ->
            put(JSONObject().apply {
                put("catalogProductId", item.catalogProductId); put("standardProductId", item.standardProductId)
                put("name", item.name); put("brand", item.brand)
                put("manufacturerName", item.manufacturerName); put("subBrandName", item.subBrandName)
                put("sellerName", item.sellerName); put("latestObservedPriceKrw", item.latestObservedPriceKrw as Any?)
                put("observedAt", item.observedAt); put("revision", item.revision)
                put("contentAmount", item.contentAmount as Any?); put("contentUnit", item.contentUnit); put("packageCount", item.packageCount as Any?)
            })
        }
    }.toString()

    fun decode(value: String?): ProductReadV1? = value?.let { encoded -> runCatching {
        val rows = JSONArray(encoded)
        require(rows.length() in 1..1000)
        val products = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ProductReadV1.fromMap(row.keys().asSequence().associateWith { key -> row.opt(key).takeUnless { it == JSONObject.NULL } })
        }
        require(products.size == 1 || (products.map { it.standardProductId }.distinct().size == 1 && products.first().standardProductId != null))
        if (products.size == 1) products.single() else ProductReadV1.search(products, "", 1).single()
    }.getOrNull() }
}
