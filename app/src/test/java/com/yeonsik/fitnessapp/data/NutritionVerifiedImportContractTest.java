package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract-level regression checks for the OCR -> Fitness Nutrition import boundary. */
public final class NutritionVerifiedImportContractTest {
    @Test
    public void keepsTheExistingNutritionOwnerAndPublicationBoundaries() throws Exception {
        String sql = readMigration();
        assertTrue(sql.contains("create table if not exists public.nutrition_verified_imports"));
        assertTrue(sql.contains("create table if not exists public.nutrition_estimation_evidence"));
        assertTrue(sql.contains("contract_version = 'fitness-nutrition-verified-import.v1'"));
        assertTrue(sql.contains("evidence_type in ('product_label', 'restaurant_estimate')"));
        assertTrue(sql.contains("user_verified boolean not null check (user_verified)"));
        assertTrue(sql.contains("nutrition_food_id text not null references public.nutrition_foods"));
        assertTrue(sql.contains("visibility = 'private'"));
        assertTrue(sql.contains("grant execute on function public.import_verified_nutrition_v1"));
        assertTrue(sql.contains("revoke insert, update, delete on public.nutrition_verified_imports"));
        assertTrue(sql.contains("set search_path = ''"));
        assertFalse(sql.contains("meal_records"));
    }

    @Test
    public void requiresSevenValuesAndSeparatesLabelFromEstimateProvenance() throws Exception {
        String sql = readMigration();
        assertTrue(sql.contains("calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams'"));
        assertTrue(sql.contains("sugars_grams', 'saturated_fat_grams', 'sodium_mg"));
        assertTrue(sql.contains("v_source_type := 'product_label'"));
        assertTrue(sql.contains("v_source_type := 'manual_estimate'"));
        assertTrue(sql.contains("restaurant_estimate provenance must set estimated=true"));
        assertTrue(sql.contains("Only user-verified Nutrition values may be imported"));
        assertTrue(sql.contains("source_type, proposal_reference, product_contract_version"));
    }

    @Test
    public void preservesIdempotencyIdentityAndUncertaintyEvidence() throws Exception {
        String sql = readMigration();
        assertTrue(sql.contains("unique (owner_id, idempotency_key)"));
        assertTrue(sql.contains("v_existing.request_payload <> v_request_payload"));
        assertTrue(sql.contains("idempotent_replay"));
        assertTrue(sql.contains("confidence numeric not null check (confidence >= 0 and confidence <= 1)"));
        assertTrue(sql.contains("uncertainty_range jsonb not null"));
        assertTrue(sql.contains("Each estimation range must contain min, point, max in order"));
        assertTrue(sql.contains("The Nutrition food already has a different approved PriceTrace link"));
        assertTrue(sql.contains("pg_advisory_xact_lock"));
    }

    @Test
    public void documentsBasisPackageWeightAndExactDiningOutIdentity() throws Exception {
        String document = new String(
                Files.readAllBytes(findPath("docs", "nutrition-verified-import.v1.md")),
                StandardCharsets.UTF_8
        );
        assertTrue(document.contains("\"p_basis_amount\": 100"));
        assertTrue(document.contains("package_total_amount=300"));
        assertTrue(document.contains("p_evidence_type=restaurant_estimate"));
        assertTrue(document.contains("confidence"));
        assertTrue(document.contains("dining-out-identity.v1"));
        assertTrue(document.contains("does not create meal records"));
    }

    private static String readMigration() throws Exception {
        return new String(
                Files.readAllBytes(findPath(
                        "supabase", "nutrition", "supabase", "migrations",
                        "20260827090000_nutrition_verified_import.sql"
                )),
                StandardCharsets.UTF_8
        );
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
