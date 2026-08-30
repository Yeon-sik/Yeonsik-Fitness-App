package com.yeonsik.fitnessapp.exercise;

import java.util.Objects;

/**
 * Comparable performance bucket. PR/e1RM/weight statistics must stay inside this exact key.
 */
public final class ExercisePerformanceKey {
    public final String familyId;
    public final String canonicalVariantKey;
    public final LoadState loadState;

    private ExercisePerformanceKey(String familyId, String canonicalVariantKey, LoadState loadState) {
        this.familyId = familyId;
        this.canonicalVariantKey = canonicalVariantKey;
        this.loadState = loadState;
    }

    public static ExercisePerformanceKey of(ExerciseFamilyIdentity identity, LoadState loadState) {
        if (identity == null || !identity.hasVariantIdentity() || loadState == null) {
            return null;
        }
        return new ExercisePerformanceKey(identity.familyId, identity.canonicalVariantKey, loadState);
    }

    public boolean isComparableTo(ExercisePerformanceKey other) {
        return other != null
                && Objects.equals(familyId, other.familyId)
                && Objects.equals(canonicalVariantKey, other.canonicalVariantKey)
                && loadState == other.loadState;
    }

    public String stableValue() {
        return familyId + "|" + canonicalVariantKey + "|" + loadState.id();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExercisePerformanceKey)) return false;
        ExercisePerformanceKey that = (ExercisePerformanceKey) other;
        return Objects.equals(familyId, that.familyId)
                && Objects.equals(canonicalVariantKey, that.canonicalVariantKey)
                && loadState == that.loadState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(familyId, canonicalVariantKey, loadState);
    }

    @Override
    public String toString() {
        return stableValue();
    }
}
