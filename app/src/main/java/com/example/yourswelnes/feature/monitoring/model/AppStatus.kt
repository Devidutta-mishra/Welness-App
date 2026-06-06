package com.example.yourswelnes.feature.monitoring.model

data class AppStatus(
    val appId: Int,
    val appName: String,
    val downloadLink: String,
    val packageName: String?,
    val isInstalled: Boolean
)
