package com.example.yourswelnes.feature.dashboard.data.repository

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.feature.dashboard.data.remote.api.DashboardApi
import com.example.yourswelnes.feature.dashboard.data.remote.dto.GenerateRedirectUrlRequest
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import timber.log.Timber

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val authPreferences: AuthPreferencesDataStore
) : DashboardRepository {

    override suspend fun getDashboardUrl(): Result<String> = runCatching {
        val user = authPreferences.cachedUser.firstOrNull()
            ?: throw DashboardException("User session not found. Please log in again.")

        val userId = user.id.toIntOrNull()
            ?: throw DashboardException("Invalid user ID. Please log in again.")

        Timber.d("Dashboard SSO request")

        val response = dashboardApi.generateRedirectUrl(GenerateRedirectUrlRequest(userId = userId))

        if (response.success == true && !response.redirectUrl.isNullOrBlank()) {
            Timber.i("Dashboard redirect URL generated successfully")
            response.redirectUrl
        } else {
            throw DashboardException(response.message ?: "Failed to generate dashboard link.")
        }
    }.recoverCatching { cause ->
        when (cause) {
            is DashboardException -> throw cause
            is IOException -> {
                Timber.e(cause, "Network error during dashboard SSO")
                throw DashboardException("No internet connection. Please check your network.")
            }
            is HttpException -> {
                Timber.e(cause, "HTTP %d during dashboard SSO", cause.code())
                throw DashboardException("Server error (${cause.code()}). Please try again.")
            }
            else -> {
                Timber.e(cause, "Unexpected error during dashboard SSO")
                throw DashboardException("Unable to open dashboard. Please try again.")
            }
        }
    }
}

class DashboardException(message: String) : Exception(message)
