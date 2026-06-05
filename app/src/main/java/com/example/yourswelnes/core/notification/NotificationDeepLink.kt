package com.example.yourswelnes.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped coordination point between MainActivity (which receives the notification tap
 * intent) and the navigation graph (which reacts by opening the Notifications screen).
 *
 * The value is the mapping ID of the tapped notification (to be marked read on arrival), or
 * null when there is no pending deep link.
 */
object NotificationDeepLink {
    private val _pendingNotificationId = MutableStateFlow<Int?>(null)
    val pendingNotificationId: StateFlow<Int?> = _pendingNotificationId.asStateFlow()

    fun set(id: Int?) { _pendingNotificationId.value = id }
    fun consume() { _pendingNotificationId.value = null }
}
