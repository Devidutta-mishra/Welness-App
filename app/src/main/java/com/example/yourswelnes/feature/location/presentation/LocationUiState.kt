package com.example.yourswelnes.feature.location.presentation

data class LocationUiState(
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isInTrackingWindow: Boolean = false,
    val lastSyncTime: Long? = null
) {
    val allPermissionsGranted: Boolean
        get() = hasFineLocationPermission && hasBackgroundLocationPermission && hasNotificationPermission
}
