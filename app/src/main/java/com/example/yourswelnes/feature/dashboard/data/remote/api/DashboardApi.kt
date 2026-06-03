package com.example.yourswelnes.feature.dashboard.data.remote.api

import com.example.yourswelnes.feature.dashboard.data.remote.dto.DashboardLoginRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * POST https://ywcenter.com/api/login-url
 *
 * The backend responds with HTTP 302 + Location: <authenticated dashboard URL>.
 * We return [Response<ResponseBody>] (not a DTO) so the repository can read the
 * Location header directly before OkHttp would have followed the redirect.
 * The OkHttpClient wired to this API has followRedirects=false for this reason.
 */
interface DashboardApi {

    @POST("api/login-url")
    suspend fun getDashboardLoginUrl(
        @Body request: DashboardLoginRequestDto
    ): Response<ResponseBody>
}
