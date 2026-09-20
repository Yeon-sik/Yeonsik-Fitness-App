package com.yeonsik.fitness.shared.feature.exercise.model

import kotlin.jvm.JvmStatic

enum class EquipmentType(
    private val persistedId: String,
    private val koreanLabel: String
) {
    BODYWEIGHT("bodyweight", "맨몸"),
    MACHINE("machine", "머신"),
    DUMBBELL("dumbbell", "덤벨"),
    BARBELL("barbell", "바벨"),
    SMITH_MACHINE("smith_machine", "스미스 머신"),
    CABLE("cable", "케이블"),
    BAND("band", "밴드"),
    KETTLEBELL("kettlebell", "케틀벨"),
    PLATE("plate", "플레이트"),
    OTHER("other", "기타");

    val id: String
        get() = persistedId

    fun id(): String = persistedId

    val labelKo: String
        get() = koreanLabel

    fun labelKo(): String = koreanLabel

    companion object {
        @JvmStatic
        fun fromId(value: String?): EquipmentType? {
            if (value == null) return null
            val normalized = value.trim().lowercase()
            return values().firstOrNull { it.persistedId == normalized }
        }
    }
}
