package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

public final class SupabaseConfigStore {
    private static final String PREFS_NAME = "fitnessapp:supabase-config:v1";
    private static final String KEY_URL = "supabase_url";
    private static final String KEY_ANON = "supabase_anon_key";
    private static final String KEY_USER = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;

    public SupabaseConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tokenStore = new SecureTokenStore(context);
    }

    public SupabaseConfig load() {
        String url = preferences.getString(KEY_URL, "");
        String anonKey = preferences.getString(KEY_ANON, "");
        String userId = preferences.getString(KEY_USER, "");
        String email = preferences.getString(KEY_EMAIL, "");

        if ((url == null || url.trim().isEmpty())
                && (anonKey == null || anonKey.trim().isEmpty())
                && (userId == null || userId.trim().isEmpty())) {
            return SupabaseConfig.empty();
        }

        return new SupabaseConfig(
                url,
                anonKey,
                userId,
                email,
                tokenStore.accessToken(),
                tokenStore.refreshToken(),
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );
    }

    public SupabaseConfig saveConnection(String supabaseUrl, String supabaseAnonKey) {
        String normalizedUrl = normalize(supabaseUrl);
        if (!normalizedUrl.isEmpty() && !normalizedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Supabase URL은 HTTPS여야 합니다.");
        }
        SupabaseConfig current = load();
        boolean connectionChanged = !current.supabaseUrl.equals(normalizedUrl)
                || !current.supabaseAnonKey.equals(normalize(supabaseAnonKey));
        if (connectionChanged) {
            clearSession();
        }
        SupabaseConfig config = new SupabaseConfig(
                normalizedUrl,
                supabaseAnonKey,
                current.userId,
                current.email,
                connectionChanged ? "" : current.accessToken,
                connectionChanged ? "" : current.refreshToken,
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );

        preferences.edit()
                .putString(KEY_URL, config.supabaseUrl)
                .putString(KEY_ANON, config.supabaseAnonKey)
                .apply();

        return config;
    }

    public SupabaseConfig saveSession(
            String userId,
            String email,
            String accessToken,
            String refreshToken
    ) {
        SupabaseConfig current = load();
        tokenStore.save(accessToken, refreshToken);
        preferences.edit()
                .putString(KEY_USER, normalize(userId))
                .putString(KEY_EMAIL, normalize(email))
                .apply();
        return new SupabaseConfig(
                current.supabaseUrl,
                current.supabaseAnonKey,
                userId,
                email,
                accessToken,
                refreshToken,
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );
    }

    public SupabaseConfig clearSession() {
        tokenStore.clear();
        SupabaseConfig current = load();
        return new SupabaseConfig(
                current.supabaseUrl,
                current.supabaseAnonKey,
                current.userId,
                current.email,
                "",
                "",
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
