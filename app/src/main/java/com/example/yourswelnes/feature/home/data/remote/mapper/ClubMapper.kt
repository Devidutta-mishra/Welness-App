package com.example.yourswelnes.feature.home.data.remote.mapper

import com.example.yourswelnes.feature.home.data.remote.dto.ClubDetailsResponseDto
import com.example.yourswelnes.feature.home.domain.model.ClubDetails

fun ClubDetailsResponseDto.toDomain(): ClubDetails {
    val item = data?.firstOrNull()
    return ClubDetails(
        id = item?.id ?: 0,
        clubName = item?.clubName ?: "",
        latitude = item?.latitude ?: 0.0,
        longitude = item?.longitude ?: 0.0
    )
}
