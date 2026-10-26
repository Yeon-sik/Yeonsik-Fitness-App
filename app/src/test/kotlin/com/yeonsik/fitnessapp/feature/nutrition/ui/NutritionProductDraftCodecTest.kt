package com.yeonsik.fitnessapp.feature.nutrition.ui

import com.yeonsik.fitnessapp.data.ProductReadV1
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class NutritionProductDraftCodecTest {
    @Test fun exactSelectionKeepsTheContractRevisionAndPackageIdentity() {
        val selected = product(FIRST, 100.0)
        val restored = requireNotNull(NutritionProductDraftCodec.decode(NutritionProductDraftCodec.encode(selected)))
        assertEquals(selected.catalogProductId, restored.catalogProductId)
        assertEquals(selected.standardProductId, restored.standardProductId)
        assertEquals(selected.revision, restored.revision)
        assertEquals(selected.contentAmount!!, restored.contentAmount!!, 0.0)
        assertEquals(selected.contentUnit, restored.contentUnit)
        assertEquals(selected.packageCount, restored.packageCount)
        assertEquals(selected.manufacturerName, restored.manufacturerName)
        assertEquals(selected.subBrandName, restored.subBrandName)
        assertNull(restored.latestObservedPriceKrw)
        assertTrue(restored.hasValidPriceTraceCatalogMetadata())
    }

    @Test fun groupedSelectionRetainsEveryVariantWithoutGuessingAnAmbiguousIdentity() {
        val grouped = ProductReadV1.search(listOf(product(FIRST, 130.0), product(SECOND, 210.0)), "", 20).single()
        val restored = requireNotNull(NutritionProductDraftCodec.decode(NutritionProductDraftCodec.encode(grouped)))
        assertFalse(restored.isExactCatalogProduct())
        assertEquals(2, restored.catalogVariantCount())
        assertEquals(FIRST, restored.exactVariantForBasis(130.0, "g").catalogProductId)
        assertEquals(SECOND, restored.exactVariantForBasis(210.0, "g").catalogProductId)
        assertNull(restored.exactVariantForBasis(100.0, "g"))
    }

    @Test fun aDraftCannotCombineDifferentStandardProductsWithTheSameDisplayName() {
        val first = JSONArray(NutritionProductDraftCodec.encode(product(FIRST, 100.0))).getJSONObject(0)
        val other = JSONArray(NutritionProductDraftCodec.encode(product(SECOND, 100.0))).getJSONObject(0)
            .put("standardProductId", "94444444-4444-4444-8444-444444444444")
        assertNull(NutritionProductDraftCodec.decode(JSONArray().put(first).put(other).toString()))
    }

    @Test fun corruptOrEmptySavedStateDoesNotRestoreASelection() {
        assertNull(NutritionProductDraftCodec.decode(null))
        assertNull(NutritionProductDraftCodec.decode("{broken"))
        assertNull(NutritionProductDraftCodec.decode("[]"))
    }

    private fun product(id: String, amount: Double) = ProductReadV1(id, STANDARD, "라벨", "브랜드",
        "제조회사", "서브브랜드", null, null, null, amount, "g", 1, "sha256:" + "a".repeat(64))

    private companion object {
        const val FIRST = "81111111-1111-4111-8111-111111111111"
        const val SECOND = "83333333-3333-4333-8333-333333333333"
        const val STANDARD = "82222222-2222-4222-8222-222222222222"
    }
}
