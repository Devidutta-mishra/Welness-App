package com.example.yourswelnes.feature.requirements.presentation

data class RequirementsUiState(
    val isInternetAvailable: Boolean = false,
    val isLocationEnabled: Boolean = false,
    // Guards against default false-values triggering navigation before any real check runs
    val hasChecked: Boolean = false
) {
    val allMet: Boolean get() = hasChecked && isInternetAvailable && isLocationEnabled
    val hasAnyIssue: Boolean get() = hasChecked && (!isInternetAvailable || !isLocationEnabled)
}
