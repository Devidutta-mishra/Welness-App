package com.example.yourswelnes.feature.monitoring.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.data.local.room.dao.AppMonitoringDao
import com.example.yourswelnes.data.local.room.entity.AppMonitoringEntity
import com.example.yourswelnes.feature.monitoring.data.remote.api.AppMonitoringApi
import com.example.yourswelnes.feature.monitoring.data.remote.dto.AppStatusUploadRequest
import com.example.yourswelnes.feature.monitoring.data.remote.dto.UpdatedAppDto
import com.example.yourswelnes.feature.monitoring.domain.model.AppStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@Singleton
class AppMonitoringRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appMonitoringApi: AppMonitoringApi,
    private val appMonitoringDao: AppMonitoringDao,
    private val authPreferences: AuthPreferencesDataStore
) : AppMonitoringRepository {

    override fun getApps(): Flow<List<AppStatus>> =
        appMonitoringDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun syncApps(): Result<Unit> = runCatching {
        // 1. Fetch required app list from backend
        val response = appMonitoringApi.getAppDownloadList()
        if (response.success != true) throw Exception("Failed to fetch app list from backend")
        val appDtos = response.appDownloads ?: run {
            Timber.d("App download list is empty — nothing to sync")
            return@runCatching
        }

        // 2. For each app, extract package name and check PackageManager
        val current = appDtos.map { dto ->
            val packageName = extractPackageName(dto.downloadLink)
            val isInstalled = packageName != null && isPackageInstalled(packageName)
            AppStatus(
                appId = dto.id,
                appName = dto.appName,
                downloadLink = dto.downloadLink,
                packageName = packageName,
                isInstalled = isInstalled
            )
        }

        // 3. Load last-known statuses from Room
        val cached = appMonitoringDao.getAllOnce().associateBy { it.appId }

        // 4. Detect any change: new app, removed app, or isInstalled flipped
        val currentIds = current.map { it.appId }.toSet()
        val hasChanges = current.any { s ->
            val c = cached[s.appId]
            c == null || c.isInstalled != s.isInstalled
        } || cached.keys.any { id -> id !in currentIds }

        if (!hasChanges) {
            Timber.d("App statuses unchanged — skipping upload and DB write")
            return@runCatching
        }

        // 5. Upload to backend only when something changed
        val user = authPreferences.cachedUser.firstOrNull()
            ?: throw Exception("User session not found.")
        val userId = user.id.toIntOrNull()
            ?: throw Exception("Invalid user ID.")

        val uploadResponse = appMonitoringApi.storeAppStatuses(
            AppStatusUploadRequest(
                userId = userId,
                updatedApps = current.map { UpdatedAppDto(it.appId, if (it.isInstalled) 1 else 0) }
            )
        )
        Timber.i("App status uploaded: success=${uploadResponse.success}")

        // 6. Persist latest state to Room
        appMonitoringDao.replaceAll(current.map { it.toEntity() })
    }.onFailure { Timber.e(it, "AppMonitoringRepository.syncApps failed") }

    private fun extractPackageName(downloadLink: String): String? =
        runCatching { Uri.parse(downloadLink).getQueryParameter("id") }.getOrNull()

    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}

private fun AppMonitoringEntity.toDomain() = AppStatus(
    appId = appId,
    appName = appName,
    downloadLink = downloadLink,
    packageName = packageName,
    isInstalled = isInstalled
)

private fun AppStatus.toEntity() = AppMonitoringEntity(
    appId = appId,
    appName = appName,
    downloadLink = downloadLink,
    packageName = packageName,
    isInstalled = isInstalled
)
