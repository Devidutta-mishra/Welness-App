package com.example.yourswelnes.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.yourswelnes.feature.auth.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.authDataStore

    val authToken: Flow<String?> = dataStore.data.map { it[KEY_TOKEN] }

    val cachedUser: Flow<AuthUser?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID]
        val displayId = prefs[KEY_DISPLAY_ID]
        val name = prefs[KEY_USER_NAME]
        if (id.isNullOrEmpty() || name == null) {
            null
        } else {
            AuthUser(
                id = id,
                userId = displayId,
                name = name,
                email = prefs[KEY_USER_EMAIL],
                phone = prefs[KEY_USER_PHONE],
                gender = prefs[KEY_USER_GENDER],
                role = prefs[KEY_USER_ROLE],
                status = prefs[KEY_USER_STATUS],
                level = prefs[KEY_USER_LEVEL],
                imageUrl = prefs[KEY_USER_IMAGE],
                profileImage = prefs[KEY_PROFILE_IMAGE],
                redirect = prefs[KEY_REDIRECT]
            )
        }
    }

    /** Blocking-safe single read of the token for the OkHttp Authorization interceptor. */
    suspend fun getToken(): String? = dataStore.data.firstOrNull()?.get(KEY_TOKEN)

    /**
     * The active user's stable server id. Used to scope the tracking-window alarm's PendingIntent
     * request code so one user's alarm can never overwrite or cancel another's on the same device.
     * Returns null when no session exists (already logged out).
     */
    suspend fun getUserId(): String? = dataStore.data.firstOrNull()?.get(KEY_USER_ID)

    suspend fun saveAuthData(token: String, user: AuthUser) {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_LOGGED_IN] = true
            prefs[KEY_USER_ID] = user.id
            user.userId.put(prefs, KEY_DISPLAY_ID)
            prefs[KEY_USER_NAME] = user.name
            user.email.put(prefs, KEY_USER_EMAIL)
            user.phone.put(prefs, KEY_USER_PHONE)
            user.gender.put(prefs, KEY_USER_GENDER)
            user.role.put(prefs, KEY_USER_ROLE)
            user.status.put(prefs, KEY_USER_STATUS)
            user.level.put(prefs, KEY_USER_LEVEL)
            user.imageUrl.put(prefs, KEY_USER_IMAGE)
            user.profileImage.put(prefs, KEY_PROFILE_IMAGE)
            user.redirect.put(prefs, KEY_REDIRECT)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        val prefs = dataStore.data.firstOrNull() ?: return false
        return prefs[KEY_LOGGED_IN] == true && !prefs[KEY_TOKEN].isNullOrBlank()
    }

    suspend fun clearAuthData() {
        dataStore.edit { prefs ->
            SESSION_KEYS.forEach { prefs.remove(it) }
        }
    }

    private fun String?.put(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>
    ) {
        if (this != null) prefs[key] = this else prefs.remove(key)
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("auth_token")
        val KEY_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_DISPLAY_ID = stringPreferencesKey("display_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        val KEY_USER_GENDER = stringPreferencesKey("user_gender")
        val KEY_USER_ROLE = stringPreferencesKey("user_role")
        val KEY_USER_STATUS = stringPreferencesKey("user_status")
        val KEY_USER_LEVEL = stringPreferencesKey("user_level")
        val KEY_USER_IMAGE = stringPreferencesKey("user_image")
        val KEY_PROFILE_IMAGE = stringPreferencesKey("profile_image")
        val KEY_REDIRECT = stringPreferencesKey("redirect")

        val SESSION_KEYS = listOf(
            KEY_TOKEN, KEY_LOGGED_IN, KEY_USER_ID, KEY_DISPLAY_ID, KEY_USER_NAME, KEY_USER_EMAIL,
            KEY_USER_PHONE, KEY_USER_GENDER, KEY_USER_ROLE, KEY_USER_STATUS,
            KEY_USER_LEVEL, KEY_USER_IMAGE, KEY_PROFILE_IMAGE, KEY_REDIRECT
            // FCM token is intentionally NOT in SESSION_KEYS — it belongs to the device,
            // not the user session. It must survive logout so the next login can use it.
        )
    }
}
