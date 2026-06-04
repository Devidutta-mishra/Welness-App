package com.example.yourswelnes.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.work.WorkManager
import com.example.yourswelnes.core.location.LocationForegroundService
import com.example.yourswelnes.core.worker.LocationUploadWorker
import com.example.yourswelnes.feature.auth.data.repository.AuthRepository
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import com.example.yourswelnes.feature.dashboard.data.repository.DashboardRepository
import com.example.yourswelnes.feature.home.data.repository.ClubRepository
import com.example.yourswelnes.feature.location.data.repository.LocationConfigRepository
import com.example.yourswelnes.feature.notifications.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val clubRepository: ClubRepository,
    private val notificationRepository: NotificationRepository,
    private val dashboardRepository: DashboardRepository,
    private val appLockManager: AppLockManager,
    private val locationConfigRepository: LocationConfigRepository
) : ViewModel() {

    val isLockRequired: StateFlow<Boolean> = appLockManager.isLockRequired

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = navigationEvents.receiveAsFlow()

    init {
        observeUser()
        loadClubDetails()
        fetchLocationConfig()
        observeNotifications()
        refreshNotifications()
    }

    private fun observeUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            userName = user.name,
                            profileImageUrl = user.profileImage ?: user.imageUrl
                        )
                    }
                }
            }
        }
    }

    private fun fetchLocationConfig() {
        viewModelScope.launch {
            locationConfigRepository.getLocationConfig()
                .onSuccess { config ->
                    Timber.tag("HomeViewModel").d(
                        "Location config fetched — window: ${config.trackingStartTime}–${config.trackingEndTime}, " +
                        "interval: ${config.trackingIntervalSeconds}s, upload: ${config.uploadIntervalMinutes}min"
                    )
                }
                .onFailure {
                    Timber.tag("HomeViewModel").w("Location config fetch failed (cached will be used): ${it.message}")
                }
        }
    }

    private fun loadClubDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            clubRepository.getClubDetails()
                .onSuccess { details ->
                    _uiState.update { it.copy(isLoading = false, clubName = details.clubName) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clubName = "Club information unavailable",
                            error = error.message
                        )
                    }
                }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationRepository.notifications.collect { notifications ->
                val hasUnread = notificationRepository.getUnreadCount(notifications) > 0
                _uiState.update { it.copy(hasUnreadNotifications = hasUnread) }
            }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            notificationRepository.fetchNotifications()
        }
    }

    fun openDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDashboardLoading = true, dashboardError = null) }
            dashboardRepository.getDashboardUrl()
                .onSuccess { url ->
                    _uiState.update { it.copy(isDashboardLoading = false) }
                    navigationEvents.send(HomeNavigationEvent.OpenDashboard(url))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDashboardLoading = false,
                            dashboardError = error.message ?: "Unable to open dashboard. Please try again."
                        )
                    }
                }
        }
    }

    fun onLogoutClicked() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun onLogoutConfirmed() {
        _uiState.update { it.copy(showLogoutDialog = false) }
        viewModelScope.launch {
            Timber.tag("HomeViewModel").d("Logout confirmed — stopping location service and upload worker")
            context.stopService(LocationForegroundService.stopIntent(context))
            LocationUploadWorker.cancel(WorkManager.getInstance(context))
            authRepository.logout()
            Timber.tag("HomeViewModel").d("Auth cleared — navigating to login")
            navigationEvents.send(HomeNavigationEvent.NavigateToLogin)
        }
    }

    fun onLogoutDismissed() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }
}

sealed interface HomeNavigationEvent {
    data object NavigateToLogin : HomeNavigationEvent
    data class OpenDashboard(val url: String) : HomeNavigationEvent
}
