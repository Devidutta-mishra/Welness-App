package com.example.yourswelnes.feature.dashboard.data.repository

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.feature.dashboard.data.remote.api.DashboardApi
import com.example.yourswelnes.feature.dashboard.data.remote.dto.DashboardLoginRequestDto
import com.example.yourswelnes.feature.dashboard.data.remote.dto.DashboardLoginResponseDto
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val authPreferences: AuthPreferencesDataStore
) : DashboardRepository {

    private val gson = Gson()

    override suspend fun getDashboardUrl(): Result<String> = runCatching {
        val token = authPreferences.getToken()
        if (token.isNullOrBlank()) {
            throw DashboardException("Not authenticated. Please log in again.")
        }

        // {{bearerToken}} in Postman resolves to the raw stored token — no "Bearer " prefix.
        // Sending "Bearer <token>" breaks the server-side token lookup.
        val response: Response<ResponseBody> = dashboardApi.getDashboardLoginUrl(
            DashboardLoginRequestDto(token = token)
        )

        val httpCode = response.code()
        Timber.d("getDashboardUrl: HTTP %d", httpCode)

        // PRIMARY PATH — backend returns 302 redirect to the authenticated dashboard URL.
        // OkHttp is configured with followRedirects=false so we see this response directly.
        if (httpCode in 300..399) {
            response.body()?.close()
            val location = response.headers()["Location"]
            if (location.isNullOrBlank()) {
                Timber.w("getDashboardUrl: 3xx with no Location header (code=%d)", httpCode)
                throw DashboardException("Unable to open dashboard. Please try again.")
            }
            // Resolve relative Location URLs against the ywcenter.com origin.
            val resolvedUrl = if (location.startsWith("http")) {
                location
            } else {
                response.raw().request.url.resolve(location)?.toString()
                    ?: throw DashboardException("Unable to open dashboard. Please try again.")
            }
            Timber.d("getDashboardUrl: redirect captured [REDACTED]")
            return@runCatching resolvedUrl
        }

        // FALLBACK — 200 with a JSON body containing an explicit URL field.
        if (response.isSuccessful) {
            val bodyString = response.body()?.use { it.string() }
            val url = extractUrlFromJson(bodyString)
            if (url != null) {
                return@runCatching url
            }
            Timber.w("getDashboardUrl: 200 body contained no URL (likely raw HTML, code=%d)", httpCode)
            throw DashboardException("Unable to open dashboard. Please try again.")
        }

        response.body()?.close()
        Timber.e("getDashboardUrl: HTTP error %d", httpCode)
        throw DashboardException("Server error ($httpCode). Please try again.")

    }.recoverCatching { cause ->
        throw when (cause) {
            is DashboardException -> cause
            is IOException -> {
                Timber.e(cause, "getDashboardUrl network error [%s]", cause.javaClass.simpleName)
                DashboardException("Unable to reach the server. Please check your connection and try again.")
            }
            is HttpException -> {
                Timber.e(cause, "getDashboardUrl HTTP %d", cause.code())
                DashboardException("Server error (${cause.code()}). Please try again.")
            }
            else -> {
                Timber.e(cause, "getDashboardUrl unexpected [%s]", cause.javaClass.simpleName)
                DashboardException("Unable to open dashboard. Please try again.")
            }
        }
    }

    /**
     * Attempts to pull a URL string out of a JSON response body.
     * Returns null if the body is blank, not valid JSON, or contains no URL field.
     */
    private fun extractUrlFromJson(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val dto = gson.fromJson(body, DashboardLoginResponseDto::class.java)
            dto.url ?: dto.loginUrl ?: dto.redirectUrl ?: dto.redirect
                ?: dto.data?.url ?: dto.data?.loginUrl
                ?: dto.data?.redirectUrl ?: dto.data?.redirect
        }.onFailure { cause ->
            if (cause is JsonSyntaxException) {
                Timber.w("getDashboardUrl: response body is not JSON (HTML page received)")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

class DashboardException(message: String) : Exception(message)
