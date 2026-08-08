package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

/** Reads and retires the former separate Nutrition-project identity after a safe migration. */
public final class LegacyNutritionAccountStore {
    private static final String CONFIG_PREFERENCES =
            "fitnessapp:nutrition-supabase-config:v1";
    private static final String TOKEN_KEY_ALIAS =
            "fitnessapp_supabase_nutrition_session_v1";
    private static final String TOKEN_PREFERENCES =
            "fitnessapp:secure-nutrition-session:v1";
    private static final String KEY_USER = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;

    public LegacyNutritionAccountStore(Context context) {
        preferences = context.getSharedPreferences(CONFIG_PREFERENCES, Context.MODE_PRIVATE);
        tokenStore = new SecureTokenStore(context, TOKEN_KEY_ALIAS, TOKEN_PREFERENCES);
    }

    public Identity loadIdentity() {
        return new Identity(
                preferences.getString(KEY_USER, ""),
                preferences.getString(KEY_EMAIL, "")
        );
    }

    public void clear() {
        preferences.edit().clear().apply();
        tokenStore.clear();
    }

    public static final class Identity {
        public final String userId;
        public final String email;

        public Identity(String userId, String email) {
            this.userId = normalize(userId);
            this.email = normalize(email);
        }

        public boolean isPresent() {
            return !userId.isEmpty() || !email.isEmpty();
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
