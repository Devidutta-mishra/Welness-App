package com.example.yourswelnes.core.location

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM tests for the tracking-window math. `now` is injected so the assertions don't
 * depend on wall-clock time. Covers the edge cases that previously failed silently: overnight
 * windows, equal start/end, and non-`HH:mm` time formats.
 */
class LocationSchedulerTest {

    private val scheduler = LocationScheduler()

    private val HOUR_MS = 3_600_000L

    // --- #1 same-day window: unchanged behaviour ---------------------------

    @Test
    fun sameDayWindow_startInclusive_endExclusive() {
        val s = "06:00"; val e = "12:00"
        assertTrue(scheduler.isInTrackingWindow(s, e, LocalTime.of(6, 0)))   // start inclusive
        assertTrue(scheduler.isInTrackingWindow(s, e, LocalTime.of(8, 0)))   // mid
        assertFalse(scheduler.isInTrackingWindow(s, e, LocalTime.of(12, 0))) // end exclusive
        assertFalse(scheduler.isInTrackingWindow(s, e, LocalTime.of(5, 59)))
        assertFalse(scheduler.isInTrackingWindow(s, e, LocalTime.of(18, 0)))
    }

    // --- #1 overnight window (crosses midnight) ----------------------------

    @Test
    fun overnightWindow_isInsideAcrossMidnight() {
        val s = "22:00"; val e = "02:00"
        assertTrue(scheduler.isInTrackingWindow(s, e, LocalTime.of(22, 0)))  // start inclusive
        assertTrue(scheduler.isInTrackingWindow(s, e, LocalTime.of(23, 30))) // evening half
        assertTrue(scheduler.isInTrackingWindow(s, e, LocalTime.of(1, 0)))   // morning half
        assertFalse(scheduler.isInTrackingWindow(s, e, LocalTime.of(2, 0)))  // end exclusive
        assertFalse(scheduler.isInTrackingWindow(s, e, LocalTime.of(12, 0))) // midday outside
    }

    // --- #1 degenerate window ----------------------------------------------

    @Test
    fun equalStartAndEnd_isNeverInWindow() {
        assertFalse(scheduler.isInTrackingWindow("06:00", "06:00", LocalTime.of(6, 0)))
        assertFalse(scheduler.isInTrackingWindow("06:00", "06:00", LocalTime.of(15, 0)))
    }

    // --- #2 tolerant parsing -----------------------------------------------

    @Test
    fun tolerantParsing_acceptsSecondsSingleDigitHourAndWhitespace() {
        assertTrue(scheduler.isInTrackingWindow("6:00", "12:00", LocalTime.of(8, 0)))
        assertTrue(scheduler.isInTrackingWindow("06:00:00", "12:00:00", LocalTime.of(8, 0)))
        assertTrue(scheduler.isInTrackingWindow(" 06:00 ", "12:00", LocalTime.of(8, 0)))
    }

    @Test
    fun unparseableTime_isTreatedAsNotInWindow() {
        assertFalse(scheduler.isInTrackingWindow("garbage", "12:00", LocalTime.of(8, 0)))
        assertFalse(scheduler.isInTrackingWindow("06:00", "25:61", LocalTime.of(8, 0)))
    }

    // --- millisUntilWindowEnd ----------------------------------------------

    @Test
    fun millisUntilWindowEnd_sameDay() {
        assertEquals(2 * HOUR_MS, scheduler.millisUntilWindowEnd("12:00", LocalTime.of(10, 0)))
    }

    @Test
    fun millisUntilWindowEnd_overnightWrapsPastMidnight() {
        assertEquals(3 * HOUR_MS, scheduler.millisUntilWindowEnd("02:00", LocalTime.of(23, 0)))
        assertEquals(1 * HOUR_MS, scheduler.millisUntilWindowEnd("02:00", LocalTime.of(1, 0)))
    }

    @Test
    fun millisUntilWindowEnd_unparseable_isZero() {
        assertEquals(0L, scheduler.millisUntilWindowEnd("nope", LocalTime.of(10, 0)))
    }

    // --- millisUntilWindowStart --------------------------------------------

    @Test
    fun millisUntilWindowStart_laterToday() {
        assertEquals(2 * HOUR_MS, scheduler.millisUntilWindowStart("08:00", LocalTime.of(6, 0)))
    }

    @Test
    fun millisUntilWindowStart_alreadyPassed_wrapsToTomorrow() {
        assertEquals(23 * HOUR_MS, scheduler.millisUntilWindowStart("06:00", LocalTime.of(7, 0)))
    }

    // --- nextWindowStartEpochMillis (exact-alarm trigger instant) -----------

    @Test
    fun nextWindowStart_laterToday_returnsTodayInstant() {
        val now = LocalDateTime.of(2026, 6, 8, 6, 0)
        val expected = LocalDateTime.of(2026, 6, 8, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, scheduler.nextWindowStartEpochMillis("09:00", now, ZoneOffset.UTC))
    }

    @Test
    fun nextWindowStart_alreadyPassed_rollsToTomorrow() {
        val now = LocalDateTime.of(2026, 6, 8, 10, 0)
        val expected = LocalDateTime.of(2026, 6, 9, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, scheduler.nextWindowStartEpochMillis("09:00", now, ZoneOffset.UTC))
    }

    @Test
    fun nextWindowStart_exactlyAtStart_rollsToTomorrow() {
        // Equal-to-now must roll forward so a re-arm right after the alarm fires targets the NEXT
        // day rather than re-firing for the same instant.
        val now = LocalDateTime.of(2026, 6, 8, 9, 0)
        val expected = LocalDateTime.of(2026, 6, 9, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, scheduler.nextWindowStartEpochMillis("09:00", now, ZoneOffset.UTC))
    }

    @Test
    fun nextWindowStart_unparseable_returnsNull() {
        assertNull(scheduler.nextWindowStartEpochMillis("garbage", LocalDateTime.of(2026, 6, 8, 6, 0)))
    }
}
