package com.yeonsik.fitness.shared.feature.workout.ui

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp

internal actual fun fixedWorkoutDecimal(value: Double, digits: Int, grouped: Boolean): String {
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = grouped
        groupingSize = 3u
        minimumFractionDigits = digits.toULong()
        maximumFractionDigits = digits.toULong()
        roundingMode = NSNumberFormatterRoundHalfUp
    }
    return formatter.stringFromNumber(NSNumber(double = value)) ?: "—"
}
