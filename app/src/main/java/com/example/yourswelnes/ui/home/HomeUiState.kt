package com.example.yourswelnes.ui.home

/**
 * UI state for the Home screen.
 *
 * This file intentionally includes placeholder properties for future location-related
 * features (permission state, last synced location, sync progress, etc.) so the
 * Home UI can be extended later without changing the state model shape.
 */
data class HomeUiState(
    val userName: String = "Your Name",
    val profileImageUrl: String? = null,

    // Future-proofing placeholders (do NOT implement location behaviour yet) ---
    // These will be used in later phases to show permission state, last sync info,
    // and progress of periodic location sync.
    val locationPermissionGranted: Boolean = false,
    val lastSyncedLocation: String? = null,
    val locationSyncInProgress: Boolean = false,
    val syncProgress: Float = 0f
)

