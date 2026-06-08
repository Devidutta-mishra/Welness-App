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

    /**
     * Decides how the lock screen should verify the user:
     *  - [AuthRequirement.BIOMETRIC_PROMPT] when a biometric or device credential exists,
     *  - [AuthRequirement.BYPASS] when the phone has no screen lock at all (nothing to verify against),
     *  - [AuthRequirement.UNAVAILABLE] for the rare secure-but-unusable case.
     */
    fun resolveAuthRequirement(): AuthRequirement = when {
        biometricRepository.canAuthenticate() -> AuthRequirement.BIOMETRIC_PROMPT
        !biometricRepository.isDeviceSecure() -> AuthRequirement.BYPASS
        else -> AuthRequirement.UNAVAILABLE
    }

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

enum class AuthRequirement {
    /** Show the system biometric / device-credential prompt. */
    BIOMETRIC_PROMPT,

    /** No screen lock on the device — let the user straight through. */
    BYPASS,

    /** Device is secure but no authenticator is currently usable. */
    UNAVAILABLE
}
