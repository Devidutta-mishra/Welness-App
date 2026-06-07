package com.example.yourswelnes.feature.location.ui

data class LocationUiState(
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimizationExempt: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isInTrackingWindow: Boolean = false,
    val lastSyncTime: Long? = null,
    val hasChecked: Boolean = false
) {
    /**
     * The four mandatory requirements that MUST all be satisfied before Home is reachable:
     *   1. Fine Location          (ACCESS_FINE_LOCATION)
     *   2. Background Location     (ACCESS_BACKGROUND_LOCATION, Android Q+)
     *   3. Notifications           (POST_NOTIFICATIONS, Android 13+)
     *   4. Battery Optimization    (isIgnoringBatteryOptimizations)
     *
     * Battery optimization is included as a hard gate because — unlike OEM auto-start /
     * background-activity settings — it is RELIABLY VERIFIABLE through the standard Android
     * API PowerManager.isIgnoringBatteryOptimizations() on every device (min SDK 29 always
     * ships the ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog). Because we can verify it,
     * we can safely require it. OEM settings, which Android cannot verify, are NEVER gated.
     */
    val mandatoryRequirementsMet: Boolean
        get() = hasChecked &&
                hasFineLocationPermission &&
                hasBackgroundLocationPermission &&
                hasNotificationPermission &&
                isBatteryOptimizationExempt

    /**
     * True when a mandatory requirement is missing — triggers the wizard redirect in
     * AppNavGraph. Only fires after hasChecked = true to avoid a false positive on cold
     * start before the first permission check completes.
     */
    val anyRequirementMissing: Boolean
        get() = hasChecked && !mandatoryRequirementsMet
}
