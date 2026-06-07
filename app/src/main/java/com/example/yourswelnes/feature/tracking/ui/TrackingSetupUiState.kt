package com.example.yourswelnes.feature.tracking.ui

import com.example.yourswelnes.core.tracking.OemManufacturer
import com.example.yourswelnes.core.tracking.OemProfile

data class TrackingSetupUiState(
    // Auto-verified permissions
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimizationExempt: Boolean = false,

    // OEM-specific guidance
    val oemProfile: OemProfile = OemProfile(OemManufacturer.GENERIC, "Android", emptyList()),

    // Tracking health timestamps (epoch ms; null = never recorded)
    val lastLocationCollectionTime: Long? = null,
    val lastWorkerExecutionTime: Long? = null,
    val lastUploadTime: Long? = null,
    val lastTimingSyncTime: Long? = null,

    // Whether the first permission check has completed (prevents false-positive blank flash)
    val hasChecked: Boolean = false
) {
    // The three Android runtime permissions required before Home is reachable.
    // Battery optimization is recommended but cannot hard-block because Android cannot
    // guarantee the user is able to disable it (OEM UIs vary; some devices hide the setting).
    val mandatoryPermissionsGranted: Boolean
        get() = hasChecked &&
                hasFineLocationPermission &&
                hasBackgroundLocationPermission &&
                hasNotificationPermission

    // Full health: all mandatory permissions + battery optimization.
    // Used for health indicators — informational only, not a gate.
    val allPermissionsGranted: Boolean
        get() = mandatoryPermissionsGranted && isBatteryOptimizationExempt

    val hasOemSteps: Boolean
        get() = oemProfile.steps.isNotEmpty()
}
