package com.example.yourswelnes.feature.home.data

import com.example.yourswelnes.feature.home.model.ClubDetails

interface ClubRepository {
    suspend fun getClubDetails(): Result<ClubDetails>
}
