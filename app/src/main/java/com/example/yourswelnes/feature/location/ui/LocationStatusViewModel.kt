package com.example.yourswelnes.feature.location.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.permission.PermissionChecker
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.location.LocationScheduler
import com.example.yourswelnes.core.location.LocationServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocationStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val locationPrefs: LocationPreferencesDataStore,
    private val locationScheduler: LocationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        observeServiceState()
        observeLastSyncTime()
        refreshPermissions()
    }

    /**
     * Re-evaluates all permission + exemption states. Called:
     *  - On init (cold start)
     *  - On HOME ON_RESUME (user returns from any settings screen or background)
     *
     * Uses [PermissionChecker] as the single source of truth — no duplicated
     * checkSelfPermission() calls. All four checked values feed
     * [LocationUiState.mandatoryRequirementsMet]; when any is missing the wizard redirect
     * fires from AppNavGraph.
     */
    fun refreshPermissions() {
        val hasFine = permissionChecker.hasFineLocation()
        val hasBackground = permissionChecker.hasBackgroundLocation()
        val hasNotification = permissionChecker.hasNotifications()
        val isBatteryExempt = permissionChecker.isBatteryOptimizationExempt()

        Timber.tag("LocationStatusVM").d(
            "Permissions — fine=$hasFine bg=$hasBackground " +
            "notification=$hasNotification batteryExempt=$isBatteryExempt"
        )

        _uiState.update {
            it.copy(
                hasFineLocationPermission = hasFine,
                hasBackgroundLocationPermission = hasBackground,
                hasNotificationPermission = hasNotification,
                isBatteryOptimizationExempt = isBatteryExempt,
                hasChecked = true
            )
        }

        if (hasFine && hasBackground) {
            startServiceIfNeeded()
            refreshTrackingWindow()
        } else {
            Timber.tag("LocationStatusVM").w("Location permissions not fully granted — service will NOT start")
        }
    }

    private fun startServiceIfNeeded() {
        if (LocationServiceState.isRunning.value) {
            Timber.tag("LocationStatusVM").d("Service already running — skipping start")
            return
        }
        Timber.tag("LocationStatusVM").d("Starting LocationForegroundService")
        try {
            ContextCompat.startForegroundService(
                context,
                LocationForegroundService.startIntent(context)
            )
            Timber.tag("LocationStatusVM").d("startForegroundService called successfully")
        } catch (e: Exception) {
            Timber.tag("LocationStatusVM").e(e, "Failed to start LocationForegroundService")
        }
    }

    private fun refreshTrackingWindow() {
        viewModelScope.launch {
            val start = locationPrefs.getTrackingStartTime() ?: return@launch
            val end = locationPrefs.getTrackingEndTime() ?: return@launch
            _uiState.update { it.copy(isInTrackingWindow = locationScheduler.isInTrackingWindow(start, end)) }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            LocationServiceState.isRunning.collect { running ->
                _uiState.update { it.copy(isServiceRunning = running) }
            }
        }
    }

    private fun observeLastSyncTime() {
        viewModelScope.launch {
            locationPrefs.lastSyncTime.collect { time ->
                _uiState.update { it.copy(lastSyncTime = time) }
            }
        }
    }
}
