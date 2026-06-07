package com.example.yourswelnes.feature.home.ui

data class HomeUiState(
    val userName: String = "Your Name",
    val profileImageUrl: String? = null,
    val showLogoutDialog: Boolean = false,
    val clubName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,

    // Notifications
    val hasUnreadNotifications: Boolean = false,

    // Dashboard
    val isDashboardLoading: Boolean = false,
    val dashboardError: String? = null,

    // Tracking health — shown as a warning card when setup is incomplete
    val trackingHealthNeedsAttention: Boolean = false,

    // Future placeholders — location phase
    val locationPermissionGranted: Boolean = false,
    val lastSyncedLocation: String? = null,
    val locationSyncInProgress: Boolean = false,
    val syncProgress: Float = 0f
)
