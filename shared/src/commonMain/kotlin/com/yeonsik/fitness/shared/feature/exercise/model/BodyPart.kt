package com.yeonsik.fitness.shared.feature.exercise.model

import kotlin.jvm.JvmStatic

enum class BodyPart(
    private val persistedId: String,
    private val koreanLabel: String
) {
    CHEST("chest", "가슴"),
    BACK("back", "등"),
    LEGS("legs", "하체"),
    SHOULDERS("shoulders", "어깨"),
    ARMS("arms", "팔"),
    ABS("abs", "복근");

    val id: String
        get() = persistedId

    fun id(): String = persistedId

    val labelKo: String
        get() = koreanLabel

    fun labelKo(): String = koreanLabel

    companion object {
        @JvmStatic
        fun fromId(value: String?): BodyPart? {
            if (value == null) return null
            val normalized = value.trim().lowercase()
            return values().firstOrNull { it.persistedId == normalized }
        }
    }
}
