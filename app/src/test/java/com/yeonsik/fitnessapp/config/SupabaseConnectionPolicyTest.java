package com.yeonsik.fitnessapp.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SupabaseConnectionPolicyTest {

    @Test
    public void completeManagedConnectionOverridesSavedManualConnection() {
        SupabaseConnectionPolicy policy = new SupabaseConnectionPolicy(
                true,
                "https://shared.supabase.co",
                "shared-anon-key"
        );

        assertTrue(policy.isManaged());
        assertEquals("https://shared.supabase.co", policy.resolveUrl("https://old.supabase.co"));
        assertEquals("shared-anon-key", policy.resolveAnonKey("old-anon-key"));
        assertTrue(policy.requiresManagedRebind(
                "https://old.supabase.co",
                "old-anon-key"
        ));
        assertFalse(policy.requiresManagedRebind(
                "https://shared.supabase.co",
                "shared-anon-key"
        ));
    }

    @Test
    public void incompleteManagedConnectionFallsBackAsAWhole() {
        SupabaseConnectionPolicy policy = new SupabaseConnectionPolicy(
                true,
                "https://shared.supabase.co",
                ""
        );

        assertFalse(policy.isManaged());
        assertEquals("https://manual.supabase.co", policy.resolveUrl("https://manual.supabase.co"));
        assertEquals("manual-anon-key", policy.resolveAnonKey("manual-anon-key"));
        assertFalse(policy.requiresManagedRebind("", ""));
    }

    @Test
    public void projectReferenceMakesTheActiveSharedTenantVisible() {
        SupabaseConfig config = new SupabaseConfig(
                "https://personal-os-project.supabase.co",
                "shared-anon-key",
                "",
                "",
                "",
                "",
                SupabaseConfig.APP_MANAGED_SOURCE
        );

        assertEquals("personal-os-project", config.projectRef());
    }

    @Test
    public void logoutKeepsConnectionButClearsAllSessionIdentity() {
        SupabaseConfig authenticated = new SupabaseConfig(
                "https://shared.supabase.co",
                "shared-anon-key",
                "user-a",
                "user@example.com",
                "access-token",
                "refresh-token",
                SupabaseConfig.APP_MANAGED_SOURCE
        );

        SupabaseConfig loggedOut = authenticated.withoutSessionIdentity();

        assertTrue(loggedOut.isConnectionConfigured());
        assertFalse(loggedOut.isConfigured());
        assertEquals(SupabaseConfig.DEFAULT_USER_ID, loggedOut.effectiveUserId());
        assertEquals("", loggedOut.userId);
        assertEquals("", loggedOut.email);
        assertEquals("", loggedOut.accessToken);
        assertEquals("", loggedOut.refreshToken);
        assertEquals(authenticated.supabaseUrl, loggedOut.supabaseUrl);
        assertEquals(authenticated.supabaseAnonKey, loggedOut.supabaseAnonKey);
    }
}
