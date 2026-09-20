package com.yeonsik.fitness.shared.feature.body.model

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Immutable body profile read model. Storage and write ownership remain platform-side. */
class BodyProfile(
    @JvmField val heightCm: Int?,
    createdAt: String?,
    updatedAt: String?
) {
    init {
        require(heightCm == null || heightCm in 50..300) {
            "키는 50~300cm 범위여야 합니다."
        }
    }

    @JvmField val createdAt: String = normalizeTimestamp(createdAt)
    @JvmField val updatedAt: String = normalizeTimestamp(updatedAt)

    fun isConfigured(): Boolean = heightCm != null

    fun heightLabelKo(): String = heightCm?.let { "${it}cm" } ?: "미설정"

    companion object {
        @JvmStatic
        fun empty(): BodyProfile = BodyProfile(null, "", "")

        private fun normalizeTimestamp(value: String?): String = value?.trim() ?: ""
    }
}
