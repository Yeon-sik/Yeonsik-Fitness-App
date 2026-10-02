package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.graphics.Color

/** Fixed record-domain colors shared by the Records calendar and Home status markers. */
object FitnessRecordMarkerColors {
    val byKey: Map<String, Color> = mapOf(
        "workout" to Color(0xFFEF4444),
        "meal" to Color(0xFFFACC15),
        "body" to Color(0xFF10B981)
    )
}
