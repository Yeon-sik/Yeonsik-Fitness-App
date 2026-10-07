package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.yeonsik.fitnessapp.BuildConfig;
import java.net.URI;

public class SupabaseConfigStore {
    private static final String KEY_URL = "supabase_url";
    private static final String KEY_ANON = "supabase_anon_key";
    private static final String KEY_USER = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;
    private final SupabaseConnectionPolicy connectionPolicy;

    public SupabaseConfigStore(Context context) {
        this(
                context,
                SupabaseStoreScope.SHARED,
                AppSurfacePolicy.allowsManagedSupabaseDefaults(),
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY
        );
    }

    protected SupabaseConfigStore(
            Context context,
            SupabaseStoreScope scope,
            boolean allowManagedConnection,
            String managedUrl,
            String managedAnonKey
    ) {
        String storageSuffix = AppSurfacePolicy.storageSuffix();
        preferences = context.getSharedPreferences(
                scope.configPreferencesName + storageSuffix,
                Context.MODE_PRIVATE
        );
        tokenStore = new SecureTokenStore(
                context,
                scope.tokenKeyAlias + AppSurfacePolicy.keyAliasSuffix(),
                scope.tokenPreferencesName + storageSuffix
        );
        connectionPolicy = new SupabaseConnectionPolicy(
                allowManagedConnection,
                managedUrl,
                managedAnonKey
        );
    }

    public SupabaseConfig load() {
        String savedUrl = preferences.getString(KEY_URL, "");
        String savedAnonKey = preferences.getString(KEY_ANON, "");
        if (connectionPolicy.requiresManagedRebind(savedUrl, savedAnonKey)) {
            replaceConnectionAndClearSession(
                    connectionPolicy.managedUrl(),
                    connectionPolicy.managedAnonKey()
            );
            savedUrl = connectionPolicy.managedUrl();
            savedAnonKey = connectionPolicy.managedAnonKey();
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
                connectionPolicy.sourceLabel(url, anonKey)
        );
    }

    public boolean isConnectionManaged() {
        return SupabaseConfig.APP_MANAGED_SOURCE.equals(load().sourceLabel);
    }

    public synchronized SupabaseConfig saveConnection(String supabaseUrl, String supabaseAnonKey) {
        String normalizedUrl = normalize(supabaseUrl);
        String normalizedAnonKey = normalize(supabaseAnonKey);
        if (normalizedUrl.isEmpty() != normalizedAnonKey.isEmpty()) {
            throw new IllegalArgumentException("Supabase URL과 anon key를 함께 입력하거나 모두 비워 주세요.");
        }
        if (!normalizedUrl.isEmpty()) {
            URI address;
            try { address = URI.create(normalizedUrl); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("올바른 DB URL을 입력하세요."); }
            if (!"https".equalsIgnoreCase(address.getScheme()) || address.getHost() == null
                    || address.getRawUserInfo() != null || address.getRawQuery() != null || address.getRawFragment() != null) {
                throw new IllegalArgumentException("DB URL은 사용자 정보나 쿼리가 없는 HTTPS 주소여야 합니다.");
            }
            if (address.getPath().startsWith("/rest/v1") || address.getPath().startsWith("/auth/v1")) {
                throw new IllegalArgumentException("API 경로 대신 프로젝트의 기본 DB URL을 입력하세요.");
            }
            if (normalizedAnonKey.startsWith("sb_secret_")) {
                throw new IllegalArgumentException("공개 anon 또는 publishable 키를 입력하세요.");
            }
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
        boolean saved = preferences.edit()
                .putString(KEY_URL, current.supabaseUrl)
                .putString(KEY_ANON, current.supabaseAnonKey)
                .putString(KEY_USER, normalize(userId))
                .putString(KEY_EMAIL, normalize(email))
                .commit();
        if (!saved) {
            throw new IllegalStateException("Supabase 로그인 정보를 저장하지 못했습니다.");
        }
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

    /** An old request must not attach its session to a newly selected remote project. */
    public synchronized SupabaseConfig saveSessionForConnection(
            SupabaseConfig expectedConnection, String userId, String email,
            String accessToken, String refreshToken
    ) {
        SupabaseConfig current = load();
        if (!current.supabaseUrl.equals(expectedConnection.supabaseUrl)
                || !current.supabaseAnonKey.equals(expectedConnection.supabaseAnonKey)) {
            throw new IllegalStateException("DB 연결이 변경되었습니다. 새 연결에서 다시 로그인하세요.");
        }
        if (!current.userId.isEmpty() && !current.userId.equals(userId)) {
            throw new IllegalStateException("로컬 기록이 다른 계정에 연결되어 있습니다.");
        }
        return saveSession(userId, email, accessToken, refreshToken);
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
        boolean saved = preferences.edit()
                .putString(KEY_URL, normalize(url))
                .putString(KEY_ANON, normalize(anonKey))
                .remove(KEY_USER)
                .remove(KEY_EMAIL)
                .commit();
        if (!saved) throw new IllegalStateException("DB 연결 설정을 저장하지 못했습니다.");
        tokenStore.clear();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
