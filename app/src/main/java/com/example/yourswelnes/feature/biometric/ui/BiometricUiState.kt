package com.example.yourswelnes.feature.biometric.ui

data class BiometricUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null
)
