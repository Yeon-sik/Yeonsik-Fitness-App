package com.yeonsik.fitnessapp.config;

/** Allows the one-time owner rewrite only for the same verified email account. */
public final class LegacyNutritionOwnerPolicy {
    private LegacyNutritionOwnerPolicy() {
    }

    public static boolean canMigrate(
            LegacyNutritionAccountStore.Identity legacyIdentity,
            SupabaseConfig sharedConfig
    ) {
        if (legacyIdentity == null || sharedConfig == null || !sharedConfig.isConfigured()) {
            return false;
        }
        if (legacyIdentity.userId.isEmpty()
                || SupabaseConfig.DEFAULT_USER_ID.equals(legacyIdentity.userId)) {
            return false;
        }
        return !legacyIdentity.email.isEmpty()
                && legacyIdentity.email.equalsIgnoreCase(sharedConfig.email);
    }
}
