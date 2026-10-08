package com.yeonsik.fitnessapp.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static contract checks for external-reference nutrition import and migration safety. */
public final class NutritionExternalReferenceContractTest {
@Test
    public void acceptsTheSharedExternalReferenceFixtureWithPublishedNutritionProvenance() throws Exception {
        String sql = readMigration("20261002120000_external_reference_nutrition_import_v1.sql");
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
                readMigration("20261002120000_external_reference_nutrition_import_v1.sql"),
                "create or replace function public.import_external_reference_nutrition_v1(",
                "revoke all on function public.import_external_reference_nutrition_v1"
        );
        assertTrue(helper.contains("v_nutrient_source_type <> 'external_reference'"));
        assertTrue(helper.contains("(select count(*) from pg_catalog.jsonb_object_keys(v_required)) <> 7"));
        assertTrue(helper.contains("(select count(*) from pg_catalog.jsonb_object_keys(v_nutrient_provenance)) <> 7"));
        assertTrue(helper.contains("catalog_mapping.nutrition_food_id"));
        assertTrue(helper.contains("source URL in evidence_refs"));
        assertTrue(helper.contains("source_reference must be a public http/https URL"));
    }

    @Test
    public void keepsAuthenticationOwnerIsolationAndIdempotentReplayBoundaries() throws Exception {
        String migration = readMigration("20261002120000_external_reference_nutrition_import_v1.sql");
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
        assertFalse(migration.contains("rename to import_canonical_nutrition_v3_legacy"));
        assertFalse(migration.contains("alter table public.nutrition_canonical_imports"));
        assertTrue(migration.contains("revoke all on function public.import_canonical_nutrition_v3_legacy"));
    }

    @Test
    public void preservesTheExistingNutritionLabelAndRestaurantEstimateContracts() throws Exception {
        String oldV3 = readMigration("20260920091342_nutrition_product_hierarchy_import_v3.sql");
        String oldV2 = readMigration("20260920091256_nutrition_canonical_provenance_v2.sql");
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

private static final String[] REQUIRED_NUTRIENTS = {
            "calories_kcal",
            "protein_grams",
            "carbs_grams",
            "fat_grams",
            "sodium_mg",
            "saturated_fat_grams",
            "sugars_grams"
    };

    @Test
    public void addsExternalReferenceWithoutChangingExistingMigration() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("input_contract in ('nutrition-label.v1', 'food-estimate.v1', 'external-reference.v1')"));
        assertTrue(sql.contains("projection_source_type in ('product_label_ocr', 'external_reference', 'food_image_estimate')"));
        assertTrue(sql.contains("'product_label_ocr', 'external_reference', 'food_image_estimate', 'menu_reference', 'manual'"));
        assertTrue(sql.contains("external-reference.v1 requires a public http/https source_reference"));
        assertTrue(sql.contains("external-nutrition-lookup.v1"));
        assertTrue(sql.contains("source_reference = case when v_contract = 'external-reference.v1'"));
        assertTrue(sql.contains("basis_amount = case when v_contract = 'external-reference.v1'"));
        assertTrue(sql.contains("basis_unit = case when v_contract = 'external-reference.v1'"));
        assertTrue(sql.contains("v_item -> 'evidence_refs' @> jsonb_build_array(v_external_source_reference)"));
        assertTrue(sql.contains("(select count(*) from jsonb_object_keys(v_required)) <> 7"));
        assertTrue(sql.contains("(select count(*) from jsonb_object_keys(v_nutrient_provenance)) <> 7"));
        assertFalse(sql.contains("jsonb_object_length"));
        assertTrue(sql.contains("external-reference.v1 cannot promote a client-supplied PriceTrace identity"));
        assertTrue(sql.contains("Only user-verified Nutrition values may be imported"));
        assertFalse(sql.contains("insert into public.meal_records"));

        for (String nutrient : REQUIRED_NUTRIENTS) {
            assertTrue("migration must retain " + nutrient, sql.contains("'" + nutrient + "'"));
        }
    }

    @Test
    public void retainsLegacyV3ContractSemanticsInTheSourceMigration() throws Exception {
        String legacyV3 = new String(Files.readAllBytes(findPath(
                "supabase", "nutrition", "supabase", "migrations",
                "20260920091342_nutrition_product_hierarchy_import_v3.sql"
        )), StandardCharsets.UTF_8);

        assertTrue(legacyV3.contains("v_contract not in ('nutrition-label.v1', 'food-estimate.v1')"));
        assertTrue(legacyV3.contains("nutrition-label.v1 accepts only observed product_label_ocr nutrient values"));
        assertTrue(legacyV3.contains("food-estimate.v1 requires at least one estimated nutrient value"));
        assertFalse(legacyV3.contains("external-reference.v1"));
    }

    @Test
    public void usesTheUnmodifiedOcrTextLookupFixture() throws Exception {
        String fixture = new String(Files.readAllBytes(findPath(
                "supabase", "nutrition", "integration", "fixtures",
                "yeonsik-ocr.v2.packaged-product.text-lookup.example.json"
        )), StandardCharsets.UTF_8);

        assertTrue(fixture.contains("\"schema_version\": \"yeonsik-ocr.v2\""));
        assertTrue(fixture.contains("\"schema_version\": \"fitness-nutrition-draft.v1\""));
        assertTrue(fixture.contains("\"source_type\": \"external_reference\""));
        assertTrue(fixture.contains("\"source_reference\": \"https://nutrition.example.com/products/test-cereal\""));
        assertTrue(fixture.contains("\"source_version\": \"external-nutrition-lookup.v1\""));
        assertTrue(fixture.contains("\"parser_version\": \"external-nutrition-lookup.v1\""));
        assertTrue(fixture.contains("\"estimate\": null"));
        assertTrue(fixture.contains("\"basis_amount\": 100"));
        assertTrue(fixture.contains("\"basis_unit\": \"g\""));

        for (String nutrient : REQUIRED_NUTRIENTS) {
            assertTrue("fixture must retain " + nutrient, fixture.contains("\"" + nutrient + "\""));
        }
    }

    @Test
    public void documentsAllThreeSourceSemantics() throws Exception {
        String document = new String(Files.readAllBytes(findPath(
                "docs", "nutrition-canonical-provenance.v3.md"
        )), StandardCharsets.UTF_8);

        assertTrue(document.contains("nutrition-label.v1"));
        assertTrue(document.contains("food-estimate.v1"));
        assertTrue(document.contains("external-reference.v1"));
        assertTrue(document.contains("public manufacturer, brand, or official distributor reference"));
        assertTrue(document.contains("source_type=external_reference"));
        assertTrue(document.contains("external-nutrition-lookup.v1"));
        assertTrue(document.contains("does not convert the basis to 100 g or 100 ml"));
        assertTrue(document.contains("does not resolve or create Product"));
    }

    private static String readMigration() throws Exception {
        return new String(Files.readAllBytes(findPath(
                "supabase", "nutrition", "supabase", "migrations",
                "20260922152608_nutrition_external_reference_import_v3.sql"
        )), StandardCharsets.UTF_8);
    }

    private static Path findPath(String... parts) {
        Path fromRoot = Paths.get("", parts);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = Paths.get("..", fromRoot.toString()).normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("Required contract file not found from "
                + Paths.get("").toAbsolutePath());
    }
}
