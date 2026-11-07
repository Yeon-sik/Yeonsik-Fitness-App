package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parses the session window typed on the probe screen.
 *
 * - date: `yyyy-MM-dd`
 * - start / end: `H:mm` or `H:mm:ss`. An end at or before the start is read as the next day.
 * - pauses: `H:mm-H:mm` entries separated by `,` or `;` (`~` is also accepted between the two
 *   times). Each pause is placed on the first day where it does not begin before the session.
 */
object SessionWindowParser {
    sealed interface Result {
        data class Parsed(val window: SessionWindow) : Result
        data class Invalid(val reason: String) : Result
    }

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm[:ss]")

    fun parse(date: String, start: String, end: String, pauses: String, zone: ZoneId): Result {
        val day = parseDate(date) ?: return Result.Invalid("날짜는 yyyy-MM-dd 형식이어야 합니다.")
        val startTime = parseTime(start) ?: return Result.Invalid("시작 시각은 HH:mm 또는 HH:mm:ss 형식이어야 합니다.")
        val endTime = parseTime(end) ?: return Result.Invalid("종료 시각은 HH:mm 또는 HH:mm:ss 형식이어야 합니다.")

        val startAt = day.atTime(startTime).atZone(zone).toInstant()
        val endAt = resolveNotBefore(day, endTime, zone, startAt, allowEqual = false)

        val parsedPauses = mutableListOf<TimeInterval>()
        val entries = pauses.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        for (entry in entries) {
            val parts = entry.split('-', '~').map { it.trim() }
            if (parts.size != 2) {
                return Result.Invalid("일시정지는 HH:mm-HH:mm 형식으로 쉼표로 구분해 입력합니다: $entry")
            }
            val pauseStartTime = parseTime(parts[0])
                ?: return Result.Invalid("일시정지 시작 시각을 읽을 수 없습니다: $entry")
            val pauseEndTime = parseTime(parts[1])
                ?: return Result.Invalid("일시정지 종료 시각을 읽을 수 없습니다: $entry")
            val pauseStart = resolveNotBefore(day, pauseStartTime, zone, startAt, allowEqual = true)
            val pauseEnd = resolveNotBefore(day, pauseEndTime, zone, pauseStart, allowEqual = false)
            parsedPauses += TimeInterval(pauseStart, pauseEnd)
        }

        return try {
            Result.Parsed(SessionWindow(startAt, endAt, parsedPauses))
        } catch (e: IllegalArgumentException) {
            Result.Invalid("일시정지는 세션 안에 있어야 하고 서로 겹치면 안 됩니다.")
        }
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim(), timeFormat)
    } catch (e: DateTimeParseException) {
        null
    }

    /** Places [time] on [day] or the following day so it is not before [reference]. */
    private fun resolveNotBefore(
        day: LocalDate,
        time: LocalTime,
        zone: ZoneId,
        reference: Instant,
        allowEqual: Boolean,
    ): Instant {
        val sameDay = day.atTime(time).atZone(zone).toInstant()
        val acceptable = if (allowEqual) !sameDay.isBefore(reference) else sameDay.isAfter(reference)
        return if (acceptable) sameDay else day.plusDays(1).atTime(time).atZone(zone).toInstant()
    }
}
