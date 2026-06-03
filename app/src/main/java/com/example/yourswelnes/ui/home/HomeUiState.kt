package com.example.yourswelnes.ui.home

data class HomeUiState(
    val userName: String = "Your Name",
    val profileImageUrl: String? = null,
    val showLogoutDialog: Boolean = false,
    val clubName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,

    // Dashboard
    val isDashboardLoading: Boolean = false,
    val dashboardError: String? = null,

    // Future placeholders — location phase
    val locationPermissionGranted: Boolean = false,
    val lastSyncedLocation: String? = null,
    val locationSyncInProgress: Boolean = false,
    val syncProgress: Float = 0f
)
