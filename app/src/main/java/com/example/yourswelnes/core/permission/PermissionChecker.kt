package com.example.yourswelnes.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all runtime permission and system exemption checks.
 *
 * Every ViewModel that previously duplicated `ActivityCompat.checkSelfPermission()` calls
 * should inject this class instead. This eliminates divergence between what different screens
 * think the permission state is, which was a root cause of the "permission still missing"
 * symptom reported after the user had already granted permissions.
 *
 * All methods query the system directly — no caching. PowerManager and PackageManager are
 * already fast in-process calls; caching would only introduce staleness bugs.
 */
@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasFineLocation(): Boolean =
        check(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Background location was added as a separate runtime permission in Android Q (API 29).
     * On older devices the coarse/fine grant implicitly covers background use.
     */
    fun hasBackgroundLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            check(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else true

    /**
     * POST_NOTIFICATIONS became a runtime permission in Android 13 (API 33 / TIRAMISU).
     * Pre-13: all apps could post notifications without a grant; return true unconditionally.
     */
    fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            check(Manifest.permission.POST_NOTIFICATIONS)
        else true

    /**
     * Never cache this. On Xiaomi, Vivo, and Oppo devices, there is a 200–500 ms window after
     * the user returns from battery settings where this still returns false even though the
     * exemption was granted. Callers that need post-return accuracy should use
     * [BatteryOptimizationManager.checkAfterReturn] which adds the required delay.
     */
    fun isBatteryOptimizationExempt(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    private fun check(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
