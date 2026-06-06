package com.example.yourswelnes.feature.location.ui

data class LocationUiState(
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    // When false, many OEMs (Xiaomi, Samsung, Realme) kill the foreground service the moment
    // the screen locks, stopping all location collection. This must be true for reliable
    // locked-screen tracking.
    val isBatteryOptimizationExempt: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isInTrackingWindow: Boolean = false,
    val lastSyncTime: Long? = null,
    // Guards against default false-values triggering navigation before the first real check runs.
    val hasChecked: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() = hasChecked &&
                hasFineLocationPermission &&
                hasBackgroundLocationPermission &&
                hasNotificationPermission &&
                isBatteryOptimizationExempt

    val anyPermissionMissing: Boolean
        get() = hasChecked && !allPermissionsGranted
}
