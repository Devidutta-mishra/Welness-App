package com.example.yourswelnes.feature.dashboard.data

interface DashboardRepository {

    /**
     * Fetches a short-lived authenticated URL for the dashboard web portal.
     * Returns [Result.success] with the URL on success, or [Result.failure] with a
     * user-presentable error message on any failure.
     */
    suspend fun getDashboardUrl(): Result<String>
}
