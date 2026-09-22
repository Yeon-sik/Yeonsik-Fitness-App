package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static contract checks for the OCR external-reference nutrition receiver. */
public final class NutritionExternalReferenceContractTest {
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
                "20260910130534_nutrition_product_hierarchy_import_v3.sql"
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
