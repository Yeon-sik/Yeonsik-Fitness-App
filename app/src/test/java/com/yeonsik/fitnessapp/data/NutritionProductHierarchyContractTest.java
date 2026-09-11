package com.yeonsik.fitnessapp.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static contract checks for the packaged-product Nutrition v3 boundary. */
public final class NutritionProductHierarchyContractTest {
    @Test
    public void addsExplicitHierarchyAndVersionedReadWithoutChangingRestaurantRecords() throws Exception {
        String sql = readV3Migration();

        assertTrue(sql.contains("add column if not exists manufacturer_name text"));
        assertTrue(sql.contains("add column if not exists brand_name text"));
        assertTrue(sql.contains("add column if not exists sub_brand_name text"));
        assertTrue(sql.contains("add column if not exists product_name text"));
        assertTrue(sql.contains("nutrition_foods_owner_product_hierarchy_idx"));
        assertTrue(sql.contains("create function public.get_nutrition_read_v3("));
        assertTrue(sql.contains("'nutrition-read.v3'::text"));
        assertTrue(sql.contains("create or replace function public.import_canonical_nutrition_v3"));
        assertTrue(sql.contains("manufacturer_name text,"));
        assertTrue(sql.contains("brand_name text,"));
        assertTrue(sql.contains("sub_brand_name text,"));
        assertTrue(sql.contains("product_name text"));
        assertTrue(sql.contains("grant execute on function public.import_canonical_nutrition_v3"));
        assertTrue(sql.contains("to authenticated"));
        assertFalse(sql.contains("insert into public.meal_records"));
        assertFalse(sql.contains("alter table public.meal_record_items"));
    }

    @Test
    public void pinsExistingNutrientContractAndExplicitV3ParameterOrder() throws Exception {
        String sql = readV3Migration();
        assertEquals(
                "create or replace function public.import_canonical_nutrition_v3(" +
                        " p_idempotency_key text, p_input_contract text, " +
                        "p_source_document_ref text, p_food_name text, p_brand text, " +
                        "p_category text, p_basis_amount numeric, p_basis_unit text, " +
                        "p_required_nutrients jsonb, p_nutrient_provenance jsonb, " +
                        "p_optional_nutrients jsonb default '{}'::jsonb, " +
                        "p_provenance jsonb default '{}'::jsonb, " +
                        "p_user_verified boolean default false, " +
                        "p_pricetrace_identity jsonb default null, " +
                        "p_estimation_evidence jsonb default null, " +
                        "p_manufacturer_name text default null, " +
                        "p_brand_name text default null, " +
                        "p_sub_brand_name text default null, " +
                        "p_product_name text default null )",
                normalizeSql(slice(sql,
                        "create or replace function public.import_canonical_nutrition_v3(",
                        "returns table"))
        );
        assertTrue(sql.contains("v_contract not in ('nutrition-label.v1', 'food-estimate.v1')"));
        assertFalse(sql.contains("nutrition-label.v3"));
        assertFalse(sql.contains("food-estimate.v3"));
        assertFalse(sql.contains("p_category_hierarchy"));
        assertEquals(
                "returns table ( canonical_import_id uuid, idempotent_replay boolean, " +
                        "nutrition_food_id text, input_contract text, projection_source_type text, " +
                        "projection_import_id uuid, catalog_product_id uuid, estimation_evidence_id uuid, " +
                        "visibility text, manufacturer_name text, brand_name text, " +
                        "sub_brand_name text, product_name text )",
                normalizeSql(slice(sql, "returns table (", "language plpgsql",
                        sql.indexOf("create or replace function public.import_canonical_nutrition_v3(")))
        );
    }

    @Test
    public void requiresVerificationNormalizesBlanksAndRejectsRestaurantHierarchy() throws Exception {
        String sql = readV3Migration();

        assertTrue(sql.contains("if p_user_verified is not true"));
        assertTrue(sql.contains("nullif(btrim(coalesce(p_manufacturer_name, '')), '')"));
        assertTrue(sql.contains("nullif(btrim(coalesce(p_brand_name, '')), '')"));
        assertTrue(sql.contains("nullif(btrim(coalesce(p_sub_brand_name, '')), '')"));
        assertTrue(sql.contains("nullif(btrim(coalesce(p_product_name, '')), '')"));
        assertTrue(sql.contains("NULL/blank never falls back to"));
        assertTrue(sql.contains("Packaged-product hierarchy is not valid for restaurant nutrition"));
        assertTrue(sql.contains("p_brand must match p_brand_name"));
        assertTrue(sql.contains("v_legacy_brand := coalesce(v_brand_name, v_input_brand)"));
    }

    @Test
    public void fingerprintsHierarchyAndKeepsProductLinkReadPath() throws Exception {
        String sql = readV3Migration();

        assertTrue(sql.contains("hierarchy_fingerprint"));
        assertTrue(sql.contains("extensions.digest"));
        assertTrue(sql.contains("v_existing.request_payload <> v_request_payload"));
        assertTrue(sql.contains("product_nutrition_links"));
        assertTrue(sql.contains("approved.catalog_product_id"));
        assertTrue(sql.contains("approved.standard_product_id"));
        assertTrue(sql.contains("nutrition_foods"));
    }

    @Test
    public void keepsV1V2ContractsAndPreservesHierarchyThroughAppSyncMapping() throws Exception {
        String v1 = readMigration(
                "20260827090000_nutrition_verified_import.sql"
        );
        String v2 = readMigration(
                "20260827103000_nutrition_canonical_provenance_v2.sql"
        );
        String client = readPath(
                "app", "src", "main", "java", "com", "yeonsik", "fitnessapp",
                "integration", "nutrition", "NutritionCatalogSyncClient.java"
        );
        String repository = readPath(
                "app", "src", "main", "java", "com", "yeonsik", "fitnessapp",
                "feature", "nutrition", "data", "NutritionCatalogRepository.java"
        );

        assertTrue(v1.contains("import_verified_nutrition_v1"));
        assertTrue(v1.contains("fitness-nutrition-verified-import.v1"));
        assertTrue(v2.contains("import_canonical_nutrition_v2"));
        assertTrue(v2.contains("fitness-nutrition-canonical-import.v2"));
        assertTrue(v2.contains("nutrition_food_nutrient_provenance"));

        for (String column : new String[] {
                "manufacturer_name", "brand_name", "sub_brand_name", "product_name"
        }) {
            assertTrue(client.contains("nullableString(row, \"" + column + "\")"));
            assertTrue(client.contains("putNullable(value, \"" + column + "\""));
            assertTrue(repository.contains("get" + toGetterSuffix(column)));
        }
    }

    private static String toGetterSuffix(String column) {
        StringBuilder result = new StringBuilder();
        boolean uppercase = true;
        for (int index = 0; index < column.length(); index++) {
            char value = column.charAt(index);
            if (value == '_') {
                uppercase = true;
            } else if (uppercase) {
                result.append(Character.toUpperCase(value));
                uppercase = false;
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    private static String readV3Migration() throws Exception {
        return readMigration("20260910130534_nutrition_product_hierarchy_import_v3.sql");
    }

    private static String slice(String value, String start, String end) {
        return slice(value, start, end, 0);
    }

    private static String slice(String value, String start, String end, int fromIndex) {
        int startIndex = value.indexOf(start, fromIndex);
        if (startIndex < 0) {
            throw new AssertionError("Missing contract section: " + start);
        }
        int endIndex = value.indexOf(end, startIndex);
        if (endIndex < 0) {
            throw new AssertionError("Missing contract section terminator: " + end);
        }
        return value.substring(startIndex, endIndex).trim();
    }

    private static String normalizeSql(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String readMigration(String fileName) throws Exception {
        return readPath(
                "supabase", "nutrition", "supabase", "migrations", fileName
        );
    }

    private static String readPath(String... parts) throws Exception {
        Path fromRoot = Paths.get("", parts);
        if (Files.exists(fromRoot)) {
            return new String(Files.readAllBytes(fromRoot), StandardCharsets.UTF_8);
        }
        Path fromModule = Paths.get("..", fromRoot.toString()).normalize();
        if (Files.exists(fromModule)) {
            return new String(Files.readAllBytes(fromModule), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Required contract file not found from "
                + Paths.get("").toAbsolutePath());
    }
}
