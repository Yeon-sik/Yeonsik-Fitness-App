package com.yeonsik.fitnessapp.config;

public final class SupabaseConfig {
    public static final String LOCAL_SETTINGS_SOURCE = "local settings";
    public static final String NOT_SET_SOURCE = "not set";
    public static final String DEFAULT_USER_ID = "local-user";

    public final String supabaseUrl;
    public final String supabaseAnonKey;
    public final String userId;
    public final String email;
    public final String accessToken;
    public final String refreshToken;
    public final String sourceLabel;

    public SupabaseConfig(
            String supabaseUrl,
            String supabaseAnonKey,
            String userId,
            String email,
            String accessToken,
            String refreshToken,
            String sourceLabel
    ) {
        this.supabaseUrl = normalize(supabaseUrl);
        this.supabaseAnonKey = normalize(supabaseAnonKey);
        this.userId = normalize(userId);
        this.email = normalize(email);
        this.accessToken = normalize(accessToken);
        this.refreshToken = normalize(refreshToken);
        this.sourceLabel = sourceLabel == null || sourceLabel.trim().isEmpty() ? NOT_SET_SOURCE : sourceLabel.trim();
    }

    public static SupabaseConfig empty() {
        return new SupabaseConfig("", "", "", "", "", "", NOT_SET_SOURCE);
    }

    public boolean isConnectionConfigured() {
        return supabaseUrl.startsWith("https://") && !supabaseAnonKey.isEmpty();
    }

    public boolean isConfigured() {
        return isConnectionConfigured() && !userId.isEmpty() && !accessToken.isEmpty();
    }

    public String effectiveUserId() {
        return userId.isEmpty() ? DEFAULT_USER_ID : userId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
