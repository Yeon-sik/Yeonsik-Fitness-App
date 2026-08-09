package com.yeonsik.fitnessapp.config;

/** Supplies a build-time connection as the first-run default without locking the device to it. */
final class SupabaseConnectionPolicy {
    private final boolean hasManagedDefaults;
    private final String managedUrl;
    private final String managedAnonKey;

    SupabaseConnectionPolicy(
            boolean allowManagedConnection,
            String managedUrl,
            String managedAnonKey
    ) {
        String normalizedUrl = normalize(managedUrl);
        String normalizedAnonKey = normalize(managedAnonKey);
        hasManagedDefaults = allowManagedConnection
                && normalizedUrl.startsWith("https://")
                && !normalizedAnonKey.isEmpty();
        this.managedUrl = hasManagedDefaults ? normalizedUrl : "";
        this.managedAnonKey = hasManagedDefaults ? normalizedAnonKey : "";
    }

    boolean isManaged() {
        return hasManagedDefaults;
    }

    boolean isManagedConnection(String activeUrl, String activeAnonKey) {
        return hasManagedDefaults
                && managedUrl.equals(normalize(activeUrl))
                && managedAnonKey.equals(normalize(activeAnonKey));
    }

    String resolveUrl(String savedUrl) {
        String normalized = normalize(savedUrl);
        return normalized.isEmpty() ? managedUrl : normalized;
    }

    String resolveAnonKey(String savedAnonKey) {
        String normalized = normalize(savedAnonKey);
        return normalized.isEmpty() ? managedAnonKey : normalized;
    }

    boolean requiresManagedRebind(String savedUrl, String savedAnonKey) {
        return hasManagedDefaults
                && normalize(savedUrl).isEmpty()
                && normalize(savedAnonKey).isEmpty();
    }

    String sourceLabel(String activeUrl, String activeAnonKey) {
        if (isManagedConnection(activeUrl, activeAnonKey)) {
            return SupabaseConfig.APP_MANAGED_SOURCE;
        }
        return normalize(activeUrl).isEmpty() && normalize(activeAnonKey).isEmpty()
                ? SupabaseConfig.NOT_SET_SOURCE
                : SupabaseConfig.LOCAL_SETTINGS_SOURCE;
    }

    String managedUrl() {
        return managedUrl;
    }

    String managedAnonKey() {
        return managedAnonKey;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
