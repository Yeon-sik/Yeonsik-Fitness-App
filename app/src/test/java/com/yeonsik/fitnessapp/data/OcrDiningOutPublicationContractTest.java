package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static regression checks for the authenticated OCR dining-out publication RPC. */
public final class OcrDiningOutPublicationContractTest {
    private static final String MIGRATION =
            "20260927120000_ocr_verified_dining_out_publication_v1.sql";

    @Test
    public void requiresOwnerVerifiedCanonicalDiningOutImportAndCompleteExactIdentity() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("v_owner_id text := (select auth.uid())::text"));
        assertTrue(sql.contains("imported.owner_id = v_owner_id"));
        assertTrue(sql.contains("imported.nutrition_food_id = p_nutrition_food_id"));
        assertTrue(sql.contains("imported.input_contract = 'food-estimate.v1'"));
        assertTrue(sql.contains("imported.projection_source_type = 'food_image_estimate'"));
        assertTrue(sql.contains("imported.user_verified is true"));
        assertTrue(sql.contains("food.kind <> 'external_menu'"));
        assertTrue(sql.contains("food.source_type <> 'food_image_estimate'"));
        assertTrue(sql.contains("food.visibility <> 'private'"));
        assertTrue(sql.contains("p_restaurant_id is null"));
        assertTrue(sql.contains("p_restaurant_location_id is null"));
        assertTrue(sql.contains("p_restaurant_menu_id is null"));
        assertTrue(sql.contains("p_catalog_product_id is null"));
        assertTrue(sql.contains("conflicts with the canonical import"));
    }

    @Test
    public void callsExistingIdentityLinkAndPublicationFunctionsInOneRpc() throws Exception {
        String sql = readMigration();
        int attachIdentity = sql.indexOf("from public.attach_dining_out_menu_identity_v1(");
        int attachNutritionLink = sql.indexOf("from public.attach_dining_out_menu_nutrition_link_v1(");
        int publish = sql.indexOf("from public.set_dining_out_menu_publication_v1(");
        int saveResult = sql.indexOf("insert into public.nutrition_ocr_dining_out_publications (");

        assertTrue(attachIdentity >= 0);
        assertTrue(attachNutritionLink > attachIdentity);
        assertTrue(publish > attachNutritionLink);
        assertTrue(saveResult > publish);
        assertTrue(sql.contains("v_link.status <> 'approved'"));
        assertTrue(sql.contains("v_publication.visibility <> 'public'"));
        assertTrue(sql.contains("aborts this outer SQL statement"));
        assertFalse(sql.contains("create or replace function public.set_dining_out_menu_publication_v1"));
        assertFalse(sql.contains("create or replace function public.attach_dining_out_menu_identity_v1"));
    }

    @Test
    public void storesStableOwnerScopedReplayResultAndRejectsPayloadCollision() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("primary key (owner_id, idempotency_key)"));
        assertTrue(sql.contains("pg_advisory_xact_lock"));
        assertTrue(sql.contains("v_existing.request_payload <> v_request_payload"));
        assertTrue(sql.contains("different request"));
        assertTrue(sql.contains("saved.published_at,"));
        assertTrue(sql.contains("            true"));
        assertTrue(sql.contains("        false;"));
        assertTrue(sql.contains("nutrition_link_revision integer"));
        assertTrue(sql.contains("food_revision integer"));
        assertTrue(sql.contains("publication_revision integer"));
        assertTrue(sql.contains("replayed boolean"));
    }

    @Test
    public void keepsCanonicalImportsPrivateAndRequiresExplicitPublicationIntent() throws Exception {
        String v2 = readPath(
                "supabase", "nutrition", "supabase", "migrations",
                "20260920091256_nutrition_canonical_provenance_v2.sql"
        );
        String v3 = readPath(
                "supabase", "nutrition", "supabase", "migrations",
                "20260920091342_nutrition_product_hierarchy_import_v3.sql"
        );
        String contract = readPath(
                "contracts", "fitness-ocr-dining-out-publication.v1.md"
        );

        assertTrue(v2.contains("visibility = 'private'"));
        assertTrue(v3.contains("visibility = 'private'"));
        assertTrue(readMigration().contains("publish_verified_ocr_dining_out_nutrition_v1"));
        assertTrue(contract.contains("generic canonical import is not publication intent"));
        assertTrue(contract.contains("p_canonical_import_id"));
        assertTrue(contract.contains("The user completes final review"));
        assertTrue(contract.toLowerCase().contains("do not ask the user to"));
    }

    @Test
    public void grantsOnlyAuthenticatedExecutionAndDoesNotChangeImportFunctions() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("revoke all on function public.publish_verified_ocr_dining_out_nutrition_v1"));
        assertTrue(sql.contains(") from public, anon;"));
        assertTrue(sql.contains(") to authenticated;"));
        assertFalse(sql.contains(") to anon;"));
        assertFalse(sql.contains("create or replace function public.import_canonical_nutrition_v2"));
        assertFalse(sql.contains("create or replace function public.import_canonical_nutrition_v3"));
    }

    private static String readMigration() throws Exception {
        return readPath("supabase", "nutrition", "supabase", "migrations", MIGRATION);
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
