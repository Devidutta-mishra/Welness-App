package com.example.yourswelnes.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.auth.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private val navigationEvents = Channel<SplashNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = navigationEvents.receiveAsFlow()

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val startTime = System.currentTimeMillis()
            
            val isLoggedIn = authRepository.isLoggedIn()
            
            // Ensure splash is visible for at least 2 seconds for branding
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingTime = 1500L - elapsedTime
            if (remainingTime > 0) {
                delay(remainingTime)
            }

            _uiState.update {
                it.copy(isLoading = false, isLoggedIn = isLoggedIn)
            }
            navigationEvents.send(
                if (isLoggedIn) {
                    SplashNavigationEvent.NavigateToBiometric
                } else {
                    SplashNavigationEvent.NavigateToLogin
                }
            )
        }
    }
}

sealed interface SplashNavigationEvent {
    data object NavigateToBiometric : SplashNavigationEvent
    data object NavigateToLogin : SplashNavigationEvent
}
