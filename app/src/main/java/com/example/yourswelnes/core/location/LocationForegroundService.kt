package com.example.yourswelnes.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.notification.LocationNotificationManager
import com.example.yourswelnes.feature.location.data.repository.LocationConfigRepository
import com.example.yourswelnes.feature.location.data.repository.LocationRepository
import com.example.yourswelnes.feature.location.domain.model.LocationRecord
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val TAG = "LocationService"
private const val CONFIG_REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes

@AndroidEntryPoint
class LocationForegroundService : android.app.Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var locationScheduler: LocationScheduler
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var locationPrefs: LocationPreferencesDataStore
    @Inject lateinit var authPrefs: AuthPreferencesDataStore
    @Inject lateinit var locationNotificationManager: LocationNotificationManager
    @Inject lateinit var locationConfigRepository: LocationConfigRepository
    @Inject lateinit var locationUploader: LocationUploader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var configRefreshJob: Job? = null
    private var uploadJob: Job? = null

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val gpsStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != LocationManager.PROVIDERS_CHANGED_ACTION) return
            serviceScope.launch {
                val gpsOn = isGpsEnabled()
                val startTime = locationPrefs.getTrackingStartTime() ?: "06:00"
                val endTime = locationPrefs.getTrackingEndTime() ?: "12:00"
                val inWindow = locationScheduler.isInTrackingWindow(startTime, endTime)
                if (!gpsOn && inWindow) {
                    Timber.tag(TAG).w("GPS toggled OFF during tracking window — showing notification immediately")
                    locationNotificationManager.showGpsDisabledNotification()
                } else if (gpsOn) {
                    Timber.tag(TAG).d("GPS toggled ON — dismissing GPS-disabled notification")
                    locationNotificationManager.cancelGpsDisabledNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("onCreate — starting foreground service")
        try {
            ServiceCompat.startForeground(
                this,
                LocationNotificationManager.NOTIFICATION_ID_SERVICE,
                locationNotificationManager.buildServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            LocationServiceState.setRunning(true)
            Timber.tag(TAG).d("Foreground service promoted successfully")
            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(gpsStateReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(gpsStateReceiver, filter)
            }
            Timber.tag(TAG).d("GPS state receiver registered")
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Cannot start FGS — permission missing or not in foreground state")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand action=${intent?.action ?: "null (Android restart)"}")
        when (intent?.action) {
            ACTION_STOP -> {
                Timber.tag(TAG).d("Stop action received — shutting down")
                stopTracking()
                stopSelf()
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) {
            Timber.tag(TAG).d("Tracking job already active — ignoring duplicate start")
            return
        }

        startConfigRefreshLoop()
        startUploadLoop()

        trackingJob = serviceScope.launch {
            val token = authPrefs.getToken()
            if (token.isNullOrBlank()) {
                Timber.tag(TAG).w("No auth token found — stopping service")
                stopSelf()
                return@launch
            }
            Timber.tag(TAG).d("Auth token present — fetching tracking config from API")

            // Fetch fresh config from backend; falls back to DataStore cache if network fails
            refreshConfigFromApi()

            Timber.tag(TAG).d("Starting location collection loop")
            while (isActive) {
                // Safety net: stop immediately if token was cleared (e.g. logout)
                if (authPrefs.getToken().isNullOrBlank()) {
                    Timber.tag(TAG).w("Auth token gone mid-session — stopping service")
                    stopSelf()
                    return@launch
                }

                // Re-read config every iteration so backend changes take effect without restart
                val startTime = locationPrefs.getTrackingStartTime() ?: "06:00"
                val endTime = locationPrefs.getTrackingEndTime() ?: "12:00"
                val intervalMs = locationPrefs.getTrackingIntervalSeconds() * 1000L
                val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                Timber.tag(TAG).d("Window check — Tracking Window: [$startTime–$endTime], Current Time: $currentTime")

                if (locationScheduler.isInTrackingWindow(startTime, endTime)) {
                    Timber.tag(TAG).d("Collection decision: INSIDE_WINDOW — interval=${intervalMs / 1000}s")
                    collectAndSaveLocation()
                    // Sleep only until the window closes or the full interval — whichever comes first.
                    // This prevents an overshoot where delay() carries us past the end time before
                    // the next window check runs.
                    val windowRemainingMs = locationScheduler.millisUntilWindowEnd(endTime)
                    delay(minOf(intervalMs, windowRemainingMs).coerceAtLeast(1_000L))
                } else {
                    val waitMs = locationScheduler.millisUntilWindowStart(startTime)
                    Timber.tag(TAG).d("Collection decision: OUTSIDE_WINDOW — sleeping ${waitMs / 60_000L} min until window opens")
                    delay(waitMs.coerceAtLeast(60_000L))
                    // Refresh config immediately when the next window is about to open so we
                    // never enter a new tracking session with stale start/end times.
                    refreshConfigFromApi()
                }
            }
            Timber.tag(TAG).d("Collection loop exited (scope cancelled)")
        }
    }

    private fun startConfigRefreshLoop() {
        if (configRefreshJob?.isActive == true) return
        configRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(CONFIG_REFRESH_INTERVAL_MS)
                Timber.tag(TAG).d("Periodic config refresh triggered (every 30 min)")
                refreshConfigFromApi()
            }
        }
    }

    private fun startUploadLoop() {
        if (uploadJob?.isActive == true) return
        uploadJob = serviceScope.launch {
            while (isActive) {
                val uploadIntervalMinutes = locationPrefs.getUploadIntervalMinutes()
                val uploadIntervalMs = uploadIntervalMinutes * 60 * 1000L
                Timber.tag(TAG).d("Next upload in $uploadIntervalMinutes min")
                delay(uploadIntervalMs)
                uploadPendingLocations()
            }
        }
    }

    private suspend fun uploadPendingLocations() {
        // Delegates to the shared uploader (batched drain + mark + purge, guarded by the upload
        // lock). The in-service loop just retries on the next tick, so the outcome is only logged.
        val result = locationUploader.uploadPending()
        Timber.tag(TAG).d("Upload tick finished — result=$result")
    }

    private suspend fun refreshConfigFromApi() {
        locationConfigRepository.getLocationConfig()
            .onSuccess { config ->
                Timber.tag(TAG).i(
                    "Config refreshed from API — window: ${config.trackingStartTime}–${config.trackingEndTime}, " +
                    "interval: ${config.trackingIntervalSeconds}s, upload: ${config.uploadIntervalMinutes}min"
                )
            }
            .onFailure { err ->
                Timber.tag(TAG).w("Config refresh failed — using cached values. Reason: ${err.message}")
            }
    }

    private suspend fun collectAndSaveLocation() {
        val userId = authPrefs.cachedUser.firstOrNull()?.id ?: run {
            Timber.tag(TAG).w("No cached user — skipping location collection")
            return
        }

        Timber.tag(TAG).d("Requesting GPS location from FusedLocationClient…")
        val location = withTimeoutOrNull(10_000L) { locationTracker.getCurrentLocation() }

        if (location == null) {
            if (!isGpsEnabled()) {
                Timber.tag(TAG).w("GPS is DISABLED — showing user notification to enable it")
                locationNotificationManager.showGpsDisabledNotification()
            } else {
                Timber.tag(TAG).w("FusedLocationClient returned null (GPS enabled, provider may be warming up)")
            }
            return
        }
        locationNotificationManager.cancelGpsDisabledNotification()

        val clubLat = locationPrefs.getClubLatitude()
        val clubLon = locationPrefs.getClubLongitude()
        if (clubLat == null || clubLon == null) {
            Timber.tag(TAG).w("Club coordinates not in DataStore — cannot calculate distance, skipping save")
            return
        }

        val distance = locationScheduler.calculateDistance(
            location.latitude, location.longitude, clubLat, clubLon
        )

        // The exact wall-clock moment this point was collected. This single value is stored
        // permanently and later transmitted verbatim as the upload payload's "time" field. It
        // must NOT be re-derived at upload time, and must NOT be taken from the GPS fix
        // (location.time): a fused fix can be stale/cached and therefore would not represent the
        // real collection instant.
        val collectionTimeMs = System.currentTimeMillis()
        val collectionTimestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(collectionTimeMs), ZoneId.systemDefault()
        ).format(timestampFormatter)

        locationRepository.saveLocation(
            LocationRecord(
                userId = userId,
                latitude = location.latitude,
                longitude = location.longitude,
                distance = distance,
                timestamp = collectionTimeMs,
                createdAt = collectionTimeMs
            )
        )
        Timber.tag(TAG).i(
            "LOCATION COLLECTED | userId=$userId | collectedAt=$collectionTimestamp " +
                "(stored verbatim — this exact value will be uploaded as time)"
        )
    }

    private fun isGpsEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun stopTracking() {
        Timber.tag(TAG).d("Cancelling tracking, config-refresh and upload jobs")
        trackingJob?.cancel(); trackingJob = null
        configRefreshJob?.cancel(); configRefreshJob = null
        uploadJob?.cancel(); uploadJob = null
        locationNotificationManager.cancelGpsDisabledNotification()
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy — service stopping")
        runCatching { unregisterReceiver(gpsStateReceiver) }
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
