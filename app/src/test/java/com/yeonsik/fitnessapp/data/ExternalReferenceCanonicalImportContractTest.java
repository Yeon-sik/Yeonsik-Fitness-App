package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Static checks for the additive external-reference.v1 Supabase boundary. */
public final class ExternalReferenceCanonicalImportContractTest {
    @Test
    public void addsExternalReferenceToTheLatestCanonicalRpcAndAllowLists() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("'external-reference.v1'"));
        assertTrue(sql.contains("'external_reference'"));
        assertTrue(sql.contains("create or replace function public.import_canonical_nutrition_v3"));
        assertTrue(sql.contains("external-reference.v1 requires observed external_reference values"));
        assertTrue(sql.contains("external-reference.v1 cannot contain estimation evidence"));
        assertTrue(sql.contains("^https?://[^[:space:]]+$"));
        assertTrue(sql.contains("Only user-verified Nutrition values may be imported"));
        assertTrue(sql.contains("pg_advisory_xact_lock"));
        assertTrue(sql.contains("nutrition_verified_imports_evidence_type_check"));
        assertTrue(sql.contains("import_canonical_nutrition_v3_legacy"));
    }

    @Test
    public void preservesExistingContractsByDelegatingToTheAppliedImplementations() throws Exception {
        String sql = readMigration();

        assertTrue(sql.contains("if v_contract <> 'external-reference.v1' then"));
        assertTrue(sql.contains("import_canonical_nutrition_v3_legacy("));
        assertTrue(sql.contains("import_verified_nutrition_v1_legacy("));
        assertTrue(sql.contains("nutrition-label.v1"));
        assertTrue(sql.contains("food-estimate.v1"));
    }

    private static String readMigration() throws Exception {
        Path path = Paths.get(
                "supabase", "nutrition", "supabase", "migrations",
                "20260919120000_external_reference_canonical_import_v1.sql"
        );
        if (!Files.exists(path)) {
            path = Paths.get("..", path.toString()).normalize();
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
