package com.example.yourswelnes.feature.auth.data.repository

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.feature.auth.domain.model.AuthUser
import com.example.yourswelnes.feature.auth.data.remote.api.AuthApi
import com.example.yourswelnes.feature.auth.data.remote.dto.LoginRequestDto
import com.example.yourswelnes.feature.auth.data.remote.mapper.toDomain
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import timber.log.Timber

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferencesDataStore
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = authPreferences.cachedUser

    override suspend fun isLoggedIn(): Boolean = authPreferences.isLoggedIn()

    override suspend fun login(phone: String, password: String): Result<AuthUser> = runCatching {
        val response = authApi.login(
            LoginRequestDto(
                phone = phone,
                password = password,
                deviceToken = PLACEHOLDER_DEVICE_TOKEN
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
        Timber.i("Login succeeded for user %s", user.id)
        user
    }.recoverCatching { throw it.toFriendlyAuthException("Unable to sign in. Please try again.") }

    override suspend fun logout() {
        Timber.i("Logging out — clearing local session")
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

    private companion object {
        const val PLACEHOLDER_DEVICE_TOKEN = "pending_fcm_token"
        const val HTTP_UNAUTHORIZED = 401
    }
}

class AuthException(message: String) : Exception(message)
