package com.example.yourswelnes.feature.auth.data.repository

import com.example.yourswelnes.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Emits the cached authenticated user, or null when logged out. */
    val currentUser: Flow<AuthUser?>

    /** True when a valid session token is persisted locally (no network call). */
    suspend fun isLoggedIn(): Boolean

    /** Performs login against the API and persists the session on success. */
    suspend fun login(phone: String, password: String): Result<AuthUser>

    /** Clears the active session locally. */
    suspend fun logout()
}
