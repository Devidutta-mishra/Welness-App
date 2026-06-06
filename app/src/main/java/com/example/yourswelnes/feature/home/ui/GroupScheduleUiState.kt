package com.example.yourswelnes.feature.home.ui

import com.example.yourswelnes.feature.home.model.ActivitySlot

data class GroupScheduleUiState(
    val isLoading: Boolean = false,
    val currentActivity: ActivitySlot? = null,
    val nextActivity: ActivitySlot? = null,
    val error: String? = null
)
