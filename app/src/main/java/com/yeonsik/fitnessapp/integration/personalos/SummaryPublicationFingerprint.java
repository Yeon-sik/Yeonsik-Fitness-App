package com.yeonsik.fitnessapp.integration.personalos;

import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Includes child-derived counts as well as the source timestamp and deletion state. */
final class SummaryPublicationFingerprint {
    private SummaryPublicationFingerprint() {}

    static String of(FitnessSummaryProjectionV2 row) {
        StringBuilder value = new StringBuilder();
        for (Object field : new Object[]{
                FitnessSummaryProjectionV2.CONTRACT_VERSION, row.id, row.sourceFitnessSessionId,
                row.date, row.completionStatus, row.chestSets, row.backSets, row.legsSets,
                row.shouldersSets, row.absSets, row.tricepsSets, row.bicepsSets,
                row.totalDurationSeconds, row.cardioDurationSeconds, row.createdAt,
                row.isBackfilled, row.backfilledAt, row.backfillReason,
                row.updatedAt, row.deletedAt, row.deviceId
        }) {
            append(value, field);
        }
        return hash(value.toString());
    }

    static String scope(String projectUrl, String ownerId) {
        StringBuilder value = new StringBuilder();
        append(value, projectUrl.replaceAll("/+$", ""));
        append(value, ownerId);
        return hash(value.toString());
    }

    private static void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("-1:");
        } else {
            String text = value.toString();
            target.append(text.length()).append(':').append(text);
        }
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(Character.forDigit((item >>> 4) & 15, 16));
                hex.append(Character.forDigit(item & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
