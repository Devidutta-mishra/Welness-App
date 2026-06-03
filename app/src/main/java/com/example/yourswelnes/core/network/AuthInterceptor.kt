package com.example.yourswelnes.core.network

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the `Authorization: Bearer <token>` header to every outgoing request when a token is
 * present in [AuthPreferencesDataStore]. The login request itself carries no token and is simply
 * passed through unchanged.
 *
 * Reading the token blocks the OkHttp dispatcher thread briefly via [runBlocking]; this is the
 * standard pattern for a synchronous interceptor backed by an async DataStore.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authPreferences: AuthPreferencesDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { authPreferences.getToken() }
        val request = chain.request()
        
        val authorized = if (token.isNullOrEmpty()) {
            request.newBuilder()
                .header("Accept", "application/json")
                .build()
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        }
        return chain.proceed(authorized)
    }
}
