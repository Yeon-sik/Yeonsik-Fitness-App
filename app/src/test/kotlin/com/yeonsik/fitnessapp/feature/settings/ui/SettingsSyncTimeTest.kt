package com.yeonsik.fitnessapp.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class SettingsSyncTimeTest {
    private val now = ZonedDateTime.parse("2026-10-20T18:00:00+09:00[Asia/Seoul]")

    @Test fun formatsLocalTodayWithoutFractionalSeconds() {
        assertEquals("최근 동기화 · 오늘 17:57",
            formatSettingsSyncTime("2026-10-20T17:57:51.249484+09:00", now))
    }

    @Test fun utcInstantIsConvertedBeforeChoosingTheDateLabel() {
        assertEquals("최근 동기화 · 오늘 00:30",
            formatSettingsSyncTime("2026-10-19T15:30:00Z", now))
        assertEquals("최근 동기화 · 어제 23:30",
            formatSettingsSyncTime("2026-10-19T14:30:00Z", now))
    }

    @Test fun olderDatesIncludeTheirYear() {
        assertEquals("최근 동기화 · 2025.12.31 10:05",
            formatSettingsSyncTime("2025-12-31T01:05:00Z", now))
    }

    @Test fun missingInvalidOrUnzonedTimestampsAreNotReplacedWithNow() {
        assertEquals("최근 동기화 시각이 없습니다.", formatSettingsSyncTime("", now))
        listOf("not a date", "2026-10-20T10:00:00", "2026-02-30T10:00:00Z").forEach {
            assertEquals("최근 동기화 시각을 확인할 수 없습니다.", formatSettingsSyncTime(it, now))
        }
    }

    @Test fun futureClockMismatchDoesNotClaimRecentSuccess() {
        assertEquals("동기화 시각을 확인하려면 기기의 날짜와 시간을 확인하세요.",
            formatSettingsSyncTime("2026-10-21T10:00:00Z", now))
    }

    @Test fun deviceTimezoneControlsDisplay() {
        val utc = now.withZoneSameInstant(java.time.ZoneId.of("UTC"))
        assertEquals("최근 동기화 · 오늘 08:57",
            formatSettingsSyncTime("2026-10-20T17:57:51+09:00", utc))
    }
}
