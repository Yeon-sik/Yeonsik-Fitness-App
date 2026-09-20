package com.yeonsik.fitness.shared.feature.exercise.model

import kotlin.jvm.JvmStatic

/** Resistance semantics for one recorded set. This is deliberately not an exercise identity. */
enum class LoadState(private val persistedId: String) {
    BODYWEIGHT("bodyweight"),
    EXTERNAL_LOAD("external_load"),
    ADDED_WEIGHT("added_weight"),
    ASSISTED("assisted"),
    BAND_ASSISTED("band_assisted"),
    BAND_RESISTED("band_resisted");

    val id: String
        get() = persistedId

    fun id(): String = persistedId

    fun isBodyweightCapable(): Boolean {
        return this == BODYWEIGHT
            || this == ADDED_WEIGHT
            || this == ASSISTED
            || this == BAND_ASSISTED
    }

    fun isBandSemantics(): Boolean {
        return this == BAND_ASSISTED || this == BAND_RESISTED
    }

    companion object {
        @JvmStatic
        fun fromId(value: String?): LoadState? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return values().firstOrNull { it.persistedId == normalized }
        }
    }
}
