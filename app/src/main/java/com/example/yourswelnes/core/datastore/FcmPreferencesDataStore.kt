package com.example.yourswelnes.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

private val Context.fcmDataStore by preferencesDataStore(name = "fcm_prefs")

@Singleton
class FcmPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.fcmDataStore

    // ── Token ─────────────────────────────────────────────────────────────────

    suspend fun getFcmToken(): String? =
        dataStore.data.firstOrNull()?.get(KEY_FCM_TOKEN)

    suspend fun saveFcmToken(token: String) {
        dataStore.edit { it[KEY_FCM_TOKEN] = token }
    }

    suspend fun clearFcmToken() {
        dataStore.edit { it.remove(KEY_FCM_TOKEN) }
    }

    // ── Deduplication ─────────────────────────────────────────────────────────
    // notification_ids are globally unique from the backend, so no per-user scoping needed.
    // The set is capped at MAX_SHOWN_IDS to prevent unbounded growth.

    suspend fun isNotificationShown(notificationId: String): Boolean {
        val shown = dataStore.data.firstOrNull()?.get(KEY_SHOWN_IDS) ?: emptySet()
        return notificationId in shown
    }

    suspend fun markNotificationShown(notificationId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SHOWN_IDS] ?: emptySet()
            val updated = current + notificationId
            prefs[KEY_SHOWN_IDS] = if (updated.size > MAX_SHOWN_IDS) {
                updated.drop(updated.size - MAX_SHOWN_IDS).toSet()
            } else {
                updated
            }
        }
    }

    /** Called on logout so that re-sent notification IDs are not silently suppressed. */
    suspend fun clearShownIds() {
        dataStore.edit { it.remove(KEY_SHOWN_IDS) }
    }

    companion object {
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_SHOWN_IDS = stringSetPreferencesKey("shown_notification_ids")
        private const val MAX_SHOWN_IDS = 200
    }
}
