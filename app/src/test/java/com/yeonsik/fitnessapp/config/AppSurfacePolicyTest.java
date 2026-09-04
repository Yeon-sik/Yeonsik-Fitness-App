package com.yeonsik.fitnessapp.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppSurfacePolicyTest {
    @Test
    public void parsesOnlyKnownProductSurfaces() {
        assertEquals(AppSurfacePolicy.Surface.PERSONAL, AppSurfacePolicy.from("personal"));
        assertEquals(AppSurfacePolicy.Surface.TEST_FRIENDS, AppSurfacePolicy.from("TEST_FRIENDS"));
        assertEquals(AppSurfacePolicy.Surface.COMMERCIAL, AppSurfacePolicy.from(" commercial "));
        assertEquals(AppSurfacePolicy.Surface.PERSONAL, AppSurfacePolicy.from("unknown"));
        assertEquals(AppSurfacePolicy.Surface.PERSONAL, AppSurfacePolicy.from(null));
    }

    @Test
    public void personalSurfaceIsTheOnlyDeveloperAndManagedDefaultSurface() {
        assertTrue(AppSurfacePolicy.allowsDeveloperSurface(AppSurfacePolicy.Surface.PERSONAL));
        assertTrue(AppSurfacePolicy.allowsManagedSupabaseDefaults(AppSurfacePolicy.Surface.PERSONAL));
        assertFalse(AppSurfacePolicy.allowsDeveloperSurface(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertFalse(AppSurfacePolicy.allowsManagedSupabaseDefaults(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertFalse(AppSurfacePolicy.allowsDeveloperSurface(AppSurfacePolicy.Surface.COMMERCIAL));
        assertFalse(AppSurfacePolicy.allowsManagedSupabaseDefaults(AppSurfacePolicy.Surface.COMMERCIAL));
    }

    @Test
    public void commercialSurfaceCannotReceiveDebugSessionProvisioning() {
        assertTrue(AppSurfacePolicy.allowsDebugSessionProvisioning(AppSurfacePolicy.Surface.PERSONAL));
        assertTrue(AppSurfacePolicy.allowsDebugSessionProvisioning(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertFalse(AppSurfacePolicy.allowsDebugSessionProvisioning(AppSurfacePolicy.Surface.COMMERCIAL));
    }

    @Test
    public void nonPersonalSurfacesUseIndependentLocalConfigurationNamespaces() {
        assertEquals("", AppSurfacePolicy.storageSuffix(AppSurfacePolicy.Surface.PERSONAL));
        assertEquals(":test-friends", AppSurfacePolicy.storageSuffix(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertEquals(":commercial", AppSurfacePolicy.storageSuffix(AppSurfacePolicy.Surface.COMMERCIAL));
        assertEquals("_test_friends", AppSurfacePolicy.keyAliasSuffix(AppSurfacePolicy.Surface.TEST_FRIENDS));
        assertEquals("_commercial", AppSurfacePolicy.keyAliasSuffix(AppSurfacePolicy.Surface.COMMERCIAL));
    }
}
