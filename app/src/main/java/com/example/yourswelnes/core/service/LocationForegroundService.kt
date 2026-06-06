package com.example.yourswelnes.core.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.location.LocationScheduler
import com.example.yourswelnes.core.location.LocationServiceState
import com.example.yourswelnes.core.location.LocationUploader
import com.example.yourswelnes.core.notification.LocationNotificationManager
import com.example.yourswelnes.feature.location.data.LocationConfigRepository
import com.example.yourswelnes.feature.location.data.LocationRepository
import com.example.yourswelnes.feature.location.model.LocationRecord
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
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
import timber.log.Timber

private const val TAG = "LocationService"
private const val CONFIG_REFRESH_INTERVAL_MS = 30 * 60 * 1000L

// How often the window-management loop wakes up to re-read config from DataStore.
// Keeps the interval responsive to backend changes without hammering the store.
private const val WINDOW_POLL_INTERVAL_MS = 30_000L

@AndroidEntryPoint
class LocationForegroundService : android.app.Service() {

    // FusedLocationProviderClient is used directly (rather than LocationTracker) because
    // requestLocationUpdates() is the only API that reliably delivers fixes when the screen
    // is off / phone is locked. getCurrentLocation() (the one-shot API previously used here)
    // returns null under Doze and cold-GPS conditions, which was the root cause of missed
    // collection points during locked-screen windows.
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
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

    // Guard flags — written and read on Dispatchers.IO (trackingJob) and on the main thread
    // (locationCallback fires on Looper.getMainLooper()). @Volatile ensures cross-thread
    // visibility without a full lock, which is sufficient here because writes are rare.
    @Volatile private var locationUpdatesActive = false
    @Volatile private var activeIntervalMs = -1L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Guard: ignore stale callbacks that fire after removeLocationUpdates() returns.
            if (!locationUpdatesActive) return
            result.lastLocation?.let { location ->
                Timber.tag(TAG).d(
                    "LOCATION RECEIVED | lat=${location.latitude} lon=${location.longitude} " +
                    "accuracy=${location.accuracy}m"
                )
                serviceScope.launch { saveLocationFromCallback(location) }
            }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                // isLocationAvailable = false means the FLP cannot produce a fix right now.
                // This is NOT the same as GPS being disabled — it also fires when GPS is on
                // but warming up, indoors, or has poor signal. Only show the user notification
                // if the GPS provider is actually turned off in system settings.
                if (!isGpsEnabled()) {
                    Timber.tag(TAG).w("GPS DISABLED | GPS provider is off — showing notification")
                    serviceScope.launch {
                        val start = locationPrefs.getTrackingStartTime() ?: "06:00"
                        val end = locationPrefs.getTrackingEndTime() ?: "12:00"
                        if (locationScheduler.isInTrackingWindow(start, end)) {
                            locationNotificationManager.showGpsDisabledNotification()
                        }
                    }
                } else {
                    Timber.tag(TAG).d(
                        "GPS DISABLED | FLP cannot get a fix right now (warming up / indoors / poor signal) " +
                        "— GPS provider is ON, waiting silently for next fix"
                    )
                }
            } else {
                Timber.tag(TAG).d("GPS available — cancelling GPS-disabled notification if shown")
                locationNotificationManager.cancelGpsDisabledNotification()
            }
        }
    }

    private val gpsStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != LocationManager.PROVIDERS_CHANGED_ACTION) return
            serviceScope.launch {
                val gpsOn = isGpsEnabled()
                val start = locationPrefs.getTrackingStartTime() ?: "06:00"
                val end = locationPrefs.getTrackingEndTime() ?: "12:00"
                val inWindow = locationScheduler.isInTrackingWindow(start, end)
                if (!gpsOn && inWindow) {
                    Timber.tag(TAG).w("GPS toggled OFF during tracking window — showing notification")
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
            Timber.tag(TAG).d("Tracking already active — ignoring duplicate start")
            return
        }
        startConfigRefreshLoop()
        startUploadLoop()

        trackingJob = serviceScope.launch {
            val token = authPrefs.getToken()
            if (token.isNullOrBlank()) {
                Timber.tag(TAG).w("No auth token — stopping service")
                stopSelf()
                return@launch
            }
            Timber.tag(TAG).i("WORKER STARTED | Auth OK — location collection pipeline starting")

            // Warn loudly if battery optimization is still enabled. On many OEMs (Xiaomi,
            // Samsung, Realme, OPPO) this causes the service to be killed within seconds of
            // the screen locking, which stops all GPS collection.
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Timber.tag(TAG).w(
                    "BATTERY OPTIMIZATION ENABLED | This device will likely kill the service " +
                    "when the screen locks. Ask the user to disable battery optimization for this app."
                )
            } else {
                Timber.tag(TAG).i("Battery optimization exempt — locked-screen collection enabled")
            }

            // Prime local cache from API before entering the collection loop so the window
            // is always current on the first iteration, even after a reboot.
            refreshConfigFromApi()

            while (isActive) {
                // Safety net: stop if token is cleared mid-session (logout).
                if (authPrefs.getToken().isNullOrBlank()) {
                    Timber.tag(TAG).w("Auth token cleared mid-session — stopping service")
                    stopContinuousUpdates()
                    stopSelf()
                    return@launch
                }

                val startTime = locationPrefs.getTrackingStartTime() ?: "06:00"
                val endTime = locationPrefs.getTrackingEndTime() ?: "12:00"
                val intervalMs = locationPrefs.getTrackingIntervalSeconds() * 1000L
                val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                Timber.tag(TAG).d(
                    "TRACKING WINDOW LOADED | window=$startTime–$endTime | " +
                    "interval=${intervalMs / 1000}s | now=$currentTime"
                )

                if (locationScheduler.isInTrackingWindow(startTime, endTime)) {
                    Timber.tag(TAG).d("INSIDE WINDOW | Ensuring continuous location updates are active")
                    startContinuousUpdates(intervalMs)

                    // Sleep until the window closes or until we need to re-read config —
                    // whichever comes first. This lets a backend interval change take effect
                    // within 30 s without carrying past the window end time.
                    val windowRemainingMs = locationScheduler.millisUntilWindowEnd(endTime)
                    delay(minOf(WINDOW_POLL_INTERVAL_MS, windowRemainingMs).coerceAtLeast(5_000L))
                } else {
                    if (locationUpdatesActive) {
                        Timber.tag(TAG).d("OUTSIDE WINDOW | Stopping continuous location updates")
                        stopContinuousUpdates()
                    }
                    val waitMs = locationScheduler.millisUntilWindowStart(startTime)
                    Timber.tag(TAG).d(
                        "OUTSIDE WINDOW | window=$startTime–$endTime | now=$currentTime | " +
                        "sleeping ${waitMs / 60_000L} min until next window"
                    )
                    delay(waitMs.coerceAtLeast(60_000L))
                    // Refresh config right before the window opens so we never enter a new
                    // tracking session with stale start/end/interval values.
                    refreshConfigFromApi()
                }
            }
            stopContinuousUpdates()
            Timber.tag(TAG).d("Collection loop exited (scope cancelled)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startContinuousUpdates(intervalMs: Long) {
        // Already active at the same interval — nothing to do.
        if (locationUpdatesActive && activeIntervalMs == intervalMs) return

        if (!hasLocationPermission()) {
            Timber.tag(TAG).w("PERMISSION DENIED | Cannot start location updates — collection paused")
            return
        }

        // If already active but interval changed, stop first then re-register.
        if (locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesActive = false
            Timber.tag(TAG).d("Restarting location updates with new interval ${intervalMs / 1000}s")
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            // Do not block for a high-accuracy fix — accept whatever the provider has.
            // When GPS warms up, accuracy improves automatically on subsequent fixes.
            .setWaitForAccurateLocation(false)
            .build()

        try {
            // Deliver callbacks on the main looper. The callback immediately re-dispatches work
            // to serviceScope (Dispatchers.IO) so there is no blocking on the main thread.
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesActive = true
            activeIntervalMs = intervalMs
            Timber.tag(TAG).i(
                "WORKER REGISTERED | Continuous location updates active — " +
                "interval=${intervalMs / 1000}s | LOCK SCREEN COLLECTION: enabled via FGS exemption"
            )
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "PERMISSION DENIED | requestLocationUpdates failed")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to register location updates — will retry on next window check")
        }
    }

    private fun stopContinuousUpdates() {
        if (!locationUpdatesActive) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationUpdatesActive = false
        activeIntervalMs = -1L
        Timber.tag(TAG).d("Continuous location updates stopped")
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
                Timber.tag(TAG).d("UPLOAD WORKER STARTED | Next upload in $uploadIntervalMinutes min")
                delay(uploadIntervalMs)
                val result = locationUploader.uploadPending()
                when (result) {
                    LocationUploader.Result.SUCCESS ->
                        Timber.tag(TAG).i("UPLOAD SUCCESS | Pending locations sent to server")
                    LocationUploader.Result.NOTHING_TO_DO ->
                        Timber.tag(TAG).d("Upload tick — nothing pending")
                    LocationUploader.Result.FAILED ->
                        Timber.tag(TAG).w("NO INTERNET | Upload failed — will retry next tick")
                }
            }
        }
    }

    private suspend fun refreshConfigFromApi() {
        locationConfigRepository.getLocationConfig()
            .onSuccess { config ->
                Timber.tag(TAG).i(
                    "TRACKING WINDOW UPDATED | API success — " +
                    "window=${config.trackingStartTime}–${config.trackingEndTime} " +
                    "interval=${config.trackingIntervalSeconds}s upload=${config.uploadIntervalMinutes}min"
                )
            }
            .onFailure { err ->
                Timber.tag(TAG).w(
                    "Config refresh failed — tracking continues on cached schedule. reason=${err.message}"
                )
            }
    }

    private suspend fun saveLocationFromCallback(location: Location) {
        val userId = authPrefs.cachedUser.firstOrNull()?.id ?: run {
            Timber.tag(TAG).w("No cached user — skipping location save")
            return
        }

        val clubName = locationPrefs.getClubName()
        if (clubName.isNullOrBlank()) {
            Timber.tag(TAG).w("No club assigned to user $userId — skipping location save")
            return
        }

        val clubLat = locationPrefs.getClubLatitude()
        val clubLon = locationPrefs.getClubLongitude()
        if (clubLat == null || clubLon == null) {
            Timber.tag(TAG).w("Club coordinates missing for user $userId — skipping location save")
            return
        }

        val distance = locationScheduler.calculateDistance(
            location.latitude, location.longitude, clubLat, clubLon
        )

        // Wall-clock moment of collection — stored verbatim and later sent as "time" in the
        // upload payload. Must NOT be derived from location.time (can be stale/cached).
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
            "LOCATION STORED | userId=$userId | lat=${location.latitude} lon=${location.longitude} " +
            "| distance=${distance.toInt()}m | collectedAt=$collectionTimestamp"
        )
    }

    private fun isGpsEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun stopTracking() {
        Timber.tag(TAG).d("Stopping all tracking — cancelling jobs and location updates")
        stopContinuousUpdates()
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
