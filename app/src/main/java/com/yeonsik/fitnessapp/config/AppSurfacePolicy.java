package com.yeonsik.fitnessapp.config;

import com.yeonsik.fitnessapp.BuildConfig;

/**
 * Keeps personal, test/friends, and commercial product surfaces explicit.
 * This is a local build/UI policy; it does not change any database contract.
 */
public final class AppSurfacePolicy {
    public enum Surface {
        PERSONAL("personal"),
        TEST_FRIENDS("test-friends"),
        COMMERCIAL("commercial");

        private final String id;

        Surface(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private AppSurfacePolicy() {
    }

    public static Surface current() {
        return from(BuildConfig.FITNESS_SURFACE);
    }

    static Surface from(String value) {
        String normalized = normalize(value);
        if ("test-friends".equals(normalized) || "test_friends".equals(normalized)) {
            return Surface.TEST_FRIENDS;
        }
        if ("personal".equals(normalized)) {
            return Surface.PERSONAL;
        }
        if ("commercial".equals(normalized)) {
            return Surface.COMMERCIAL;
        }
        // Unknown configuration must never grant personal/developer privileges.
        return Surface.COMMERCIAL;
    }

    public static boolean allowsDeveloperSurface() {
        return allowsDeveloperSurface(current());
    }

    static boolean allowsDeveloperSurface(Surface surface) {
        return surface == Surface.PERSONAL;
    }

    public static boolean allowsManagedSupabaseDefaults() {
        return BuildConfig.ALLOW_MANAGED_SUPABASE_DEFAULTS
                && current() == Surface.PERSONAL;
    }

    static boolean allowsManagedSupabaseDefaults(Surface surface) {
        return surface == Surface.PERSONAL;
    }

    /** Debug provisioning is a Personal-only developer convenience. */
    public static boolean allowsDebugSessionProvisioning() {
        return allowsDebugSessionProvisioning(current());
    }

    static boolean allowsDebugSessionProvisioning(Surface surface) {
        return surface == Surface.PERSONAL;
    }

    /** Personal storage keeps its historic names; other surfaces get isolated namespaces. */
    public static String storageSuffix() {
        return storageSuffix(current());
    }

    static String storageSuffix(Surface surface) {
        return surface == Surface.PERSONAL ? "" : ":" + surface.id();
    }

    /** Android Keystore aliases use a conservative suffix compatible with existing aliases. */
    public static String keyAliasSuffix() {
        return keyAliasSuffix(current());
    }

    static String keyAliasSuffix(Surface surface) {
        return surface == Surface.PERSONAL ? "" : "_" + surface.id().replace('-', '_');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
