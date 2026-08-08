package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.yeonsik.fitnessapp.BuildConfig;

public class SupabaseConfigStore {
    private static final String PREFERENCES_NAME = "fitnessapp:supabase-config:v1";
    private static final String KEY_URL = "supabase_url";
    private static final String KEY_ANON = "supabase_anon_key";
    private static final String KEY_USER = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;
    private final SupabaseConnectionPolicy connectionPolicy;

    public SupabaseConfigStore(Context context) {
        preferences = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        tokenStore = new SecureTokenStore(context);
        connectionPolicy = new SupabaseConnectionPolicy(
                true,
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY
        );
    }

    public SupabaseConfig load() {
        String savedUrl = preferences.getString(KEY_URL, "");
        String savedAnonKey = preferences.getString(KEY_ANON, "");
        if (connectionPolicy.requiresManagedRebind(savedUrl, savedAnonKey)) {
            replaceConnectionAndClearSession(
                    connectionPolicy.resolveUrl(savedUrl),
                    connectionPolicy.resolveAnonKey(savedAnonKey)
            );
            savedUrl = connectionPolicy.resolveUrl(savedUrl);
            savedAnonKey = connectionPolicy.resolveAnonKey(savedAnonKey);
        }

        String url = connectionPolicy.resolveUrl(savedUrl);
        String anonKey = connectionPolicy.resolveAnonKey(savedAnonKey);
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
                connectionPolicy.sourceLabel()
        );
    }

    public boolean isConnectionManaged() {
        return connectionPolicy.isManaged();
    }

    public SupabaseConfig saveConnection(String supabaseUrl, String supabaseAnonKey) {
        if (isConnectionManaged()) {
            throw new IllegalStateException("공통 DB 연결은 앱 빌드 설정으로 관리됩니다.");
        }
        String normalizedUrl = normalize(supabaseUrl);
        String normalizedAnonKey = normalize(supabaseAnonKey);
        if (!normalizedUrl.isEmpty() && !normalizedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Supabase URL은 HTTPS여야 합니다.");
        }
        SupabaseConfig current = load();
        boolean connectionChanged = !current.supabaseUrl.equals(normalizedUrl)
                || !current.supabaseAnonKey.equals(normalizedAnonKey);
        if (connectionChanged) {
            replaceConnectionAndClearSession(normalizedUrl, normalizedAnonKey);
            return load();
        }
        return current;
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
                .putString(KEY_URL, current.supabaseUrl)
                .putString(KEY_ANON, current.supabaseAnonKey)
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
                current.sourceLabel
        );
    }

    public SupabaseConfig clearSession() {
        SupabaseConfig current = load();
        tokenStore.clear();
        preferences.edit()
                .remove(KEY_USER)
                .remove(KEY_EMAIL)
                .apply();
        return current.withoutSessionIdentity();
    }

    private void replaceConnectionAndClearSession(String url, String anonKey) {
        tokenStore.clear();
        preferences.edit()
                .putString(KEY_URL, normalize(url))
                .putString(KEY_ANON, normalize(anonKey))
                .remove(KEY_USER)
                .remove(KEY_EMAIL)
                .apply();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
