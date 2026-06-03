package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.feature.home.domain.model.ActivitySlot

interface GroupDetailsRepository {
    suspend fun fetchGroupDetails(): Result<List<ActivitySlot>>
}
