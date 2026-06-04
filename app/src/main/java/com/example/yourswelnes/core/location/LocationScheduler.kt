package com.example.yourswelnes.core.location

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LocationScheduler @Inject constructor() {

    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isInTrackingWindow(startTime: String, endTime: String): Boolean {
        return try {
            val now = LocalTime.now()
            val start = LocalTime.parse(startTime, formatter)
            val end = LocalTime.parse(endTime, formatter)
            !now.isBefore(start) && now.isBefore(end)
        } catch (e: Exception) {
            Timber.e(e, "Failed to evaluate tracking window [$startTime–$endTime]")
            false
        }
    }

    /** Returns milliseconds until the current window closes. Returns 0 if already outside the window. */
    fun millisUntilWindowEnd(endTime: String): Long {
        return try {
            val now = LocalTime.now()
            val end = LocalTime.parse(endTime, formatter)
            val nowSeconds = now.toSecondOfDay().toLong()
            val endSeconds = end.toSecondOfDay().toLong()
            if (endSeconds > nowSeconds) (endSeconds - nowSeconds) * 1000L else 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate time until window end [$endTime]")
            0L
        }
    }

    /** Returns milliseconds until the next window open. Returns 0 if already inside the window. */
    fun millisUntilWindowStart(startTime: String): Long {
        return try {
            val now = LocalTime.now()
            val start = LocalTime.parse(startTime, formatter)
            val nowSeconds = now.toSecondOfDay().toLong()
            val startSeconds = start.toSecondOfDay().toLong()
            if (startSeconds > nowSeconds) {
                (startSeconds - nowSeconds) * 1000L
            } else {
                (86400L - nowSeconds + startSeconds) * 1000L
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate time until window start [$startTime]")
            0L
        }
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
}
