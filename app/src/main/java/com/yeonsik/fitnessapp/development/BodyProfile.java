package com.yeonsik.fitnessapp.development;

public final class BodyProfile {
    public final Integer heightCm;
    public final String createdAt;
    public final String updatedAt;

    public BodyProfile(Integer heightCm, String createdAt, String updatedAt) {
        this.heightCm = requireHeight(heightCm);
        this.createdAt = normalizeTimestamp(createdAt);
        this.updatedAt = normalizeTimestamp(updatedAt);
    }

    public static BodyProfile empty() {
        return new BodyProfile(null, "", "");
    }

    public boolean isConfigured() {
        return heightCm != null;
    }

    public String heightLabelKo() {
        return isConfigured() ? heightCm + "cm" : "미설정";
    }

    private static Integer requireHeight(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 50 || value > 300) {
            throw new IllegalArgumentException("키는 50~300cm 범위여야 합니다.");
        }
        return value;
    }

    private static String normalizeTimestamp(String value) {
        return value == null ? "" : value.trim();
    }
}
