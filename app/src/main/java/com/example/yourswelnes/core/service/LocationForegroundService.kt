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
import com.example.yourswelnes.core.location.AlarmHandoffWakeLock
import com.example.yourswelnes.core.location.LocationScheduler
import com.example.yourswelnes.core.location.LocationServiceState
import com.example.yourswelnes.core.location.LocationUploader
import com.example.yourswelnes.core.location.TrackingAlarmScheduler
import com.example.yourswelnes.core.worker.LocationUploadWorker
import androidx.work.WorkManager
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

// Sustained partial wake lock held only while collecting inside the window. A foreground service
// does NOT by itself keep the CPU awake — during Deep Doze the OS suspends the CPU and withholds
// location between maintenance windows even for an FGS. A battery-optimization-exempt app (which
// onboarding enforces) IS allowed to hold a partial wake lock during Doze, and that is what keeps
// the CPU alive to deliver GPS fixes while the phone is locked/idle overnight. The lock is
// refreshed every WINDOW_POLL_INTERVAL_MS with a timeout safety net so a killed service can never
// hold the CPU awake indefinitely; it is released explicitly the moment we leave the window.
private const val WAKELOCK_TAG = "yourswelnes:location_collection_wakelock"
private const val WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L

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
    @Inject lateinit var trackingAlarmScheduler: TrackingAlarmScheduler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var configRefreshJob: Job? = null
    private var uploadJob: Job? = null

    // Held only while actively collecting inside the window (see WAKELOCK_TAG comment above).
    private var collectionWakeLock: PowerManager.WakeLock? = null

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Guard flags — written and read on Dispatchers.IO (trackingJob) and on the main thread
    // (locationCallback fires on Looper.getMainLooper()). @Volatile ensures cross-thread
    // visibility without a full lock, which is sufficient here because writes are rare.
    @Volatile private var locationUpdatesActive = false
    @Volatile private var activeIntervalMs = -1L

    // True once startForeground() has succeeded for this service instance.
    @Volatile private var foregroundPromoted = false

    // True once the FIRST collected point of this service run has been written to Room. The
    // alarm→service hand-off wake lock is released exactly at that transition: offline overnight
    // there is no A-GPS, so the hardware GPS needs a 10–45 s cold start, and the CPU must stay
    // held until a real coordinate has been PERSISTED — not merely until the service started.
    @Volatile private var firstFixPersisted = false

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
        if (!promoteToForeground()) return
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gpsStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(gpsStateReceiver, filter)
        }
        Timber.tag(TAG).d("GPS state receiver registered")
    }

    /**
     * Promote to foreground IMMEDIATELY (API 31+ allows ~5 s after startForegroundService(), but
     * under Doze the process can be frozen long before that, so the promotion happens first thing
     * in onCreate — i.e. before onStartCommand even runs). Returns false when promotion failed and
     * the service is already stopping, so callers must not start any work.
     *
     * API 34 hard rule verified HERE, inside the service execution block: promoting an FGS of type
     * "location" without a granted FINE/COARSE runtime permission throws SecurityException and the
     * OS silently kills the service before any location code runs. Checking first lets us stop
     * cleanly and leave a diagnosable log instead of relying on the exception path.
     */
    private fun promoteToForeground(): Boolean {
        if (foregroundPromoted) return true

        if (!hasLocationPermission()) {
            Timber.tag(TAG).e(
                "PERMISSION DENIED | FINE/COARSE location not granted — a location-type FGS cannot " +
                "be promoted on API 34+. Stopping self; the permission wizard must re-grant."
            )
            stopSelf()
            return false
        }
        if (!hasBackgroundLocationPermission()) {
            // Not fatal for a foreground (app-open) session, but locked-screen collection will be
            // withheld by the OS. Logged loudly so a silent overnight gap is diagnosable.
            Timber.tag(TAG).w(
                "BACKGROUND LOCATION MISSING | 'Allow all the time' not granted — fixes will stop " +
                "when the app leaves the foreground. Onboarding wizard enforces this grant."
            )
        }

        return try {
            ServiceCompat.startForeground(
                this,
                LocationNotificationManager.NOTIFICATION_ID_SERVICE,
                locationNotificationManager.buildServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            foregroundPromoted = true
            LocationServiceState.setRunning(true)
            Timber.tag(TAG).d("Foreground service promoted successfully")
            true
        } catch (e: SecurityException) {
            // API 34+: location permission revoked between our check and the promotion.
            Timber.tag(TAG).e(e, "Cannot start FGS — location permission missing")
            stopSelf()
            false
        } catch (e: IllegalStateException) {
            // API 31+: ForegroundServiceStartNotAllowedException (background start without an
            // active exemption) and the API 34 typed-FGS exceptions (Missing/Invalid
            // ForegroundServiceTypeException) are all IllegalStateException subclasses. Without
            // this branch they would be UNCAUGHT and crash the background process — exactly during
            // the locked/Doze window this feature targets. Stop cleanly instead; the watchdog and
            // next exact alarm will retry once the exemption is available.
            Timber.tag(TAG).e(e, "Cannot start FGS — background-start not allowed / invalid service type")
            stopSelf()
            false
        } catch (e: Exception) {
            // Last-resort guard: any other failure during foreground promotion must not crash a
            // background-started service. Stop and let the recovery layers (watchdog / exact alarm)
            // bring collection back when conditions allow.
            Timber.tag(TAG).e(e, "Unexpected failure promoting foreground service — stopping cleanly")
            stopSelf()
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand action=${intent?.action ?: "null (Android restart)"}")
        // Re-assert foreground state (no-op when already promoted). Covers the START_STICKY
        // null-intent restart and any path where onCreate's promotion was skipped.
        if (!promoteToForeground()) return START_NOT_STICKY
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
            Timber.tag(TAG).i("USER SESSION LOADED | Auth token present — location collection pipeline starting")

            // Runtime-permission re-verification INSIDE the execution block (API 34 requirement):
            // a revocation while the service was scheduled would otherwise surface as a silent
            // OS kill. Without FINE/COARSE no fix can ever be delivered, so idling here would only
            // burn the wake lock — stop immediately; the alarm/watchdog retry after re-grant.
            if (!hasLocationPermission()) {
                Timber.tag(TAG).e("PERMISSION DENIED | Location permission revoked — stopping service")
                stopSelf()
                return@launch
            }

            // Warn loudly if battery optimization is still enabled. On many OEMs (Xiaomi,
            // Samsung, Realme, OPPO) this causes the service to be killed within seconds of
            // the screen locking, which stops all GPS collection.
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Timber.tag(TAG).w(
                    "BATTERY OPTIMIZATION ENABLED | This device will likely kill the service " +
                    "when the screen locks. Battery optimization exemption required for reliable tracking."
                )
            } else {
                Timber.tag(TAG).i("Battery optimization exempt — locked-screen collection enabled")
            }

            // Refresh config in the background so the collection loop starts immediately with
            // cached values. A 30-second connect timeout would otherwise delay the first GPS
            // request by up to 30 s when internet is unavailable.
            serviceScope.launch { refreshConfigFromApi() }

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
                Timber.tag(TAG).i(
                    "TIMING CACHE LOADED | Cached window=$startTime–$endTime | " +
                    "interval=${intervalMs / 1000}s | now=$currentTime"
                )

                if (locationScheduler.isInTrackingWindow(startTime, endTime)) {
                    Timber.tag(TAG).i("TRACKING WINDOW ACTIVE | Inside window $startTime–$endTime — ensuring GPS updates")
                    // Pin the CPU BEFORE registering for fixes: offline overnight there is no A-GPS,
                    // so the hardware GPS cold start takes 10–45 s — if the CPU is allowed to sleep
                    // in that gap the chip never finishes warming up and zero fixes are delivered.
                    // Refreshed every loop tick (<= WINDOW_POLL_INTERVAL_MS) so a long window stays
                    // covered; the WAKELOCK_TIMEOUT_MS safety net guarantees it is dropped if the
                    // service is ever killed mid-window.
                    acquireOrRefreshWakeLock()
                    startContinuousUpdates(intervalMs)

                    // Sleep until the window closes or until we need to re-read config —
                    // whichever comes first. This lets a backend interval change take effect
                    // within 30 s without carrying past the window end time.
                    val windowRemainingMs = locationScheduler.millisUntilWindowEnd(endTime)
                    delay(minOf(WINDOW_POLL_INTERVAL_MS, windowRemainingMs).coerceAtLeast(5_000L))
                } else {
                    // Graceful self-termination: the cached window has closed (or we were started
                    // outside it). Clear GPS updates, drop every wake lock, arm tomorrow's exact
                    // alarm from the CACHED config (offline-safe — DataStore only), enqueue an
                    // upload drain for whenever network returns, and stop. The exact alarm — not a
                    // sleeping service — owns reopening the window; an idle FGS would only cost
                    // battery and invite OEM kills that desync LocationServiceState.
                    Timber.tag(TAG).i(
                        "WINDOW CLOSED | window=$startTime–$endTime | now=$currentTime — " +
                        "graceful shutdown; exact alarm owns the next window start"
                    )
                    stopContinuousUpdates()
                    releaseWakeLock()
                    AlarmHandoffWakeLock.release()
                    // Belt-and-braces re-arm: TrackingAlarmReceiver already re-armed after firing,
                    // but a manual / watchdog / app-open start has no alarm guaranteed yet.
                    runCatching { trackingAlarmScheduler.scheduleNextWindowStart() }
                        .onFailure { Timber.tag(TAG).e(it, "Failed to arm next window before shutdown") }
                    // Deferred upload: points collected this window that have not been sent yet are
                    // drained by this one-time worker as soon as connectivity exists — uploads never
                    // require the collection service to be alive.
                    runCatching {
                        LocationUploadWorker.scheduleOneTime(WorkManager.getInstance(applicationContext))
                    }
                    stopSelf()
                    return@launch
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

        // Configured for aggressive OFFLINE acquisition: PRIORITY_HIGH_ACCURACY forces the hardware
        // GPS chip (network/cell providers have nothing to offer with no internet), a definitive
        // interval drives delivery by TIME so a stationary phone on a desk still produces points,
        // and batching is explicitly disabled so fixes are not held back until a flush that Doze
        // may never grant.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            // Time-driven, not movement-driven: any displacement filter (> 0 m) suppresses every
            // fix from a phone lying still — which overnight is the NORMAL case.
            .setMinUpdateDistanceMeters(0f)
            // No batching: deliver each fix as it is computed instead of queueing for a deferred
            // flush the device may sleep through.
            .setMaxUpdateDelayMillis(0L)
            // Do not block for a high-accuracy fix — accept whatever the provider has.
            // When GPS warms up, accuracy improves automatically on subsequent fixes.
            .setWaitForAccurateLocation(false)
            .build()

        try {
            // Deliver callbacks on the main looper. The callback immediately re-dispatches work
            // to serviceScope (Dispatchers.IO) so there is no blocking on the main thread.
            Timber.tag(TAG).i(
                "LOCATION REQUEST STARTED | Registering continuous GPS updates — " +
                "interval=${intervalMs / 1000}s"
            )
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesActive = true
            activeIntervalMs = intervalMs
            Timber.tag(TAG).i(
                "LOCATION REQUEST STARTED | GPS updates active — " +
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

    /**
     * Acquire (or refresh the timeout on) the partial wake lock that keeps the CPU awake during
     * collection. Idempotent and reference-count-disabled: re-calling it each loop tick simply
     * resets WAKELOCK_TIMEOUT_MS so a multi-hour window stays covered without stacking locks.
     */
    private fun acquireOrRefreshWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = collectionWakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .also {
                it.setReferenceCounted(false)
                collectionWakeLock = it
            }
        try {
            wl.acquire(WAKELOCK_TIMEOUT_MS)
            Timber.tag(TAG).d("WAKELOCK HELD | CPU kept awake for in-window collection (timeout refreshed)")
            // NOTE: the alarm→service hand-off lock is deliberately NOT released here. It stays
            // held until the FIRST fix is persisted to Room (see saveLocationFromCallback) or its
            // 60 s hard timeout — releasing it merely because our own lock was acquired proved
            // fragile: if this acquire ever throws, the CPU is dropped mid-GPS-cold-start and the
            // offline window collects nothing.
        } catch (e: Exception) {
            // WAKE_LOCK permission missing or OEM restriction — collection continues best-effort.
            Timber.tag(TAG).e(e, "Could not acquire collection wake lock — Doze may throttle fixes")
        }
    }

    private fun releaseWakeLock() {
        collectionWakeLock?.let { if (it.isHeld) it.release() }
        Timber.tag(TAG).d("WAKELOCK RELEASED | CPU no longer held by collection service")
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
                        Timber.tag(TAG).w(
                            "NO INTERNET — UPLOAD DEFERRED | " +
                            "Pending locations remain in Room; will retry next tick"
                        )
                }
            }
        }
    }

    private suspend fun refreshConfigFromApi() {
        locationConfigRepository.getLocationConfig()
            .onSuccess { config ->
                Timber.tag(TAG).i(
                    "TRACKING WINDOW UPDATED | API success — " +
                    "window=${config.trackingStartTime}–${config.trackingEndTime}"
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

        val persisted = locationRepository.saveLocation(
            LocationRecord(
                userId = userId,
                latitude = location.latitude,
                longitude = location.longitude,
                distance = distance,
                timestamp = collectionTimeMs,
                createdAt = collectionTimeMs
            )
        )
        if (!persisted) {
            // Keep the hand-off lock held (its 60 s hard timeout is the ceiling) so the CPU stays
            // up for the next fix attempt instead of sleeping on a failed first write.
            Timber.tag(TAG).w("DB WRITE FAILED | Location not persisted — hand-off lock kept for retry")
            return
        }
        locationPrefs.saveLastLocationCollectionTime(collectionTimeMs)
        Timber.tag(TAG).i(
            "LOCATION SAVED LOCALLY | userId=$userId | lat=${location.latitude} lon=${location.longitude} " +
            "| distance=${distance.toInt()}m | collectedAt=$collectionTimestamp"
        )
        if (!firstFixPersisted) {
            firstFixPersisted = true
            // Hand-off contract fulfilled: the first valid coordinate of this run is durably in
            // Room, so the alarm→service hand-off lock can finally be released. From here the
            // sustained collection lock (refreshed every loop tick) keeps the CPU alive for the
            // rest of the window. No-op for non-alarm starts (the lock was never acquired).
            AlarmHandoffWakeLock.release()
            Timber.tag(TAG).i(
                "FIRST FIX PERSISTED | GPS warm-up complete — alarm hand-off wake lock released"
            )
        }
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

    private fun hasBackgroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun stopTracking() {
        Timber.tag(TAG).d("Stopping all tracking — cancelling jobs and location updates")
        stopContinuousUpdates()
        releaseWakeLock()
        trackingJob?.cancel(); trackingJob = null
        configRefreshJob?.cancel(); configRefreshJob = null
        uploadJob?.cancel(); uploadJob = null
        locationNotificationManager.cancelGpsDisabledNotification()
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy — service stopping")
        // Covers the paths where the service was started by the alarm but stopped before reaching the
        // collection loop (FGS promotion failed, missing token, manual stop) — release the hand-off
        // lock here instead of waiting out its timeout.
        AlarmHandoffWakeLock.release()
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
