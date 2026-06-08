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
     * The three mandatory permissions that MUST all be granted before Home is reachable:
     *   1. Fine Location       (ACCESS_FINE_LOCATION)
     *   2. Background Location  (ACCESS_BACKGROUND_LOCATION, Android Q+)
     *   3. Notifications        (POST_NOTIFICATIONS, Android 13+)
     *
     * Battery optimization is NOT a hard Home gate. It is still strongly guided inside the
     * permission wizard (a mandatory step there, verified via isIgnoringBatteryOptimizations),
     * but it cannot permanently block Home: on some OEM builds (ColorOS / FuntouchOS) the
     * system API does not reflect the device's own battery toggle, so a user who has actually
     * disabled optimization would otherwise be trapped forever. The wizard offers an explicit
     * "I Have Done This" fallback for that case, and ongoing battery-kill problems are caught
     * by the Home tracking-health monitor ("Tracking Needs Attention" → Fix Tracking).
     */
    val mandatoryRequirementsMet: Boolean
        get() = hasChecked &&
                hasFineLocationPermission &&
                hasBackgroundLocationPermission &&
                hasNotificationPermission

    /**
     * True when a mandatory requirement is missing — triggers the wizard redirect in
     * AppNavGraph. Only fires after hasChecked = true to avoid a false positive on cold
     * start before the first permission check completes.
     */
    val anyRequirementMissing: Boolean
        get() = hasChecked && !mandatoryRequirementsMet
}
