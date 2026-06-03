package com.example.yourswelnes.feature.home.data.remote.api

import com.example.yourswelnes.feature.home.data.remote.dto.ClubDetailsResponseDto
import retrofit2.http.GET

interface ClubApi {

    @GET("api/user-club-details")
    suspend fun getClubDetails(): ClubDetailsResponseDto
}
