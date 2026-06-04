package com.example.yourswelnes.feature.home.domain.model

data class Group(
    val groupId: Long,
    val groupName: String,
    val activities: List<ActivitySlot>
)
