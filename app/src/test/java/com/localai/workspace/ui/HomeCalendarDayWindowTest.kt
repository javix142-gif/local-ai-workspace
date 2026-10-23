package com.localai.workspace.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCalendarDayWindowTest {
    @Test fun dayQueryIncludesTheDateAndExcludesTheFollowingMidnight() {
        val date = LocalDate.of(2026, 10, 22)
        val zone = ZoneId.of("America/Los_Angeles")
        val window = homeCalendarDayWindow(date, zone)

        assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), window.startEpochMillis)
        assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), window.endExclusiveEpochMillis)

        val candidates = listOf(
            window.startEpochMillis - 1,
            window.startEpochMillis,
            window.endExclusiveEpochMillis - 1,
            window.endExclusiveEpochMillis,
        )
        val included = candidates.filter { it >= window.startEpochMillis && it < window.endExclusiveEpochMillis }
        assertEquals(listOf(window.startEpochMillis, window.endExclusiveEpochMillis - 1), included)
    }

    @Test fun localDayBoundsFollowDaylightSavingTransitions() {
        val date = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val window = homeCalendarDayWindow(date, zone)

        assertEquals(date, java.time.Instant.ofEpochMilli(window.startEpochMillis).atZone(zone).toLocalDate())
        assertEquals(date.plusDays(1), java.time.Instant.ofEpochMilli(window.endExclusiveEpochMillis).atZone(zone).toLocalDate())
        assertTrue("spring DST day is 23 hours, not an assumed fixed 24 hours",
            window.endExclusiveEpochMillis - window.startEpochMillis == 23 * 60 * 60 * 1000L)
    }
}
