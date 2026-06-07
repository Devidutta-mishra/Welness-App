package com.example.yourswelnes.feature.tracking.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.location.LocationServiceState
import com.example.yourswelnes.core.permission.BatteryOptimizationManager
import com.example.yourswelnes.core.permission.PermissionChecker
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.tracking.detectOemProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class TrackingSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val batteryOptimizationManager: BatteryOptimizationManager,
    private val locationPrefs: LocationPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingSetupUiState())
    val uiState: StateFlow<TrackingSetupUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val hasFine = permissionChecker.hasFineLocation()
        val hasBackground = permissionChecker.hasBackgroundLocation()
        val hasNotification = permissionChecker.hasNotifications()
        val isBatteryExempt = batteryOptimizationManager.checkNow()
        val oemProfile = detectOemProfile(context.packageName)

        Timber.tag("TrackingSetup").i(
            "TRACKING SETUP LOADED | fine=$hasFine bg=$hasBackground " +
            "notif=$hasNotification battery=$isBatteryExempt oem=${oemProfile.displayName}"
        )
        Timber.tag("TrackingSetup").i("LOCATION PERMISSION STATUS | fine=$hasFine")
        Timber.tag("TrackingSetup").i("BACKGROUND LOCATION STATUS | granted=$hasBackground")
        Timber.tag("TrackingSetup").i("NOTIFICATION STATUS | granted=$hasNotification")

        viewModelScope.launch {
            val lastCollection = locationPrefs.getLastLocationCollectionTime()
            val lastWorker = locationPrefs.getLastWorkerExecutionTime()
            val lastUpload = locationPrefs.getLastSyncTime()
            val lastTimingSync = locationPrefs.getLastScheduleSyncTime()

            _uiState.update {
                it.copy(
                    hasFineLocationPermission       = hasFine,
                    hasBackgroundLocationPermission = hasBackground,
                    hasNotificationPermission       = hasNotification,
                    isBatteryOptimizationExempt     = isBatteryExempt,
                    oemProfile                      = oemProfile,
                    lastLocationCollectionTime      = lastCollection,
                    lastWorkerExecutionTime         = lastWorker,
                    lastUploadTime                  = lastUpload,
                    lastTimingSyncTime              = lastTimingSync,
                    hasChecked                      = true
                )
            }

            if (hasFine && hasBackground && !LocationServiceState.isRunning.value) {
                Timber.tag("TrackingSetup").d("Permissions OK — attempting service start from setup screen")
                try {
                    ContextCompat.startForegroundService(
                        context,
                        LocationForegroundService.startIntent(context)
                    )
                } catch (e: Exception) {
                    Timber.tag("TrackingSetup").e(e, "Service start failed from setup screen")
                }
            }
        }
    }
}
