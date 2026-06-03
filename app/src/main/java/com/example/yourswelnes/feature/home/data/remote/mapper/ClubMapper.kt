package com.example.yourswelnes.feature.home.data.remote.mapper

import com.example.yourswelnes.feature.home.data.remote.dto.ClubDetailsResponseDto
import com.example.yourswelnes.feature.home.domain.model.ClubDetails

fun ClubDetailsResponseDto.toDomain(): ClubDetails {
    // Check top-level fields first, then wrapped data
    val name = clubName ?: club_name ?: groupName 
        ?: data?.clubName ?: data?.club_name ?: data?.groupName 
        ?: user?.clubName ?: user?.club_name ?: user?.groupName
    
    val camera = cameraEnabled ?: data?.cameraEnabled ?: user?.cameraEnabled ?: true
    val tracking = trackingEnabled ?: data?.trackingEnabled ?: user?.trackingEnabled ?: true

    return ClubDetails(
        clubName = name ?: "Club information unavailable",
        cameraEnabled = camera,
        trackingEnabled = tracking
    )
}
