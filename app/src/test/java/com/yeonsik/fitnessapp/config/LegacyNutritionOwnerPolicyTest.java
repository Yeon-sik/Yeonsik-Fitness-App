package com.yeonsik.fitnessapp.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LegacyNutritionOwnerPolicyTest {
    @Test
    public void sameEmailCanMigrateFromFormerProjectIdentity() {
        LegacyNutritionAccountStore.Identity legacy =
                new LegacyNutritionAccountStore.Identity("legacy-user", "User@example.com");

        assertTrue(LegacyNutritionOwnerPolicy.canMigrate(
                legacy,
                authenticatedSharedConfig("user@example.com")
        ));
    }

    @Test
    public void differentEmailCannotReassignPrivateNutritionRows() {
        LegacyNutritionAccountStore.Identity legacy =
                new LegacyNutritionAccountStore.Identity("legacy-user", "user-a@example.com");

        assertFalse(LegacyNutritionOwnerPolicy.canMigrate(
                legacy,
                authenticatedSharedConfig("user-b@example.com")
        ));
    }

    @Test
    public void localOrUnauthenticatedIdentityDoesNotNeedLegacyMigration() {
        assertFalse(LegacyNutritionOwnerPolicy.canMigrate(
                new LegacyNutritionAccountStore.Identity(
                        SupabaseConfig.DEFAULT_USER_ID,
                        "user@example.com"
                ),
                authenticatedSharedConfig("user@example.com")
        ));
        assertFalse(LegacyNutritionOwnerPolicy.canMigrate(
                new LegacyNutritionAccountStore.Identity("legacy-user", "user@example.com"),
                SupabaseConfig.empty()
        ));
    }

    private static SupabaseConfig authenticatedSharedConfig(String email) {
        return new SupabaseConfig(
                "https://shared.supabase.co",
                "anon-key",
                "shared-user",
                email,
                "access-token",
                "refresh-token",
                SupabaseConfig.APP_MANAGED_SOURCE
        );
    }
}
