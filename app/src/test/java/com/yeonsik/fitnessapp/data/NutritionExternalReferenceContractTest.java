package com.yeonsik.fitnessapp.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static contract checks for OCR-App V2 external-reference.v1 and additive migration safety. */
public final class NutritionExternalReferenceContractTest {
    @Test
    public void acceptsTheSharedExternalReferenceFixtureWithPublishedNutritionProvenance() throws Exception {
        String sql = readMigration("20260922100000_external_reference_nutrition_import_v1.sql");
        String fixture = readFixture();
        String helper = slice(
                sql,
                "create or replace function public.import_external_reference_nutrition_v1(",
                "revoke all on function public.import_external_reference_nutrition_v1"
        );

        assertTrue(fixture.contains("\"p_input_contract\": \"external-reference.v1\""));
        assertTrue(fixture.contains("\"p_source_document_ref\": \"https://nutrition.example.com/products/test-cereal\""));
        assertTrue(fixture.contains("\"source_type\": \"external_reference\""));
        assertTrue(fixture.contains("\"source_version\": \"external-nutrition-lookup.v1\""));
        for (String nutrient : new String[] {
                "calories_kcal", "protein_grams", "carbs_grams", "fat_grams",
                "sodium_mg", "saturated_fat_grams", "sugars_grams"
        }) {
            assertTrue("fixture nutrient: " + nutrient, fixture.contains("\"" + nutrient + "\""));
            assertTrue("migration nutrient: " + nutrient, helper.contains("'" + nutrient + "'"));
        }
        assertTrue(helper.contains("v_contract <> 'external-reference.v1'"));
        assertTrue(helper.contains("v_source_reference !~* '^https?://"));
        assertTrue(helper.contains("v_source_host = 'localhost'"));
        assertTrue(helper.contains("p_source_document_ref is null"));
        assertTrue(helper.contains("p_source_document_ref <> v_source_reference"));
        assertTrue(helper.contains("source_type', 'external_reference'"));
        assertTrue(helper.contains("source_version', 'external-nutrition-lookup.v1'"));
        assertTrue(helper.contains("canonical_input_contract', '') <> 'external-reference.v1'"));
        assertTrue(helper.contains("coalesce(v_provenance ->> 'estimated', '') <> 'false'"));
        assertTrue(helper.contains("external-reference.v1 cannot be estimated"));
        assertTrue(helper.contains("p_estimation_evidence is not null"));
        assertTrue(helper.contains("p_pricetrace_identity is not null"));
    }

    @Test
    public void rejectsSourceMismatchBlankReferenceAndMissingRequiredNutrients() throws Exception {
        String helper = slice(
                readMigration("20260922100000_external_reference_nutrition_import_v1.sql"),
                "create or replace function public.import_external_reference_nutrition_v1(",
                "revoke all on function public.import_external_reference_nutrition_v1"
        );
        assertTrue(helper.contains("v_nutrient_source_type <> 'external_reference'"));
        assertTrue(helper.contains("jsonb_object_length(v_required) <> 7"));
        assertTrue(helper.contains("jsonb_object_length(v_nutrient_provenance) <> 7"));
        assertTrue(helper.contains("source URL in evidence_refs"));
        assertTrue(helper.contains("source_reference must be a public http/https URL"));
    }

    @Test
    public void keepsAuthenticationOwnerIsolationAndIdempotentReplayBoundaries() throws Exception {
        String migration = readMigration("20260922100000_external_reference_nutrition_import_v1.sql");
        String helper = slice(
                migration,
                "create or replace function public.import_external_reference_nutrition_v1(",
                "revoke all on function public.import_external_reference_nutrition_v1"
        );
        assertTrue(helper.contains("v_user_id text := (select auth.uid())::text"));
        assertTrue(helper.contains("if v_user_id is null then"));
        assertTrue(helper.contains("where owner_id = v_user_id"));
        assertTrue(helper.contains("v_existing.request_payload <> v_request_payload"));
        assertTrue(helper.contains("pg_advisory_xact_lock"));
        assertTrue(helper.contains("source_type = 'external_reference'"));
        assertTrue(helper.contains("source_reference = v_source_reference"));
        assertTrue(migration.contains("grant execute on function public.import_canonical_nutrition_v3"));
        assertTrue(migration.contains("revoke all on function public.import_canonical_nutrition_v3_legacy"));
    }

    @Test
    public void preservesTheExistingNutritionLabelAndRestaurantEstimateContracts() throws Exception {
        String oldV3 = readMigration("20260910130534_nutrition_product_hierarchy_import_v3.sql");
        String oldV2 = readMigration("20260827103000_nutrition_canonical_provenance_v2.sql");
        assertTrue(oldV3.contains("v_contract not in ('nutrition-label.v1', 'food-estimate.v1')"));
        assertFalse(oldV3.contains("external-reference.v1"));
        assertTrue(oldV2.contains("input_contract in ('nutrition-label.v1', 'food-estimate.v1')"));
        assertTrue(oldV2.contains("'product_label_ocr', 'food_image_estimate', 'menu_reference', 'manual'"));
        assertFalse(oldV2.contains("external_reference"));
    }

    private static String readFixture() throws Exception {
        return readPath("contracts", "fitness-external-reference.v1.json");
    }

    private static String readMigration(String fileName) throws Exception {
        return readPath("supabase", "nutrition", "supabase", "migrations", fileName);
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

    private static String slice(String value, String start, String end) {
        int startIndex = value.indexOf(start);
        if (startIndex < 0) throw new AssertionError("Missing contract section: " + start);
        int endIndex = value.indexOf(end, startIndex);
        if (endIndex < 0) throw new AssertionError("Missing contract section terminator: " + end);
        return value.substring(startIndex, endIndex);
    }
}
