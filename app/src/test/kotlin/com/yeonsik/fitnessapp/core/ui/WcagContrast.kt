package com.yeonsik.fitnessapp.core.ui

import kotlin.math.pow

/** WCAG 2.x contrast for ARGB token integers, without Android dependencies. */
internal object WcagContrast {
    fun rgb(argb: Int) = DoubleArray(3) { ((argb shr (16 - it * 8)) and 0xFF) / 255.0 }

    /** Composites [top] at [alpha] over an opaque [bottom]. */
    fun over(top: DoubleArray, alpha: Float, bottom: DoubleArray) =
        DoubleArray(3) { alpha * top[it] + (1 - alpha) * bottom[it] }

    fun ratio(foreground: DoubleArray, background: DoubleArray): Double {
        val (high, low) = listOf(luminance(foreground), luminance(background)).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }

    fun ratio(foreground: Int, background: Int) = ratio(rgb(foreground), rgb(background))

    private fun luminance(color: DoubleArray): Double {
        val linear = color.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }
}
