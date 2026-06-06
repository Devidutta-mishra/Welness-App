package com.example.yourswelnes.feature.home.data.api

import com.example.yourswelnes.feature.home.data.dto.ClubDetailsResponseDto
import retrofit2.http.GET

interface ClubApi {

    @GET("api/user-club-details")
    suspend fun getClubDetails(): ClubDetailsResponseDto
}
