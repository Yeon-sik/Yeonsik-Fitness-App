package com.yeonsik.fitnessapp.config;

/** Selects an app-managed connection only when its URL and anon key are both complete. */
final class SupabaseConnectionPolicy {
    private final boolean managed;
    private final String managedUrl;
    private final String managedAnonKey;

    SupabaseConnectionPolicy(
            boolean allowManagedConnection,
            String managedUrl,
            String managedAnonKey
    ) {
        String normalizedUrl = normalize(managedUrl);
        String normalizedAnonKey = normalize(managedAnonKey);
        managed = allowManagedConnection
                && normalizedUrl.startsWith("https://")
                && !normalizedAnonKey.isEmpty();
        this.managedUrl = managed ? normalizedUrl : "";
        this.managedAnonKey = managed ? normalizedAnonKey : "";
    }

    boolean isManaged() {
        return managed;
    }

    String resolveUrl(String savedUrl) {
        return managed ? managedUrl : normalize(savedUrl);
    }

    String resolveAnonKey(String savedAnonKey) {
        return managed ? managedAnonKey : normalize(savedAnonKey);
    }

    boolean requiresManagedRebind(String savedUrl, String savedAnonKey) {
        return managed && (!managedUrl.equals(normalize(savedUrl))
                || !managedAnonKey.equals(normalize(savedAnonKey)));
    }

    String sourceLabel() {
        return managed
                ? SupabaseConfig.APP_MANAGED_SOURCE
                : SupabaseConfig.LOCAL_SETTINGS_SOURCE;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
