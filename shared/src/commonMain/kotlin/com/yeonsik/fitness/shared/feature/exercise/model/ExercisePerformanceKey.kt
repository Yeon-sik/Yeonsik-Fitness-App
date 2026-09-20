package com.yeonsik.fitness.shared.feature.exercise.model

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Comparable performance bucket. PR/e1RM/weight statistics stay inside this exact key. */
class ExercisePerformanceKey private constructor(
    @JvmField val familyId: String?,
    @JvmField val canonicalVariantKey: String?,
    @JvmField val loadState: LoadState
) {
    fun isComparableTo(other: ExercisePerformanceKey?): Boolean {
        return other != null
            && familyId == other.familyId
            && canonicalVariantKey == other.canonicalVariantKey
            && loadState == other.loadState
    }

    fun stableValue(): String = "$familyId|$canonicalVariantKey|${loadState.id()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExercisePerformanceKey) return false
        return familyId == other.familyId
            && canonicalVariantKey == other.canonicalVariantKey
            && loadState == other.loadState
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + (familyId?.hashCode() ?: 0)
        result = 31 * result + (canonicalVariantKey?.hashCode() ?: 0)
        result = 31 * result + loadState.hashCode()
        return result
    }

    override fun toString(): String = stableValue()

    companion object {
        @JvmStatic
        fun of(
            identity: ExerciseFamilyIdentity?,
            loadState: LoadState?
        ): ExercisePerformanceKey? {
            if (identity == null || !identity.hasVariantIdentity() || loadState == null) {
                return null
            }
            return ExercisePerformanceKey(
                identity.familyId,
                identity.canonicalVariantKey,
                loadState
            )
        }
    }
}
