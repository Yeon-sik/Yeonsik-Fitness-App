package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWindowParserTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun parsed(date: String, start: String, end: String, pauses: String = ""): SessionWindow {
        val result = SessionWindowParser.parse(date, start, end, pauses, seoul)
        assertTrue("expected a parsed window but got $result", result is SessionWindowParser.Result.Parsed)
        return (result as SessionWindowParser.Result.Parsed).window
    }

    private fun invalid(date: String, start: String, end: String, pauses: String = "") {
        val result = SessionWindowParser.parse(date, start, end, pauses, seoul)
        assertTrue("expected invalid input but got $result", result is SessionWindowParser.Result.Invalid)
    }

    @Test
    fun parsesLocalTimesInTheGivenZoneWithPauses() {
        val window = parsed("2026-11-05", "07:30", "08:10:30", "07:45-07:50, 07:55~07:57")
        assertEquals(Instant.parse("2026-11-04T22:30:00Z"), window.start)
        assertEquals(Instant.parse("2026-11-04T23:10:30Z"), window.end)
        assertEquals(2, window.pauses.size)
        assertEquals(Duration.ofMinutes(7), window.pauses.fold(Duration.ZERO) { sum, pause -> sum.plus(pause.duration) })
        assertEquals(Duration.ofSeconds(40 * 60 + 30 - 7 * 60), window.activeDuration())
        assertEquals(3, window.activeIntervals().size)
    }

    @Test
    fun endAtOrBeforeStartIsTheNextDayAndPausesFollowAcrossMidnight() {
        val window = parsed("2026-11-05", "23:40", "00:20", "23:55-00:05")
        assertEquals(Duration.ofMinutes(40), window.duration)
        assertEquals(Duration.ofMinutes(30), window.activeDuration())
        assertEquals(Duration.ofMinutes(10), window.pauses.single().duration)
    }

    @Test
    fun rejectsMalformedInput() {
        invalid("2026/11/05", "07:30", "08:00")
        invalid("2026-11-05", "7시", "08:00")
        invalid("2026-11-05", "07:30", "")
        invalid("2026-11-05", "07:30", "08:00", "07:40")
        invalid("2026-11-05", "07:30", "08:00", "07:40-ab")
    }

    @Test
    fun rejectsPausesOutsideTheSessionOrOverlappingEachOther() {
        invalid("2026-11-05", "07:30", "08:00", "07:50-08:10")
        invalid("2026-11-05", "07:30", "08:00", "07:00-07:05")
        invalid("2026-11-05", "07:30", "08:00", "07:40-07:50, 07:45-07:55")
    }

    @Test
    fun touchingPausesAreAllowedAndBlankPauseTextMeansNoPause() {
        val window = parsed("2026-11-05", "07:30", "08:00", "07:40-07:45;07:45-07:50")
        assertEquals(Duration.ofMinutes(20), window.activeDuration())
        assertTrue(parsed("2026-11-05", "07:30", "08:00", "  ").pauses.isEmpty())
    }
}
