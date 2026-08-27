package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static regression checks for the canonical OCR Nutrition provenance boundary. */
public final class NutritionCanonicalProvenanceContractTest {
    @Test
    public void separatesCanonicalInputsFromTheNutritionFoodsProjection() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("create table if not exists public.nutrition_canonical_imports"));
        assertTrue(sql.contains("create table if not exists public.nutrition_food_nutrient_provenance"));
        assertTrue(sql.contains("input_contract in ('nutrition-label.v1', 'food-estimate.v1')"));
        assertTrue(sql.contains("value_status in ('observed', 'estimated')"));
        assertTrue(sql.contains("'product_label_ocr', 'food_image_estimate', 'menu_reference', 'manual'"));
        assertTrue(sql.contains("nutrition_foods is deliberately only the final point-value projection"));
        assertTrue(sql.contains("grant execute on function public.import_canonical_nutrition_v2"));
        assertTrue(sql.contains("revoke insert, update, delete on public.nutrition_canonical_imports"));
        assertTrue(sql.contains("Only user-verified Nutrition values may be imported"));
        assertFalse(sql.contains("insert into public.meal_records"));
    }

    @Test
    public void validatesTheTwoEvidenceContractsAndIdempotency() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("nutrition-label.v1 accepts only observed product_label_ocr nutrient values"));
        assertTrue(sql.contains("food-estimate.v1 requires at least one estimated nutrient value"));
        assertTrue(sql.contains("food-estimate.v1 cannot claim product-label nutrient evidence"));
        assertTrue(sql.contains("food-estimate.v1 requires numeric confidence evidence"));
        assertTrue(sql.contains("Every required nutrient needs a matching value and at least one evidence reference"));
        assertTrue(sql.contains("The idempotency key was already used with a different canonical payload"));
        assertTrue(sql.contains("pg_advisory_xact_lock"));
    }

    @Test
    public void keepsExactDiningOutPublicationAndAndroidCompatibility() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("food.source_type in ('manual_estimate', 'food_image_estimate')"));
        assertTrue(sql.contains("The exact PriceTrace dining-out identity is required"));
        assertTrue(sql.contains("link.catalog_product_id <> p_catalog_product_id"));

        NutritionFood canonicalEstimate = NutritionFood.builder()
                .id("menu")
                .ownerId("owner")
                .name("Menu")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .basis(1, "serving")
                .source("food_image_estimate", "test")
                .build();
        assertTrue(canonicalEstimate.isDiningOutMenu());
        assertTrue(NutritionFood.isDiningOutSourceType("manual_estimate"));
        assertFalse(NutritionFood.isDiningOutSourceType("product_label_ocr"));
    }

    @Test
    public void documentsCanonicalPayloadsAndNoForcedOcrLineEvidence() throws Exception {
        String document = new String(Files.readAllBytes(findPath(
                "docs", "nutrition-canonical-provenance.v2.md"
        )), StandardCharsets.UTF_8);

        assertTrue(document.contains("import_canonical_nutrition_v2"));
        assertTrue(document.contains("\"p_input_contract\": \"nutrition-label.v1\""));
        assertTrue(document.contains("\"p_input_contract\": \"food-estimate.v1\""));
        assertTrue(document.contains("p_basis_amount=100"));
        assertTrue(document.contains("package_total_amount=300"));
        assertTrue(document.contains("evidence_refs"));
        assertTrue(document.contains("not limited to OCR line IDs"));
        assertTrue(document.contains("dining-out-identity.v1"));
    }

    private static String readMigration() throws Exception {
        return new String(Files.readAllBytes(findPath(
                "supabase", "nutrition", "supabase", "migrations",
                "20260827103000_nutrition_canonical_provenance_v2.sql"
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
