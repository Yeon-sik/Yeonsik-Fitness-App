package com.yeonsik.fitness.shared.feature.exercise.model

import kotlin.jvm.JvmField

/**
 * Stable family/preset/variant identity resolved from the normative catalog.
 *
 * The legacy exercise ID remains part of this object so old routines and records can be
 * addressed without rewriting historical rows. Load state is intentionally exposed separately
 * and is persisted on each set.
 */
class ExerciseFamilyIdentity(
    @JvmField val legacyExerciseId: String?,
    @JvmField val familyId: String?,
    @JvmField val presetId: String?,
    @JvmField val canonicalPresetId: String?,
    @JvmField val presetNameKo: String?,
    @JvmField val presetNameEn: String?,
    @JvmField val legacyNameKo: String?,
    @JvmField val legacyNameEn: String?,
    @JvmField val defaultUiPart: String?,
    @JvmField val canonicalVariantKey: String?,
    @JvmField val visualVariantKey: String?,
    @JvmField val illustrationKey: String?,
    @JvmField val defaultLoadState: String?,
    @JvmField val legacyRecordType: String?,
    @JvmField val variantJson: String?
) {
    /** Detailed preset/legacy name for routine, workout, and history rows. */
    fun displayName(): String? {
        if (presetNameKo != null && presetNameKo.trim().isNotEmpty()) {
            return presetNameKo
        }
        if (legacyNameKo != null && legacyNameKo.trim().isNotEmpty()) {
            return legacyNameKo
        }
        if (presetNameEn != null && presetNameEn.trim().isNotEmpty()) {
            return presetNameEn
        }
        return legacyNameEn
    }

    fun defaultLoadStateValue(): LoadState? = LoadState.fromId(defaultLoadState)

    fun performanceKey(loadState: LoadState?): ExercisePerformanceKey? =
        ExercisePerformanceKey.of(this, loadState)

    fun hasVariantIdentity(): Boolean {
        return familyId != null
            && familyId.trim().isNotEmpty()
            && canonicalVariantKey != null
            && canonicalVariantKey.trim().isNotEmpty()
    }
}
