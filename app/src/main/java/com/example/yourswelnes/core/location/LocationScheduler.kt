package com.example.yourswelnes.core.location

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LocationScheduler @Inject constructor() {

    // Accepts single- or double-digit hours and an optional seconds component (H:mm, HH:mm,
    // HH:mm:ss) so a variation in the backend's time format doesn't silently disable collection.
    private val timeFormatter = DateTimeFormatter.ofPattern("H:mm[:ss]")

    private fun parseTimeOrNull(raw: String): LocalTime? =
        try {
            LocalTime.parse(raw.trim(), timeFormatter)
        } catch (e: Exception) {
            Timber.e(e, "Unparseable tracking time: '%s'", raw)
            null
        }

    /**
     * True if [now] is inside the [startTime]..[endTime] window. Handles both same-day windows
     * (start < end, e.g. 06:00–12:00) and windows that cross midnight (start > end, e.g.
     * 22:00–02:00). start == end is treated as a zero-length window (never open). Start is
     * inclusive, end is exclusive. Returns false on unparseable input.
     *
     * [now] is injectable for testing and defaults to the current local time.
     */
    fun isInTrackingWindow(
        startTime: String,
        endTime: String,
        now: LocalTime = LocalTime.now()
    ): Boolean {
        val start = parseTimeOrNull(startTime) ?: return false
        val end = parseTimeOrNull(endTime) ?: return false
        return when {
            start.isBefore(end) -> !now.isBefore(start) && now.isBefore(end) // same day
            start.isAfter(end) -> !now.isBefore(start) || now.isBefore(end)  // crosses midnight
            else -> false                                                    // start == end
        }
    }

    /**
     * Milliseconds until the current window closes. Only meaningful when [now] is inside the
     * window (its sole caller invokes it from the in-window branch); for an overnight window it
     * correctly wraps past midnight. Returns 0 on unparseable input.
     */
    fun millisUntilWindowEnd(endTime: String, now: LocalTime = LocalTime.now()): Long {
        val end = parseTimeOrNull(endTime) ?: return 0L
        val nowSeconds = now.toSecondOfDay().toLong()
        val endSeconds = end.toSecondOfDay().toLong()
        val remaining =
            if (endSeconds > nowSeconds) endSeconds - nowSeconds
            else SECONDS_PER_DAY - nowSeconds + endSeconds
        return remaining * 1000L
    }

    /** Milliseconds until the next window opens (wraps to tomorrow if start already passed). 0 on bad input. */
    fun millisUntilWindowStart(startTime: String, now: LocalTime = LocalTime.now()): Long {
        val start = parseTimeOrNull(startTime) ?: return 0L
        val nowSeconds = now.toSecondOfDay().toLong()
        val startSeconds = start.toSecondOfDay().toLong()
        return if (startSeconds > nowSeconds) {
            (startSeconds - nowSeconds) * 1000L
        } else {
            (SECONDS_PER_DAY - nowSeconds + startSeconds) * 1000L
        }
    }

    /**
     * Epoch millis (wall-clock / RTC) of the next time [startTime] occurs. This is the trigger
     * instant for the exact alarm that opens the tracking window (see TrackingAlarmScheduler).
     * If today's start time has not yet passed it returns today's instant; otherwise it rolls to
     * tomorrow. Equal-to-now also rolls forward so a re-arm right after the alarm fires targets the
     * NEXT day, not the same instant. Returns null on unparseable input so the caller can skip
     * arming rather than schedule a bogus alarm.
     *
     * [now] and [zone] are injectable for deterministic testing and default to the device clock.
     */
    fun nextWindowStartEpochMillis(
        startTime: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val start = parseTimeOrNull(startTime) ?: return null
        var next = now.toLocalDate().atTime(start)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    /** Distance in meters between two lat/lon points using Android's Location API. */
    fun calculateDistance(
        userLat: Double,
        userLon: Double,
        clubLat: Double,
        clubLon: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(userLat, userLon, clubLat, clubLon, results)
        return results[0]
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
