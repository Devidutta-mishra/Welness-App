package com.example.yourswelnes.feature.biometric.presentation

data class BiometricUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null
)
