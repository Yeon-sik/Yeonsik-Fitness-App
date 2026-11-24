package com.yeonsik.fitnessapp.feature.workout.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared interpretation for completed workout facts at the storage boundary. */
internal object WorkoutReadSemantics {
    private val legacyClock = DateTimeFormatter.ofPattern("H:mm[:ss]", Locale.ROOT)
    private val fleekZone = ZoneId.of("Asia/Seoul")

    fun isCompleted(sourceApp: String?, metadata: String?): Boolean {
        if (sourceApp == "os") return true
        return metadata.orEmpty().filterNot(Char::isWhitespace)
            .contains("\"status\":\"completed\"")
    }

    fun legacyLocalStart(date: String, startedAt: String): LocalDateTime? = runCatching {
        LocalDate.parse(date).atTime(LocalTime.parse(startedAt, legacyClock))
    }.getOrNull()

    fun recordedStartAt(date: String, startedAt: String, fleek: Boolean, reportedTime: Boolean?): String {
        if (startedAt.isBlank()) return date
        if (fleek) {
            val reported = reportedTime ?: runCatching {
                OffsetDateTime.parse(startedAt).toInstant() != LocalDate.parse(date).atStartOfDay(fleekZone).toInstant()
            }.getOrDefault(true)
            if (!reported) return date
        }
        return legacyLocalStart(date, startedAt)?.toString() ?: startedAt
    }
}
