package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProductNutritionLinkContractTest {
    @Test
    public void nutritionSyncAllowsLinkMetadataButNeverMealRecords() {
        assertTrue(NutritionCatalogRepository.CATALOG_TABLES.contains("product_nutrition_links"));
        assertFalse(NutritionCatalogRepository.CATALOG_TABLES.contains("meal_records"));
        assertFalse(NutritionCatalogRepository.CATALOG_TABLES.contains("meal_record_items"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("catalog_product_id"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("standard_product_id"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("product_contract_version"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("catalog_product_revision"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("catalog_content_amount"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("catalog_content_unit"));
        assertTrue(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("catalog_package_count"));
        assertFalse(Arrays.asList(NutritionCatalogRepository.PRODUCT_LINK_SYNC_COLUMNS)
                .contains("latest_price_krw"));
    }

    @Test
    public void migrationContainsRlsApprovalAndReadContractBoundaries() throws Exception {
        String sql = new String(Files.readAllBytes(findMigration()), StandardCharsets.UTF_8);

        assertTrue(sql.contains("create table if not exists public.product_nutrition_links"));
        assertTrue(sql.contains("catalog_product_id uuid not null"));
        assertTrue(sql.contains("status in ('suggested', 'approved', 'rejected')"));
        assertTrue(sql.contains("enable row level security"));
        assertTrue(sql.contains("product_nutrition_links_select"));
        assertTrue(sql.contains("owner_id = ((select auth.uid())::text)"));
        assertTrue(sql.contains("source_type = 'manual_selection'"));
        assertTrue(sql.contains("guard_product_nutrition_link_update"));
        assertTrue(sql.contains("link identity and provenance are immutable"));
        assertTrue(sql.contains("get_nutrition_read_v1"));
        assertTrue(sql.contains("'nutrition-read.v1'::text"));
        assertTrue(sql.contains("'sodium_mg', food.sodium_mg"));
        assertTrue(sql.contains("micronutrients jsonb"));
        assertTrue(sql.contains("food.revision"));
        assertTrue(sql.contains("bump_parent_nutrition_food_revision"));
        assertTrue(sql.contains("is not distinct from"));
        assertFalse(sql.contains("alter table public.meal_record_items"));
    }

    @Test
    public void identityMigrationAddsBrandAndVersionedReadProjection() throws Exception {
        String sql = new String(Files.readAllBytes(findIdentityMigration()), StandardCharsets.UTF_8);

        assertTrue(sql.contains("add column if not exists brand text"));
        assertTrue(sql.contains("add column if not exists standard_product_id uuid"));
        assertTrue(sql.contains("new.brand"));
        assertTrue(sql.contains("get_nutrition_read_v2"));
        assertTrue(sql.contains("'nutrition-read.v2'::text"));
        assertTrue(sql.contains("food.brand"));
        assertTrue(sql.contains("approved.standard_product_id"));
    }

    @Test
    public void pricetraceMetadataMigrationKeepsExactRevisionAndSpecification() throws Exception {
        String sql = new String(Files.readAllBytes(findPriceTraceMetadataMigration()), StandardCharsets.UTF_8);

        assertTrue(sql.contains("catalog_product_revision text"));
        assertTrue(sql.contains("catalog_content_amount numeric"));
        assertTrue(sql.contains("catalog_content_unit text"));
        assertTrue(sql.contains("catalog_package_count integer"));
        assertTrue(sql.contains("^sha256:[0-9a-f]{64}$"));
        assertTrue(sql.contains("catalog_content_unit in ('g', 'ml', 'each')"));
        assertTrue(sql.contains("get_public_product_nutrition_v1"));
        assertTrue(sql.contains("link.catalog_product_revision"));
    }

    private static Path findMigration() {
        Path fromRoot = Paths.get(
                "supabase", "nutrition", "supabase", "migrations",
                "20260809093000_product_nutrition_links.sql"
        );
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = Paths.get("..", fromRoot.toString()).normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("Product nutrition migration not found from "
                + Paths.get("").toAbsolutePath());
    }

    private static Path findIdentityMigration() {
        Path fromRoot = Paths.get(
                "supabase", "nutrition", "supabase", "migrations",
                "20260809100000_nutrition_external_menu_identity.sql"
        );
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = Paths.get("..", fromRoot.toString()).normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("Nutrition identity migration not found from "
                + Paths.get("").toAbsolutePath());
    }

    private static Path findPriceTraceMetadataMigration() {
        Path fromRoot = Paths.get(
                "supabase", "nutrition", "supabase", "migrations",
                "20260814100000_product_nutrition_link_pricetrace_metadata.sql"
        );
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = Paths.get("..", fromRoot.toString()).normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("PriceTrace metadata migration not found from "
                + Paths.get("").toAbsolutePath());
    }
}
