package com.example.yourswelnes.core.location

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.notification.LocationNotificationManager
import com.example.yourswelnes.feature.location.data.repository.LocationRepository
import com.example.yourswelnes.feature.location.domain.model.LocationRecord
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class LocationForegroundService : android.app.Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var locationScheduler: LocationScheduler
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var locationPrefs: LocationPreferencesDataStore
    @Inject lateinit var authPrefs: AuthPreferencesDataStore
    @Inject lateinit var locationNotificationManager: LocationNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        try {
            ServiceCompat.startForeground(
                this,
                LocationNotificationManager.NOTIFICATION_ID_SERVICE,
                locationNotificationManager.buildServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            LocationServiceState.setRunning(true)
        } catch (e: SecurityException) {
            // Location permission not yet granted or app not in eligible foreground state.
            // Stop immediately — the service will be restarted once the user grants permission.
            Timber.e(e, "Cannot start location FGS — permission missing or not in foreground")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
            // ACTION_START or null (null = Android restarted the service via START_STICKY)
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = serviceScope.launch {
            val token = authPrefs.getToken()
            if (token.isNullOrBlank()) {
                Timber.w("No auth token — stopping location service")
                stopSelf()
                return@launch
            }

            val startTime = locationPrefs.getTrackingStartTime() ?: "06:00"
            val endTime = locationPrefs.getTrackingEndTime() ?: "12:00"
            val intervalMs = locationPrefs.getTrackingIntervalSeconds() * 1000L

            Timber.d("Location tracking started. Window: $startTime–$endTime, interval: ${intervalMs}ms")

            while (isActive) {
                if (locationScheduler.isInTrackingWindow(startTime, endTime)) {
                    collectAndSaveLocation()
                    delay(intervalMs)
                } else {
                    val waitMs = locationScheduler.millisUntilWindowStart(startTime)
                    Timber.d("Outside tracking window — resuming in ${waitMs / 60_000L} min")
                    delay(waitMs.coerceAtLeast(60_000L))
                }
            }
        }
    }

    private suspend fun collectAndSaveLocation() {
        val location = locationTracker.getCurrentLocation()
        if (location == null) {
            if (!isGpsEnabled()) {
                Timber.w("GPS disabled during tracking window")
                locationNotificationManager.showGpsDisabledNotification()
            }
            return
        }
        locationNotificationManager.cancelGpsDisabledNotification()

        val clubLat = locationPrefs.getClubLatitude()
        val clubLon = locationPrefs.getClubLongitude()
        if (clubLat == null || clubLon == null) {
            Timber.w("Club coordinates not cached — skipping location save")
            return
        }

        val distance = locationScheduler.calculateDistance(
            location.latitude, location.longitude, clubLat, clubLon
        )

        locationRepository.saveLocation(
            LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                distance = distance,
                timestamp = location.time,
                createdAt = System.currentTimeMillis()
            )
        )
        Timber.d("Location saved: (${location.latitude}, ${location.longitude}) dist=${distance}m")
    }

    private fun isGpsEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        locationNotificationManager.cancelGpsDisabledNotification()
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        LocationServiceState.setRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.yourswelnes.ACTION_LOCATION_START"
        const val ACTION_STOP = "com.example.yourswelnes.ACTION_LOCATION_STOP"

        fun startIntent(context: Context) =
            Intent(context, LocationForegroundService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, LocationForegroundService::class.java).apply { action = ACTION_STOP }
    }
}
