package com.example.yourswelnes.feature.auth.data.remote.api

import com.example.yourswelnes.feature.auth.data.remote.dto.LoginRequestDto
import com.example.yourswelnes.feature.auth.data.remote.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for authentication endpoints.
 *
 * Base URL is `https://ywadvance.com/` (configured in [com.example.yourswelnes.di.NetworkModule]).
 * Only the login endpoint is documented in api_structure.md — there is no refresh, validate, or
 * logout endpoint, so those flows are handled client-side.
 */
interface AuthApi {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto
}
