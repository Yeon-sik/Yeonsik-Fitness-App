package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ProductReadV1Test {
    private static final String CATALOG_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND_CATALOG_ID = "33333333-3333-4333-8333-333333333333";
    private static final String STANDARD_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void adaptsCurrentPriceTraceRpcWithoutInferringIdentityFromName() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("catalog_product_id", CATALOG_ID);
        row.put("standard_product_id", STANDARD_ID);
        row.put("brand_name", "버거킹");
        row.put("standard_name", "닭가슴살 100g");
        row.put("content_amount", 100.0);
        row.put("content_unit", "g");
        row.put("package_count", 1);
        row.put("coupang_listed_price_krw", 3200);
        row.put("coupang_observed_at", "2026-08-09T00:00:00Z");

        ProductReadV1 product = ProductReadV1.fromMap(row);

        assertEquals(CATALOG_ID, product.catalogProductId);
        assertEquals("버거킹", product.brand);
        assertEquals("닭가슴살 100g", product.name);
        assertEquals("쿠팡", product.sellerName);
        assertEquals(Integer.valueOf(3200), product.latestObservedPriceKrw);
        String label = product.exactSelectionLabel();
        assertTrue(label.contains(product.specificationLabel()));
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
    public void standardProductSearchDeduplicatesObservationsForTheSameCatalogChild() {
        ProductReadV1 withoutPrice = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "버거킹", "PX", null, null, 100.0, "g", 1
        );
        ProductReadV1 withPrice = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "버거킹", "쿠팡", 2500,
                "2026-08-09T00:00:00Z", 100.0, "g", 1
        );

        List<ProductReadV1> results = ProductReadV1.search(
                Arrays.asList(withPrice, withoutPrice),
                "닭가슴",
                50
        );

        assertEquals(1, results.size());
        assertEquals(Integer.valueOf(2500), results.get(0).latestObservedPriceKrw);
    }

    @Test
    public void standardProductSearchKeepsTheNewestPricedObservationRegardlessOfOrder() {
        ProductReadV1 older = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "버거킹", "PX", 2400,
                "2026-08-08T23:30:00+09:00", 100.0, "g", 1
        );
        ProductReadV1 newer = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "버거킹", "PX", 2500,
                "2026-08-08T15:00:00Z", 100.0, "g", 1
        );

        assertEquals(Integer.valueOf(2500), ProductReadV1.search(
                Arrays.asList(older, newer), "닭가슴", 50
        ).get(0).latestObservedPriceKrw);
        assertEquals(Integer.valueOf(2500), ProductReadV1.search(
                Arrays.asList(newer, older), "닭가슴", 50
        ).get(0).latestObservedPriceKrw);
    }

    @Test
    public void prefersStandardNameAndSeparatesExplicitBrandPrefix() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("catalog_product_id", CATALOG_ID);
        row.put("standard_product_id", STANDARD_ID);
        row.put("brand_name", "CJ");
        row.put("name", "CJ Hatban 210g");
        row.put("standard_name", "CJ Hatban");

        ProductReadV1 product = ProductReadV1.fromMap(row);

        assertEquals("Hatban", product.name);
        assertEquals("CJ · Hatban", product.standardProductLabel());
    }

    @Test
    public void adaptsVersionedContractWithSeparateBrandAndProductName() {
        Map<String, Object> standardProduct = new LinkedHashMap<>();
        standardProduct.put("id", STANDARD_ID);
        standardProduct.put("name", "CJ · 햇반 백미밥");
        standardProduct.put("brand", "CJ");

        Map<String, Object> catalogProduct = new LinkedHashMap<>();
        catalogProduct.put("id", CATALOG_ID);
        catalogProduct.put("contentAmount", 210.0);
        catalogProduct.put("contentUnit", "g");
        catalogProduct.put("packageCount", 1);

        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("sellerLabel", "쿠팡");
        observation.put("listedPriceKrw", 1900);
        observation.put("observedAt", "2026-08-10T00:00:00Z");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("standardProduct", standardProduct);
        row.put("catalogProduct", catalogProduct);
        row.put("sellerProducts", Collections.emptyList());
        row.put("observations", Collections.singletonList(observation));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", ProductReadV1.CONTRACT_VERSION);
        payload.put("namespace", "pricetrace");
        payload.put("products", Collections.singletonList(row));

        ProductReadV1 product = ProductReadV1.fromContractMap(payload).get(0);

        assertEquals("CJ", product.brand);
        assertEquals("햇반 백미밥", product.name);
        assertEquals("CJ · 햇반 백미밥", product.standardProductLabel());
        assertEquals("쿠팡", product.sellerName);
        assertEquals(Integer.valueOf(1900), product.latestObservedPriceKrw);
    }

    @Test
    public void infersMissingBrandOnlyWhenCatalogNameCorroboratesProductName() {
        Map<String, Object> standardProduct = new LinkedHashMap<>();
        standardProduct.put("id", STANDARD_ID);
        standardProduct.put("name", "CJ 햇반");
        standardProduct.put("brand", null);

        Map<String, Object> catalogProduct = new LinkedHashMap<>();
        catalogProduct.put("id", CATALOG_ID);
        catalogProduct.put("name", "햇반 130g");
        catalogProduct.put("contentAmount", 130.0);
        catalogProduct.put("contentUnit", "g");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("standardProduct", standardProduct);
        row.put("catalogProduct", catalogProduct);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", ProductReadV1.CONTRACT_VERSION);
        payload.put("namespace", "pricetrace");
        payload.put("products", Collections.singletonList(row));

        ProductReadV1 product = ProductReadV1.fromContractMap(payload).get(0);

        assertEquals("CJ", product.brand);
        assertEquals("햇반", product.name);
        assertEquals("CJ · 햇반", product.standardProductLabel());
    }

    @Test
    public void keepsCombinedStandardNameWhenCatalogNameDoesNotCorroborateSplit() {
        Map<String, Object> standardProduct = new LinkedHashMap<>();
        standardProduct.put("id", STANDARD_ID);
        standardProduct.put("name", "가격 미관측 상품");

        Map<String, Object> catalogProduct = new LinkedHashMap<>();
        catalogProduct.put("id", CATALOG_ID);
        catalogProduct.put("name", "다른 상품 100g");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("standardProduct", standardProduct);
        row.put("catalogProduct", catalogProduct);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", ProductReadV1.CONTRACT_VERSION);
        payload.put("namespace", "pricetrace");
        payload.put("products", Collections.singletonList(row));

        ProductReadV1 product = ProductReadV1.fromContractMap(payload).get(0);

        assertNull(product.brand);
        assertEquals("가격 미관측 상품", product.name);
    }

    @Test
    public void standardProductSearchCollapsesCatalogChildrenAndResolvesMatchingBasis() {
        ProductReadV1 firstChild = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "Hatban", "CJ", "Store A", null, null, 210.0, "g", 1
        );
        ProductReadV1 secondChild = new ProductReadV1(
                SECOND_CATALOG_ID, STANDARD_ID, "Hatban", "CJ", "Store B", null, null, 130.0, "g", 1
        );

        List<ProductReadV1> results = ProductReadV1.search(
                Arrays.asList(firstChild, secondChild), "CJ Hatban", 50
        );

        assertEquals(1, results.size());
        ProductReadV1 standardResult = results.get(0);
        assertEquals("CJ · Hatban", standardResult.standardProductLabel());
        assertEquals(2, standardResult.catalogVariantCount());
        assertFalse(standardResult.isExactCatalogProduct());
        assertEquals(CATALOG_ID, standardResult.exactVariantForBasis(210.0, "g").catalogProductId);
        assertEquals(SECOND_CATALOG_ID, standardResult.exactVariantForBasis(0.13, "kg").catalogProductId);
        assertNull(standardResult.exactVariantForBasis(100.0, "g"));
    }

    @Test
    public void standardProductSearchIgnoresRowsWithoutStandardId() {
        ProductReadV1 catalogOnly = new ProductReadV1(
                CATALOG_ID, null, "Catalog child", "Brand", "PX", null, null, 100.0, "g", 1
        );

        assertTrue(ProductReadV1.search(
                java.util.Collections.singletonList(catalogOnly), "Catalog", 50
        ).isEmpty());
    }

    @Test
    public void nameSearchAlsoMatchesBrand() {
        ProductReadV1 product = new ProductReadV1(
                CATALOG_ID, STANDARD_ID, "닭가슴살", "버거킹", "매장", null,
                null, 1.0, "serving", 1
        );

        assertEquals(1, ProductReadV1.search(
                java.util.Collections.singletonList(product), "버거킹", 50
        ).size());
    }
}
