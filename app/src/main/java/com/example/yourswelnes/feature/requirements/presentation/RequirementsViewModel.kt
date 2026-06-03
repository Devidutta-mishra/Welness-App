package com.example.yourswelnes.feature.requirements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.requirements.data.repository.RequirementsRepository
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
class RequirementsViewModel @Inject constructor(
    private val requirementsRepository: RequirementsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequirementsUiState())
    val uiState: StateFlow<RequirementsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun checkAndProceed(nextDestination: String) {
        val internet = requirementsRepository.isInternetAvailable()
        val location = requirementsRepository.isLocationEnabled()

        _uiState.update {
            it.copy(isInternetAvailable = internet, isLocationEnabled = location, hasChecked = true)
        }

        if (internet && location) {
            viewModelScope.launch {
                _navigationEvent.send(nextDestination)
            }
        }
    }

    // Used by the HOME screen on resume — updates state so the screen can react,
    // but does NOT emit a navigation event when requirements pass (avoids HOME→HOME loop).
    fun recheckFromBackground() {
        val internet = requirementsRepository.isInternetAvailable()
        val location = requirementsRepository.isLocationEnabled()
        _uiState.update {
            it.copy(isInternetAvailable = internet, isLocationEnabled = location, hasChecked = true)
        }
    }
}
