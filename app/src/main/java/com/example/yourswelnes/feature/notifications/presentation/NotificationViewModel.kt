package com.example.yourswelnes.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.notifications.data.repository.NotificationRepository
import com.example.yourswelnes.feature.notifications.domain.model.NotificationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _selectedNotification = MutableStateFlow<NotificationItem?>(null)

    val uiState: StateFlow<NotificationUiState> = combine(
        notificationRepository.notifications,
        _isLoading,
        _error,
        _selectedNotification
    ) { notifications, isLoading, error, selected ->
        NotificationUiState(
            isLoading = isLoading,
            notifications = notifications,
            error = error,
            selectedNotification = selected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationUiState()
    )

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            notificationRepository.fetchNotifications()
                .onSuccess { _isLoading.value = false }
                .onFailure { error ->
                    _isLoading.value = false
                    _error.value = error.message ?: "Failed to load notifications"
                }
        }
    }

    fun onNotificationClicked(notification: NotificationItem) {
        _selectedNotification.value = notification
        if (!notification.isRead) {
            markAsRead(notification.id)
        }
    }

    fun dismissDialog() {
        _selectedNotification.value = null
    }

    private fun markAsRead(notificationId: Int) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
                .onFailure { error ->
                    Timber.e(error, "markAsRead failed for id=$notificationId")
                }
        }
    }

    /** Called from NavGraph when the screen is opened via a system notification tap. */
    fun markAsReadExternal(notificationId: Int) = markAsRead(notificationId)

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            notificationRepository.refreshNotifications()
                .onSuccess { _isLoading.value = false }
                .onFailure { error ->
                    _isLoading.value = false
                    _error.value = error.message ?: "Failed to refresh"
                }
        }
    }
}
