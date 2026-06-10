package com.example.yourswelnes.feature.monitoring.data

import android.content.Context
import android.content.pm.PackageManager
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.database.dao.AppMonitoringDao
import com.example.yourswelnes.core.database.entity.AppMonitoringEntity
import com.example.yourswelnes.feature.monitoring.data.api.AppMonitoringApi
import com.example.yourswelnes.feature.monitoring.data.dto.AppStatusUploadRequest
import com.example.yourswelnes.feature.monitoring.data.dto.UpdatedAppDto
import com.example.yourswelnes.feature.monitoring.model.AppStatus
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

        // 2. Resolve each backend app id to its explicitly declared package and check it.
        //    Post-QUERY_ALL_PACKAGES-removal, only packages listed in the manifest <queries>
        //    block are visible — an undeclared package ALWAYS reports "not installed" — so the
        //    id→package mapping is a static allowlist instead of being parsed from the download
        //    link. Ids missing from the map are reported not-installed and logged loudly so a
        //    backend-side addition can't silently produce false data.
        val current = appDtos.map { dto ->
            val packageName = KNOWN_APP_PACKAGES[dto.id]
            if (packageName == null) {
                Timber.w(
                    "App id=%d (%s) has no declared package mapping — reporting not installed. " +
                    "Add it to KNOWN_APP_PACKAGES and the manifest <queries> block.",
                    dto.id, dto.appName
                )
            }
            AppStatus(
                appId = dto.id,
                appName = dto.appName,
                downloadLink = dto.downloadLink,
                packageName = packageName,
                isInstalled = packageName != null && isPackageInstalled(packageName)
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

    /** Visibility-filtered existence check — only succeeds for packages declared in <queries>. */
    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private companion object {
        /**
         * Backend app id → Android package, for every app the backend may ask us to monitor.
         * MUST stay in lockstep with the manifest <queries> block: a package missing there is
         * invisible to [isPackageInstalled] (Android 11+ package-visibility filtering) and would
         * silently report "not installed" on every device.
         */
        val KNOWN_APP_PACKAGES = mapOf(
            1 to "org.telegram.messenger",
            2 to "us.zoom.videomeetings"
        )
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
