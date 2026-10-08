package com.yeonsik.fitnessapp.config;

import com.yeonsik.fitnessapp.BuildConfig;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FriendsQaConfigurationTest {
    @Test public void friendVariantHasNoManagedRemoteConnectionAndUsesIsolatedStorage() {
        Assume.assumeTrue("Runs on the QA variant", "qa".equals(BuildConfig.BUILD_TYPE));
        assertEquals("test-friends", BuildConfig.FITNESS_SURFACE);
        assertFalse(BuildConfig.ALLOW_MANAGED_SUPABASE_DEFAULTS);
        assertEquals("", BuildConfig.SUPABASE_URL);
        assertEquals("", BuildConfig.SUPABASE_ANON_KEY);
        assertEquals("", BuildConfig.NUTRITION_SUPABASE_URL);
        assertEquals("", BuildConfig.NUTRITION_SUPABASE_ANON_KEY);
        assertEquals("", BuildConfig.PRICETRACE_SUPABASE_URL);
        assertEquals("", BuildConfig.PRICETRACE_SUPABASE_ANON_KEY);
        assertFalse(AppSurfacePolicy.allowsDeveloperSurface(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertFalse(AppSurfacePolicy.allowsManagedSupabaseDefaults(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertEquals(":test-friends", AppSurfacePolicy.storageSuffix(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertFalse(SupabaseConfig.empty().isConfigured());
    }
}
