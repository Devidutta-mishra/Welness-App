package com.example.yourswelnes.feature.home.data.remote.mapper

import com.example.yourswelnes.feature.home.data.remote.dto.GroupDetailsResponse
import com.example.yourswelnes.feature.home.domain.model.ActivitySlot
import java.time.LocalTime
import timber.log.Timber

fun GroupDetailsResponse.toActivitySlots(): List<ActivitySlot> =
    groups?.flatMap { group ->
        group.messages?.mapNotNull { msg ->
            runCatching {
                ActivitySlot(
                    groupId = group.groupId,
                    groupName = group.groupName,
                    keywordId = msg.keywordId,
                    keyword = msg.keyword,
                    pointValue = msg.pointValue,
                    startTime = LocalTime.parse(msg.startTime),
                    endTime = LocalTime.parse(msg.endTime)
                )
            }.onFailure {
                Timber.w("Skipping malformed slot in group '${group.groupName}': ${it.message}")
            }.getOrNull()
        } ?: emptyList()
    } ?: emptyList()
