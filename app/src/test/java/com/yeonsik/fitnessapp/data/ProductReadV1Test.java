package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ProductReadV1Test {
    private static final String CATALOG_ID = "11111111-1111-4111-8111-111111111111";
    private static final String STANDARD_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void adaptsCurrentPriceTraceRpcWithoutInferringIdentityFromName() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("catalog_product_id", CATALOG_ID);
        row.put("standard_product_id", STANDARD_ID);
        row.put("standard_name", "닭가슴살 100g");
        row.put("content_amount", 100.0);
        row.put("content_unit", "g");
        row.put("package_count", 1);
        row.put("coupang_listed_price_krw", 3200);
        row.put("coupang_observed_at", "2026-08-09T00:00:00Z");

        ProductReadV1 product = ProductReadV1.fromMap(row);

        assertEquals(CATALOG_ID, product.catalogProductId);
        assertEquals("닭가슴살 100g", product.name);
        assertEquals("쿠팡", product.sellerName);
        assertEquals(Integer.valueOf(3200), product.latestObservedPriceKrw);
        String label = product.exactSelectionLabel();
        assertTrue(label.contains("catalogProductId: " + CATALOG_ID));
        assertTrue(label.contains("판매처 쿠팡"));
        assertTrue(label.contains("최근 관측가 3,200원"));
        assertTrue(label.contains("관측 2026-08-09T00:00:00Z"));
    }

    @Test
    public void keepsAbsentPriceAndObservedAtNullInsteadOfZero() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("contract_version", ProductReadV1.CONTRACT_VERSION);
        row.put("catalogProductId", CATALOG_ID);
        row.put("name", "가격 미관측 상품");
        row.put("sellerName", "PX");

        ProductReadV1 product = ProductReadV1.fromMap(row);

        assertNull(product.latestObservedPriceKrw);
        assertNull(product.observedAt);
        assertTrue(product.priceObservationLabel().contains("최근 관측가 없음"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPartialPriceObservationTuple() {
        new ProductReadV1(
                CATALOG_ID,
                STANDARD_ID,
                "불완전 가격",
                "쿠팡",
                1000,
                null,
                100.0,
                "g",
                1
        );
    }

    @Test
    public void nameSearchDeduplicatesByExactCatalogProductId() {
        ProductReadV1 withoutPrice = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "PX", null, null, 100.0, "g", 1
        );
        ProductReadV1 withPrice = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "쿠팡", 2500,
                "2026-08-09T00:00:00Z", 100.0, "g", 1
        );

        List<ProductReadV1> results = ProductReadV1.search(
                Arrays.asList(withoutPrice, withPrice),
                "닭가슴",
                50
        );

        assertEquals(1, results.size());
        assertEquals(Integer.valueOf(2500), results.get(0).latestObservedPriceKrw);
    }
}
