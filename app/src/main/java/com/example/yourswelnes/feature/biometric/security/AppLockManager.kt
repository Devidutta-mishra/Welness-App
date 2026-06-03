package com.example.yourswelnes.feature.biometric.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
class AppLockManager @Inject constructor() {

    companion object {
        const val LOCK_TIMEOUT_MS = 60_000L // 60 seconds
    }

    private var backgroundedAt: Long = Long.MAX_VALUE

    private val _isLockRequired = MutableStateFlow(false)
    val isLockRequired: StateFlow<Boolean> = _isLockRequired.asStateFlow()

    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
        Timber.d("App backgrounded at $backgroundedAt")
    }

    fun onAppForegrounded() {
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (elapsed >= LOCK_TIMEOUT_MS) {
            Timber.d("App was away for ${elapsed}ms — requiring re-authentication")
            _isLockRequired.value = true
        }
    }

    fun onAuthSuccess() {
        backgroundedAt = Long.MAX_VALUE
        _isLockRequired.value = false
        Timber.d("Authentication succeeded — lock cleared")
    }
}
