package com.example.yourswelnes.feature.biometric.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.biometric.data.BiometricRepository
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BiometricViewModel @Inject constructor(
    private val biometricRepository: BiometricRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiometricUiState())
    val uiState: StateFlow<BiometricUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<BiometricNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun canAuthenticate(): Boolean = biometricRepository.canAuthenticate()

    fun onAuthSuccess() {
        appLockManager.onAuthSuccess()
        _uiState.update { it.copy(isAuthenticated = true, errorMessage = null) }
        viewModelScope.launch {
            _navigationEvent.send(BiometricNavigationEvent.NavigateToHome)
        }
    }

    fun onAuthError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

sealed interface BiometricNavigationEvent {
    data object NavigateToHome : BiometricNavigationEvent
}
