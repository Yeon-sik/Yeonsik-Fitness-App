package com.yeonsik.fitnessapp.config;

import com.yeonsik.fitnessapp.BuildConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualDatabaseConfigurationTest {
    @Test public void allDatabaseConnectionsAreManualInEveryBuildVariant() {
        assertFalse(BuildConfig.ALLOW_MANAGED_SUPABASE_DEFAULTS);
        assertEquals("", BuildConfig.SUPABASE_URL);
        assertEquals("", BuildConfig.SUPABASE_ANON_KEY);
        assertEquals("", BuildConfig.NUTRITION_SUPABASE_URL);
        assertEquals("", BuildConfig.NUTRITION_SUPABASE_ANON_KEY);
        assertEquals("", BuildConfig.PRICETRACE_SUPABASE_URL);
        assertEquals("", BuildConfig.PRICETRACE_SUPABASE_ANON_KEY);
    }
}
