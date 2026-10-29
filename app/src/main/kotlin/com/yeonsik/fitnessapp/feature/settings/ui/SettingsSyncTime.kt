package com.yeonsik.fitnessapp.feature.settings.ui

import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Formats only the existing sync timestamp; never invents a success time from the clock. */
internal fun formatSettingsSyncTime(
    timestamp: String,
    now: ZonedDateTime = ZonedDateTime.now()
): String {
    if (timestamp.isBlank()) return "최근 동기화 시각이 없습니다."
    val instant = try {
        OffsetDateTime.parse(timestamp).toInstant()
    } catch (_: DateTimeParseException) {
        return "최근 동기화 시각을 확인할 수 없습니다."
    }
    if (instant.isAfter(now.toInstant().plusSeconds(300))) {
        return "동기화 시각을 확인하려면 기기의 날짜와 시간을 확인하세요."
    }
    val local = instant.atZone(now.zone)
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN))
    val display = when (local.toLocalDate()) {
        now.toLocalDate() -> "오늘 $time"
        now.toLocalDate().minusDays(1) -> "어제 $time"
        else -> local.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREAN))
    }
    return "최근 동기화 · $display"
}
