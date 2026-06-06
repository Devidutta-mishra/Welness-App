package com.example.yourswelnes.feature.home.data.mapper

import com.example.yourswelnes.feature.home.data.dto.GroupDetailsResponse
import com.example.yourswelnes.feature.home.model.ActivitySlot
import com.example.yourswelnes.feature.home.model.Group
import java.time.LocalTime
import timber.log.Timber

fun GroupDetailsResponse.toGroups(): List<Group> =
    groups?.map { groupDto ->
        val activities = groupDto.messages?.mapNotNull { msg ->
            runCatching {
                ActivitySlot(
                    groupId = groupDto.groupId,
                    groupName = groupDto.groupName,
                    keywordId = msg.keywordId,
                    keyword = msg.keyword,
                    pointValue = msg.pointValue,
                    startTime = LocalTime.parse(msg.startTime),
                    endTime = LocalTime.parse(msg.endTime)
                )
            }.onFailure {
                Timber.w("Skipping malformed slot in group '${groupDto.groupName}': ${it.message}")
            }.getOrNull()
        } ?: emptyList()
        Group(groupId = groupDto.groupId, groupName = groupDto.groupName, activities = activities)
    } ?: emptyList()

fun GroupDetailsResponse.toActivitySlots(): List<ActivitySlot> =
    toGroups().flatMap { it.activities }
