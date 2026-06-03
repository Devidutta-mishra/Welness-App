package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.feature.home.domain.model.ClubDetails

interface ClubRepository {
    suspend fun getClubDetails(): Result<ClubDetails>
}
