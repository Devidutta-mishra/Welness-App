package com.example.yourswelnes.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.home.data.repository.GroupDetailsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GroupScheduleViewModel @Inject constructor(
    private val groupDetailsRepository: GroupDetailsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupScheduleUiState())
    val uiState: StateFlow<GroupScheduleUiState> = _uiState.asStateFlow()

    init {
        loadGroupDetails()
    }

    fun loadGroupDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            groupDetailsRepository.fetchGroupDetails()
                .onSuccess { slots ->
                    val now = LocalTime.now()
                    val current = slots.firstOrNull { slot ->
                        !now.isBefore(slot.startTime) && !now.isAfter(slot.endTime)
                    }
                    val next = slots
                        .filter { it.startTime.isAfter(now) }
                        .minByOrNull { it.startTime }
                    _uiState.update {
                        it.copy(isLoading = false, currentActivity = current, nextActivity = next)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load schedule")
                    }
                }
        }
    }
}
