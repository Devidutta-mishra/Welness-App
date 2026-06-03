package com.example.yourswelnes.feature.home.presentation

import com.example.yourswelnes.feature.home.domain.model.ActivitySlot

data class GroupScheduleUiState(
    val isLoading: Boolean = false,
    val currentActivity: ActivitySlot? = null,
    val nextActivity: ActivitySlot? = null,
    val error: String? = null
)
