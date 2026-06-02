package com.example.yourswelnes.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple ViewModel for the Home screen. Holds the [HomeUiState].
 *
 * Note: This ViewModel intentionally does NOT implement location collection or any
 * long-running work. It exposes simple methods to mutate state so previews and
 * UI wiring can be tested. Real implementations (permissions, location, sync)
 * will be added in later phases.
 */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            userName = "Ansuman Senapati",
            profileImageUrl = null
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Quick helpers to update the UI state. In a production app these would be
    // replaced with real repository interactions and business logic.
    fun setUserName(name: String) {
        _uiState.value = _uiState.value.copy(userName = name)
    }

    fun setProfileImage(url: String?) {
        _uiState.value = _uiState.value.copy(profileImageUrl = url)
    }

    fun setLocationPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationPermissionGranted = granted)
    }

    fun startSync() {
        _uiState.value = _uiState.value.copy(locationSyncInProgress = true, syncProgress = 0f)
    }

    fun updateSyncProgress(progress: Float) {
        _uiState.value = _uiState.value.copy(syncProgress = progress)
    }

    fun finishSync(lastLocation: String?) {
        _uiState.value = _uiState.value.copy(
            locationSyncInProgress = false,
            lastSyncedLocation = lastLocation,
            syncProgress = 1f
        )
    }
}

