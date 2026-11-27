package com.yeonsik.fitness.shared.feature.workout.model

/** Values for an ordinary weight/reps set. Empty cells stay absent, including an empty load. */
data class OrdinarySetEntry(
    val inputLoad: Double?,
    val unit: MassUnit,
    val weightKg: Double?,
    val reps: Int?,
    val rir: Int?
)

object OrdinarySetRules {
    fun read(
        weightText: String, repsText: String, rirText: String, unit: MassUnit,
        originalText: String, originalLoad: Double?, originalKg: Double?
    ): OrdinarySetEntry? {
        val weight = weightText.trim()
        val reps = repsText.trim()
        val rir = rirText.trim()
        // In particular, never parse a one-decimal display value over a more precise stored load.
        val useOriginal = weight.isNotEmpty() && weight == originalText &&
            originalKg != null && originalKg.isFinite() && originalKg >= 0.0
        val inputLoad = when {
            useOriginal -> originalLoad?.takeIf { it.isFinite() && it >= 0.0 }
            weight.isEmpty() -> null
            else -> weight.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        }
        val count = if (reps.isEmpty()) null else reps.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val reserve = if (rir.isEmpty()) null else rir.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return OrdinarySetEntry(inputLoad, unit, if (useOriginal) originalKg else inputLoad?.let(unit::toKg), count, reserve)
    }

    fun canRemove(count: Int, setIndex: Long, isFirst: Boolean): Boolean =
        count > 1 && setIndex > 1 && !isFirst
}
