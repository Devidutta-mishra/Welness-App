package com.example.yourswelnes.feature.camera.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.home.data.repository.GroupDetailsRepository
import com.example.yourswelnes.feature.home.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupSelectionUiState(
    val isLoading: Boolean = false,
    val groups: List<Group> = emptyList(),
    val selectedGroup: Group? = null,
    val error: String? = null,
    val validationError: String? = null
)

@HiltViewModel
class GroupSelectionViewModel @Inject constructor(
    private val groupDetailsRepository: GroupDetailsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupSelectionUiState())
    val uiState: StateFlow<GroupSelectionUiState> = _uiState.asStateFlow()

    init {
        loadGroups(forceRefresh = true)
    }

    fun loadGroups(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            groupDetailsRepository.fetchGroups(forceRefresh)
                .onSuccess { groups ->
                    _uiState.update { it.copy(isLoading = false, groups = groups) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load groups")
                    }
                }
        }
    }

    fun selectGroup(group: Group) {
        _uiState.update { it.copy(selectedGroup = group, validationError = null) }
    }

    fun validateAndProceed(onSuccess: (Group) -> Unit) {
        val selected = _uiState.value.selectedGroup
        if (selected == null) {
            _uiState.update { it.copy(validationError = "Please select a group") }
        } else {
            _uiState.update { it.copy(validationError = null) }
            onSuccess(selected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedGroup = null, validationError = null) }
    }
}
