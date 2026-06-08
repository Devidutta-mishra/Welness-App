package com.example.yourswelnes.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.work.WorkManager
import com.example.yourswelnes.core.location.TrackingAlarmScheduler
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.worker.AppInstallationSyncWorker
import com.example.yourswelnes.core.worker.LocationUploadWorker
import com.example.yourswelnes.core.worker.LocationWatchdogWorker
import com.example.yourswelnes.core.worker.NotificationSyncWorker
import com.example.yourswelnes.core.worker.ScheduleSyncWorker
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.auth.data.AuthRepository
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import com.example.yourswelnes.feature.dashboard.data.DashboardRepository
import com.example.yourswelnes.feature.home.data.ClubRepository
import com.example.yourswelnes.feature.home.data.GroupDetailsRepository
import com.example.yourswelnes.feature.location.data.LocationConfigRepository
import com.example.yourswelnes.feature.notifications.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val clubRepository: ClubRepository,
    private val notificationRepository: NotificationRepository,
    private val dashboardRepository: DashboardRepository,
    private val appLockManager: AppLockManager,
    private val locationConfigRepository: LocationConfigRepository,
    private val groupDetailsRepository: GroupDetailsRepository,
    private val locationPrefs: LocationPreferencesDataStore,
    private val trackingAlarmScheduler: TrackingAlarmScheduler
) : ViewModel() {

    val isLockRequired: StateFlow<Boolean> = appLockManager.isLockRequired

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = navigationEvents.receiveAsFlow()

    init {
        observeUser()
        loadClubDetails()
        fetchLocationConfig()
        observeNotifications()
        rescheduleWorkers()
        checkTrackingHealth()
    }

    private fun rescheduleWorkers() {
        val workManager = WorkManager.getInstance(context)
        LocationUploadWorker.schedule(workManager)
        ScheduleSyncWorker.schedule(workManager)
        LocationWatchdogWorker.schedule(workManager)
        NotificationSyncWorker.cancel(workManager)  // FCM handles delivery — cancel any stale polling work
        AppInstallationSyncWorker.schedulePeriodic(workManager)
        // No need to scheduleOneTime for AppInstallation here as it's intended for app start

        // Arm the Doze-proof exact alarm now that config is (or is about to be) cached. The watchdog
        // above is a 15-min periodic worker that Deep Doze defers overnight; the exact alarm fires
        // precisely at the window start regardless of Doze. Reads cached config only — offline-safe.
        viewModelScope.launch { trackingAlarmScheduler.scheduleNextWindowStart() }
    }

    private fun observeUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            userName = user.name,
                            profileImageUrl = user.profileImage ?: user.imageUrl
                        )
                    }
                }
            }
        }
    }

    fun recheckTrackingHealth() { checkTrackingHealth() }

    /**
     * Background-tracking health monitor.
     *
     * Battery optimization and the four runtime permissions are VERIFIABLE, so they are
     * enforced as a hard gate by the permission wizard (AppNavGraph redirects to it whenever
     * LocationUiState.anyRequirementMissing is true). They are intentionally NOT checked here.
     *
     * This monitor instead detects the failures Android CANNOT surface directly — the symptoms
     * of an OEM aggressively killing background work despite every permission being granted:
     *
     *   • Collection stale: locations have been collected before, but not within the last 24h.
     *     Over a full day at least one tracking window must elapse, so a gap this long means
     *     collection is silently failing — almost always an OEM background kill or a missing
     *     auto-start / background-activity allowance that no API can verify.
     *
     *   • Worker stale: the watchdog runs every 15 min with NO network constraint and stamps
     *     [LocationPreferencesDataStore.saveLastWorkerExecutionTime] on every run. If it has
     *     not executed in [WORKER_STALE_THRESHOLD_MS], WorkManager itself is being killed —
     *     the strongest possible signal that background execution is blocked.
     *
     * Both conditions require a non-null history value, so a fresh login (which has never
     * collected or run a worker) never produces a false alarm. When unhealthy, Home shows the
     * "Tracking Needs Attention" card whose [Fix Tracking] action re-opens the OEM setup.
     */
    private fun checkTrackingHealth() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lastCollection = locationPrefs.getLastLocationCollectionTime()
            val lastWorker = locationPrefs.getLastWorkerExecutionTime()

            val collectionStale = lastCollection != null &&
                (now - lastCollection) > COLLECTION_STALE_THRESHOLD_MS
            val workerStale = lastWorker != null &&
                (now - lastWorker) > WORKER_STALE_THRESHOLD_MS

            val needsAttention = collectionStale || workerStale

            Timber.tag("HomeViewModel").d(
                "TRACKING HEALTH ${if (needsAttention) "UNHEALTHY" else "HEALTHY"} | " +
                "collectionStale=$collectionStale workerStale=$workerStale " +
                "lastCollection=$lastCollection lastWorker=$lastWorker"
            )
            _uiState.update { it.copy(trackingHealthNeedsAttention = needsAttention) }
        }
    }

    private fun fetchLocationConfig() {
        viewModelScope.launch {
            locationConfigRepository.getLocationConfig()
                .onSuccess { config ->
                    Timber.tag("HomeViewModel").d(
                        "Location config fetched — window: ${config.trackingStartTime}–${config.trackingEndTime}, " +
                        "interval: ${config.trackingIntervalSeconds}s, upload: ${config.uploadIntervalMinutes}min"
                    )
                }
                .onFailure {
                    Timber.tag("HomeViewModel").w("Location config fetch failed (cached will be used): ${it.message}")
                }
        }
    }

    private fun loadClubDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Do NOT clear club info here. Logout already calls clearClubInfo() so there is
            // no cross-user contamination. Pre-clearing caused a critical bug: if the network
            // request failed (offline, flaky connection) club info stayed empty, and the
            // running service silently skipped every location collection for the entire session.
            // ClubRepositoryImpl handles clearing when the server rejects the club (no club
            // assigned) and saving on success; on network failure the cached info is preserved.
            clubRepository.getClubDetails()
                .onSuccess { details ->
                    // Covers both online (fresh) and offline (cached) — the repository returns
                    // the cached club on network failure, so the real club name is never lost.
                    _uiState.update { it.copy(isLoading = false, clubName = details.clubName) }
                }
                .onFailure { error ->
                    // Reached only when the server explicitly reports no club AND there is no
                    // cached club (e.g. brand-new user offline). Do NOT fabricate a placeholder
                    // club name — leave it blank so the UI does not show a club that isn't real.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clubName = "",
                            error = error.message
                        )
                    }
                }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationRepository.notifications.collect { notifications ->
                val hasUnread = notificationRepository.getUnreadCount(notifications) > 0
                _uiState.update { it.copy(hasUnreadNotifications = hasUnread) }
            }
        }
    }

    fun openDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDashboardLoading = true, dashboardError = null) }
            dashboardRepository.getDashboardUrl()
                .onSuccess { url ->
                    _uiState.update { it.copy(isDashboardLoading = false) }
                    navigationEvents.send(HomeNavigationEvent.OpenDashboard(url))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDashboardLoading = false,
                            dashboardError = error.message ?: "Unable to open dashboard. Please try again."
                        )
                    }
                }
        }
    }

    fun onLogoutClicked() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun onLogoutConfirmed() {
        _uiState.update { it.copy(showLogoutDialog = false) }
        viewModelScope.launch {
            Timber.tag("HomeViewModel").d("Logout — beginning full session cleanup")
            val workManager = WorkManager.getInstance(context)

            // 1. Stop location service so it doesn't collect/upload under the leaving user.
            context.stopService(LocationForegroundService.stopIntent(context))

            // 2. Cancel all user-bound workers. They will be rescheduled when User B logs in
            //    and the app starts fresh (YourswelnesApplication.onCreate re-enqueues them).
            LocationUploadWorker.cancel(workManager)
            ScheduleSyncWorker.cancel(workManager)
            LocationWatchdogWorker.cancel(workManager)
            NotificationSyncWorker.cancel(workManager)
            AppInstallationSyncWorker.cancel(workManager)

            // Cancel the exact tracking-window alarm so the leaving user's window can't wake the
            // service for the next user. Application.onCreate re-arms it when User B logs in.
            trackingAlarmScheduler.cancel()

            // 3. Clear all in-memory and persisted caches scoped to the leaving user.
            groupDetailsRepository.clearCache()
            notificationRepository.clearCache()
            locationPrefs.clearClubInfo()  // prevents stale club info being used by next user's service

            // 4. Wipe the auth session — this revokes the bearer token for all future requests.
            authRepository.logout()

            Timber.tag("HomeViewModel").d("Session cleanup complete — navigating to login")
            navigationEvents.send(HomeNavigationEvent.NavigateToLogin)
        }
    }

    fun onLogoutDismissed() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }
}

sealed interface HomeNavigationEvent {
    data object NavigateToLogin : HomeNavigationEvent
    data class OpenDashboard(val url: String) : HomeNavigationEvent
}

private const val COLLECTION_STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L  // 24 hours (per spec)
private const val WORKER_STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L       // 3 hours; watchdog runs every 15 min
