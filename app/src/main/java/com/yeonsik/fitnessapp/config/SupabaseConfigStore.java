package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

public final class SupabaseConfigStore {
    private static final String PREFS_NAME = "fitnessapp:supabase-config:v1";
    private static final String KEY_URL = "supabase_url";
    private static final String KEY_ANON = "supabase_anon_key";
    private static final String KEY_USER = "user_id";

    private final SharedPreferences preferences;

    public SupabaseConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SupabaseConfig load() {
        String url = preferences.getString(KEY_URL, "");
        String anonKey = preferences.getString(KEY_ANON, "");
        String userId = preferences.getString(KEY_USER, "");

        if ((url == null || url.trim().isEmpty())
                && (anonKey == null || anonKey.trim().isEmpty())
                && (userId == null || userId.trim().isEmpty())) {
            return SupabaseConfig.empty();
        }

        return new SupabaseConfig(url, anonKey, userId, SupabaseConfig.LOCAL_SETTINGS_SOURCE);
    }

    public SupabaseConfig save(String supabaseUrl, String supabaseAnonKey, String userId) {
        SupabaseConfig config = new SupabaseConfig(
                supabaseUrl,
                supabaseAnonKey,
                userId,
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );

        preferences.edit()
                .putString(KEY_URL, config.supabaseUrl)
                .putString(KEY_ANON, config.supabaseAnonKey)
                .putString(KEY_USER, config.userId)
                .apply();

        return config;
    }
}
