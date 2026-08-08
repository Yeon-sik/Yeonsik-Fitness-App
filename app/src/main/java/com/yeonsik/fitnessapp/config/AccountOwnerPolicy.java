package com.yeonsik.fitnessapp.config;

/** Defines safe local-owner transitions around login and logout. */
public final class AccountOwnerPolicy {
    private AccountOwnerPolicy() {
    }

    public static String loggedOutOwnerId() {
        return SupabaseConfig.DEFAULT_USER_ID;
    }

    public static boolean shouldClaimLocalRows(String previousUserId, String nextUserId) {
        String previous = normalize(previousUserId);
        String next = normalize(nextUserId);
        return SupabaseConfig.DEFAULT_USER_ID.equals(previous)
                && !SupabaseConfig.DEFAULT_USER_ID.equals(next);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : normalized;
    }
}
