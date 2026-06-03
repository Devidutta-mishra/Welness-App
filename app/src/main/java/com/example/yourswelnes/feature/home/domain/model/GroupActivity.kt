package com.example.yourswelnes.feature.home.domain.model

import java.time.LocalTime

data class ActivitySlot(
    val groupId: Long,
    val groupName: String,
    val keywordId: Int,
    val keyword: String,
    val pointValue: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)
