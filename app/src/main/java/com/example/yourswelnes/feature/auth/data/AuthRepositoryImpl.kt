package com.example.yourswelnes.feature.auth.data

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.FcmPreferencesDataStore
import com.example.yourswelnes.feature.auth.model.AuthUser
import com.example.yourswelnes.feature.auth.data.api.AuthApi
import com.example.yourswelnes.feature.auth.data.dto.LoginRequestDto
import com.example.yourswelnes.feature.auth.data.mapper.toDomain
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import timber.log.Timber

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferencesDataStore,
    private val fcmPrefs: FcmPreferencesDataStore
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = authPreferences.cachedUser

    override suspend fun isLoggedIn(): Boolean = authPreferences.isLoggedIn()

    override suspend fun login(phone: String, password: String): Result<AuthUser> = runCatching {
        // Use the locally cached token; if it isn't cached yet (fresh install race condition)
        // wait briefly for Firebase to generate one rather than sending a placeholder that
        // the backend would permanently associate as an invalid token.
        val deviceToken = fcmPrefs.getFcmToken() ?: fetchFcmTokenNow()
        Timber.tag(TAG).i("TOKEN SENT TO SERVER: $deviceToken")

        val response = authApi.login(
            LoginRequestDto(
                phone = phone,
                password = password,
                deviceToken = deviceToken
            )
        )

        val token = response.token
        if (token.isNullOrBlank() || response.user == null) {
            Timber.w("Login rejected: status=%s", response.status)
            throw AuthException("Invalid phone number or password.")
        }

        val user = response.user.toDomain().copy(
            profileImage = response.profileImage?.trim()?.takeIf { it.isNotEmpty() },
            redirect = response.redirect?.trim()?.takeIf { it.isNotEmpty() }
        )
        authPreferences.saveAuthData(token = token, user = user)
        Timber.i("Login succeeded")
        user
    }.recoverCatching { throw it.toFriendlyAuthException("Unable to sign in. Please try again.") }

    override suspend fun logout() {
        Timber.tag(TAG).i("Logging out — clearing local session")

        // Delete the FCM token from Firebase so the backend can no longer deliver
        // notifications for the departing user to this device. Firebase will generate
        // a fresh token; onNewToken() stores it so the next login registers it for
        // the new user, preventing any cross-user notification delivery.
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener { cont.resume(Unit) }  // resume on success or failure
            }
            fcmPrefs.clearFcmToken()
            fcmPrefs.clearShownIds()
            Timber.tag(TAG).i("USER LOGOUT TOKEN CLEARED: FCM token deleted from Firebase")
        } catch (e: Exception) {
            Timber.tag(TAG).w("FCM token deletion issue (non-fatal): ${e.message}")
        }

        authPreferences.clearAuthData()
    }

    private fun Throwable.toFriendlyAuthException(fallback: String): AuthException = when (this) {
        is AuthException -> this
        is HttpException -> {
            Timber.w(this, "HTTP error during auth: code=%d", code())
            if (code() == HTTP_UNAUTHORIZED) {
                AuthException("Invalid phone number or password.")
            } else {
                AuthException(fallback)
            }
        }
        is IOException -> {
            Timber.w(this, "Network error during auth")
            AuthException("No internet connection. Please check your network.")
        }
        else -> {
            Timber.e(this, "Unexpected error during auth")
            AuthException(fallback)
        }
    }

    /** Fetches the current FCM registration token directly from Firebase with a 5-second timeout.
     *  Used only when the locally cached token hasn't been written yet (first-install race). */
    private suspend fun fetchFcmTokenNow(): String =
        withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        val token = task.result?.takeIf { it.isNotEmpty() }
                        if (token != null) {
                            Timber.tag(TAG).i("FCM token fetched on-demand before login")
                        } else {
                            Timber.tag(TAG).w("FCM token fetch failed — using placeholder")
                        }
                        cont.resume(token ?: PLACEHOLDER_DEVICE_TOKEN)
                    }
            }
        } ?: run {
            Timber.tag(TAG).w("FCM token fetch timed out — using placeholder")
            PLACEHOLDER_DEVICE_TOKEN
        }

    private companion object {
        const val TAG = "AuthRepo"
        const val PLACEHOLDER_DEVICE_TOKEN = "pending_fcm_token"
        const val HTTP_UNAUTHORIZED = 401
    }
}

class AuthException(message: String) : Exception(message)
