package com.yeonsik.fitnessapp.exercise;

/**
 * Stable family/preset/variant identity resolved from the normative catalog.
 *
 * <p>The legacy exercise ID remains part of this object so old routines and records can be
 * addressed without rewriting historical rows. Load state is intentionally exposed separately
 * and is persisted on each set.</p>
 */
public final class ExerciseFamilyIdentity {
    public final String legacyExerciseId;
    public final String familyId;
    public final String presetId;
    public final String canonicalPresetId;
    public final String presetNameKo;
    public final String presetNameEn;
    public final String legacyNameKo;
    public final String legacyNameEn;
    public final String defaultUiPart;
    public final String canonicalVariantKey;
    public final String visualVariantKey;
    public final String illustrationKey;
    public final String defaultLoadState;
    public final String legacyRecordType;
    public final String variantJson;

    public ExerciseFamilyIdentity(
            String legacyExerciseId,
            String familyId,
            String presetId,
            String canonicalPresetId,
            String presetNameKo,
            String presetNameEn,
            String legacyNameKo,
            String legacyNameEn,
            String defaultUiPart,
            String canonicalVariantKey,
            String visualVariantKey,
            String illustrationKey,
            String defaultLoadState,
            String legacyRecordType,
            String variantJson
    ) {
        this.legacyExerciseId = legacyExerciseId;
        this.familyId = familyId;
        this.presetId = presetId;
        this.canonicalPresetId = canonicalPresetId;
        this.presetNameKo = presetNameKo;
        this.presetNameEn = presetNameEn;
        this.legacyNameKo = legacyNameKo;
        this.legacyNameEn = legacyNameEn;
        this.defaultUiPart = defaultUiPart;
        this.canonicalVariantKey = canonicalVariantKey;
        this.visualVariantKey = visualVariantKey;
        this.illustrationKey = illustrationKey;
        this.defaultLoadState = defaultLoadState;
        this.legacyRecordType = legacyRecordType;
        this.variantJson = variantJson;
    }

    /** Detailed preset/legacy name for routine, workout, and history rows. */
    public String displayName() {
        if (presetNameKo != null && !presetNameKo.trim().isEmpty()) {
            return presetNameKo;
        }
        if (legacyNameKo != null && !legacyNameKo.trim().isEmpty()) {
            return legacyNameKo;
        }
        if (presetNameEn != null && !presetNameEn.trim().isEmpty()) {
            return presetNameEn;
        }
        return legacyNameEn;
    }

    public LoadState defaultLoadStateValue() {
        return LoadState.fromId(defaultLoadState);
    }

    public ExercisePerformanceKey performanceKey(LoadState loadState) {
        return ExercisePerformanceKey.of(this, loadState);
    }

    public boolean hasVariantIdentity() {
        return familyId != null
                && !familyId.trim().isEmpty()
                && canonicalVariantKey != null
                && !canonicalVariantKey.trim().isEmpty();
    }
}
