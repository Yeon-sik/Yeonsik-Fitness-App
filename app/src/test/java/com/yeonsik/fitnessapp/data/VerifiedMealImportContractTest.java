package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static regression checks for the Fitness-owned OCR Meal ingest boundary. */
public final class VerifiedMealImportContractTest {
    @Test
    public void definesFitnessOwnedMealRowsAndAuthenticatedWriteBoundary() throws Exception {
        String sql = readMealMigration();

        assertTrue(sql.contains("create table if not exists public.meal_records"));
        assertTrue(sql.contains("create table if not exists public.meal_record_items"));
        assertTrue(sql.contains("create table if not exists public.meal_record_item_nutrients"));
        assertTrue(sql.contains("create table if not exists public.meal_verified_imports"));
        assertTrue(sql.contains("contract_version = 'fitness-meal-verified-import.v1'"));
        assertTrue(sql.contains("nutrition_food_id text not null references public.nutrition_foods"));
        assertTrue(sql.contains("consumed_amount numeric not null check (consumed_amount > 0)"));
        assertTrue(sql.contains("consumed_unit text not null"));
        assertTrue(sql.contains("source_provenance jsonb not null"));
        assertTrue(sql.contains("pricetrace_identity jsonb"));
        assertTrue(sql.contains("unique (owner_id, idempotency_key)"));
        assertTrue(sql.contains("revoke all on public.meal_records"));
        assertTrue(sql.contains("grant execute on function public.import_verified_meal_v1"));
        assertTrue(sql.contains("security definer"));
        assertTrue(sql.contains("set search_path = ''"));
    }

    @Test
    public void validatesOffsetDateMealPolicyAndImmutableNutritionSnapshots() throws Exception {
        String sql = readMealMigration();

        assertTrue(sql.contains("eaten_at must be an ISO timestamp with an explicit offset"));
        assertTrue(sql.contains("v_local_eaten_at::date"));
        assertTrue(sql.contains("v_today := (now() + pg_catalog.make_interval"));
        assertTrue(sql.contains("Future meal dates are not allowed"));
        assertTrue(sql.contains("nutrition_calculation_contract text not null"));
        assertTrue(sql.contains("'meal-item-snapshot.v1'"));
        assertTrue(sql.contains("food_name_snapshot"));
        assertTrue(sql.contains("food_data_version_snapshot"));
        assertTrue(sql.contains("nutrient.amount * v_scale"));
        assertTrue(sql.contains("v_item_source := v_item -> 'source_provenance'"));
        assertTrue(sql.contains("The idempotency key was already used with a different meal payload"));
        assertTrue(sql.contains("pg_advisory_xact_lock"));
    }

    @Test
    public void acceptsNullableRestaurantMenuAndMealComponentNutrition() throws Exception {
        String sql = readMealMigration();

        assertTrue(sql.contains("import_meal_component_estimate_v1"));
        assertTrue(sql.contains("fitness-meal-component-estimate.v1"));
        assertTrue(sql.contains("'meal_component_estimate'"));
        assertTrue(sql.contains("nullif(v_identity ->> 'restaurant_menu_id', '') is not null"));
        assertTrue(sql.contains("restaurant_menu_id text"));
        assertTrue(sql.contains("source_type in ("));
        assertTrue(sql.contains("'food_image_estimate', 'menu_reference', 'manual'"));
        assertTrue(sql.contains("Every meal component nutrient needs a matching value and evidence"));
        assertTrue(sql.contains("v_has_estimated"));
        assertTrue(sql.contains("must be explicitly estimated"));
        assertTrue(sql.contains("grant execute on function public.import_meal_component_estimate_v1"));
        assertTrue(NutritionFood.isDiningOutSourceType(
                NutritionFood.SOURCE_MEAL_COMPONENT_ESTIMATE));
    }

    @Test
    public void keepsExistingNutritionImportRpcContractsMealFree() throws Exception {
        String verifiedNutrition = readMigration(
                "supabase", "nutrition", "supabase", "migrations",
                "20260827090000_nutrition_verified_import.sql");
        String canonicalNutrition = readMigration(
                "supabase", "nutrition", "supabase", "migrations",
                "20260827103000_nutrition_canonical_provenance_v2.sql");

        assertFalse(verifiedNutrition.contains("insert into public.meal_records"));
        assertFalse(canonicalNutrition.contains("insert into public.meal_records"));
        assertTrue(canonicalNutrition.contains("import_canonical_nutrition_v2"));
        assertFalse(NutritionCatalogRepository.CATALOG_TABLES.contains("meal_records"));
        assertFalse(NutritionCatalogRepository.CATALOG_TABLES.contains("meal_record_items"));
        assertFalse(NutritionCatalogRepository.CATALOG_TABLES.contains("meal_record_item_nutrients"));
    }

    private static String readMealMigration() throws Exception {
        return readMigration(
                "supabase", "nutrition", "supabase", "migrations",
                "20260906090000_verified_meal_ingest_v1.sql");
    }

    private static String readMigration(String... parts) throws Exception {
        return new String(Files.readAllBytes(findPath(parts)), StandardCharsets.UTF_8);
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
