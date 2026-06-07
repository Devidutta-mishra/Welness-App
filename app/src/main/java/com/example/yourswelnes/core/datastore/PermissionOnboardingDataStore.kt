package com.example.yourswelnes.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.permissionOnboardingDataStore by preferencesDataStore(name = "permission_onboarding")

/**
 * Persists permission onboarding progress across app restarts.
 *
 * Why this exists:
 * Without persistence, every cold start re-builds the step list from scratch. OEM steps (Xiaomi
 * Auto Start, Vivo Background Activity, etc.) have no Android API to verify completion, so they
 * reappear on every launch. This DataStore records which steps the user has dismissed so they
 * are never shown again in a subsequent session.
 *
 * Battery optimization: once the user has seen the request and either exempted the app or
 * explicitly skipped, we record the attempt timestamp. This prevents a cold-start from
 * auto-opening the battery settings dialog again within a short window.
 */
@Singleton
class PermissionOnboardingDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.permissionOnboardingDataStore

    // ── Onboarding completion ──────────────────────────────────────────────────

    /** Emits true once the user has passed through the full permission wizard at least once. */
    val isOnboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }

    /**
     * Resets onboarding state. Used when a mandatory permission is later revoked by the user
     * from system settings — we want the wizard to reappear for that specific permission.
     */
    suspend fun resetOnboarding() {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = false }
    }

    // ── Battery optimization attempt tracking ─────────────────────────────────

    /** Records the wall-clock timestamp when the battery settings screen was last launched. */
    suspend fun saveBatteryOptAttempt() {
        dataStore.edit { it[KEY_BATTERY_OPT_LAST_ATTEMPT] = System.currentTimeMillis() }
    }

    suspend fun getBatteryOptLastAttemptTime(): Long =
        dataStore.data.firstOrNull()?.get(KEY_BATTERY_OPT_LAST_ATTEMPT) ?: 0L

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_BATTERY_OPT_LAST_ATTEMPT = longPreferencesKey("battery_opt_last_attempt")
    }
}
