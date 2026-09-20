package com.yeonsik.fitness.shared.feature.workout.model

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Supported user-facing mass units. Stored workout mass remains canonical kilograms. */
enum class MassUnit(
    private val persistedId: String,
    private val koreanLabel: String
) {
    KG("kg", "킬로그램"),
    LB("lb", "파운드");

    val id: String
        get() = persistedId

    fun id(): String = persistedId

    val labelKo: String
        get() = koreanLabel

    fun labelKo(): String = koreanLabel

    fun symbol(): String = persistedId

    companion object {
        @JvmField
        val KILOGRAMS_PER_POUND: Double = 0.45359237

        @JvmStatic
        fun parse(value: String?): MassUnit? {
            if (value == null) return null
            val normalized = value.trim().lowercase()
            return values().firstOrNull { it.persistedId == normalized }
        }

        @JvmStatic
        fun orDefault(unit: MassUnit?): MassUnit = unit ?: KG

        @JvmStatic
        fun toKg(value: Double, unit: MassUnit?): Double {
            val effective = orDefault(unit)
            return if (effective == LB) value * KILOGRAMS_PER_POUND else value
        }

        @JvmStatic
        fun fromKg(kilograms: Double, unit: MassUnit?): Double {
            val effective = orDefault(unit)
            return if (effective == LB) kilograms / KILOGRAMS_PER_POUND else kilograms
        }

        @JvmStatic
        fun convert(value: Double, from: MassUnit?, to: MassUnit?): Double =
            fromKg(toKg(value, from), to)
    }

    fun toKg(value: Double): Double = toKg(value, this)

    fun fromKg(kilograms: Double): Double = fromKg(kilograms, this)
}
