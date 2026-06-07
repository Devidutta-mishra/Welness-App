package com.example.yourswelnes.core.tracking

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

private const val TAG = "OemProfile"

enum class OemManufacturer {
    GOOGLE, SAMSUNG, XIAOMI, OPPO, REALME, VIVO, ONEPLUS, MOTOROLA, NOTHING, GENERIC
}

data class OemSetupStep(
    val title: String,
    val description: String,
    val actionLabel: String,
    val isManual: Boolean = false,
    val launchAction: (Context) -> Unit = {}
)

data class OemProfile(
    val manufacturer: OemManufacturer,
    val displayName: String,
    val steps: List<OemSetupStep>
)

fun detectOemProfile(packageName: String): OemProfile {
    val rawManufacturer = Build.MANUFACTURER.lowercase().trim()
    val rawBrand = Build.BRAND.lowercase().trim()
    Timber.tag(TAG).i(
        "OEM DETECTED | manufacturer='$rawManufacturer' brand='$rawBrand' model='${Build.MODEL}'"
    )

    val manufacturer = when {
        rawManufacturer == "google" -> OemManufacturer.GOOGLE
        rawManufacturer == "samsung" -> OemManufacturer.SAMSUNG
        rawManufacturer.contains("xiaomi") || rawBrand.contains("xiaomi") ||
            rawBrand.contains("redmi") || rawBrand.contains("poco") -> OemManufacturer.XIAOMI
        rawManufacturer.contains("oppo") -> OemManufacturer.OPPO
        rawManufacturer.contains("realme") || rawBrand.contains("realme") -> OemManufacturer.REALME
        rawManufacturer.contains("vivo") || rawBrand.contains("vivo") -> OemManufacturer.VIVO
        rawManufacturer.contains("oneplus") || rawBrand.contains("oneplus") -> OemManufacturer.ONEPLUS
        rawManufacturer.contains("motorola") || rawManufacturer.contains("moto") -> OemManufacturer.MOTOROLA
        rawManufacturer.contains("nothing") -> OemManufacturer.NOTHING
        else -> OemManufacturer.GENERIC
    }

    val displayName = when (manufacturer) {
        OemManufacturer.XIAOMI  -> "Xiaomi / Redmi / POCO"
        OemManufacturer.OPPO    -> "OPPO / ColorOS"
        OemManufacturer.REALME  -> "Realme"
        OemManufacturer.VIVO    -> "Vivo / FuntouchOS"
        OemManufacturer.ONEPLUS -> "OnePlus / OxygenOS"
        OemManufacturer.SAMSUNG -> "Samsung / One UI"
        OemManufacturer.GOOGLE  -> "Google Pixel"
        OemManufacturer.MOTOROLA -> "Motorola"
        OemManufacturer.NOTHING -> "Nothing"
        OemManufacturer.GENERIC -> "Android"
    }

    val steps = buildOemSteps(manufacturer, packageName)
    Timber.tag(TAG).i("OEM DETECTED | profile=$displayName steps=${steps.size}")
    return OemProfile(manufacturer, displayName, steps)
}

private fun buildOemSteps(
    manufacturer: OemManufacturer,
    packageName: String
): List<OemSetupStep> = when (manufacturer) {

    OemManufacturer.XIAOMI -> listOf(
        OemSetupStep(
            title = "Enable Auto Start",
            description = "Allow the app to start automatically after the device boots.",
            actionLabel = "Open Auto Start",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Allow Background Activity",
            description = "Prevent MIUI / HyperOS from restricting background app activity.",
            actionLabel = "Open App Permissions",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.appdetail.AppDetailActivity"
                        )
                        .putExtra("extra_pkgname", packageName),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Lock App in Recents",
            description = "Open recent apps, find this app, and tap the padlock icon to prevent it being cleared.",
            actionLabel = "Manual Step",
            isManual = true
        )
    )

    OemManufacturer.VIVO -> listOf(
        OemSetupStep(
            title = "Enable Auto Start",
            description = "Allow the app to start automatically in the background.",
            actionLabel = "Open Auto Start",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Allow Background Activity",
            description = "Permit background data usage for continuous location tracking.",
            actionLabel = "Open Background Settings",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewTabActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Allow High Background Power",
            description = "Enable high background power usage to prevent tracking interruption.",
            actionLabel = "Open Battery Settings",
            launchAction = { ctx ->
                ctx.launchOemIntent(appDetailsIntent(ctx, packageName))
            }
        )
    )

    OemManufacturer.OPPO -> listOf(
        OemSetupStep(
            title = "Enable Auto Start",
            description = "Allow the app to start automatically after device reboot.",
            actionLabel = "Open Auto Start",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safeguard",
                            "com.coloros.safeguard.autostart.StartupAppListActivity"
                        )
                    ),
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.oppoguardelf",
                            "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Allow Background Activity",
            description = "Prevent ColorOS from restricting background activity for this app.",
            actionLabel = "Open App Settings",
            launchAction = { ctx -> ctx.launchOemIntent(appDetailsIntent(ctx, packageName)) }
        )
    )

    OemManufacturer.REALME -> listOf(
        OemSetupStep(
            title = "Enable Auto Start",
            description = "Allow the app to start automatically after device reboot.",
            actionLabel = "Open Auto Start",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safeguard",
                            "com.coloros.safeguard.autostart.StartupAppListActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Allow Background Activity",
            description = "Prevent realme UI from restricting background activity for this app.",
            actionLabel = "Open App Settings",
            launchAction = { ctx -> ctx.launchOemIntent(appDetailsIntent(ctx, packageName)) }
        )
    )

    OemManufacturer.ONEPLUS -> listOf(
        OemSetupStep(
            title = "Allow Background Activity",
            description = "Prevent OxygenOS from restricting background app activity.",
            actionLabel = "Open Auto-Launch",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        ),
        OemSetupStep(
            title = "Remove Battery Restrictions",
            description = "Set this app's battery usage to \"Don't optimize\" so OxygenOS keeps " +
                "tracking alive when the screen is off.",
            actionLabel = "Open Battery Settings",
            launchAction = { ctx -> ctx.launchOemIntent(appDetailsIntent(ctx, packageName)) }
        )
    )

    // Samsung One UI aggressively sleeps background apps via "Put unused apps to sleep" and
    // the "Sleeping apps" list in Device Care. There is no reliable verifiable API, so this is
    // guidance that deep-links to the app's settings where Battery → Unrestricted can be set.
    OemManufacturer.SAMSUNG -> listOf(
        OemSetupStep(
            title = "Allow Unrestricted Battery",
            description = "Open this app's info, tap Battery, and choose \"Unrestricted\" so One UI " +
                "never puts tracking to sleep. Also turn OFF \"Put app to sleep\" if shown.",
            actionLabel = "Open App Battery Settings",
            launchAction = { ctx ->
                ctx.launchOemIntent(
                    // Samsung Device Care app-power management screen (One UI), falls back to
                    // the standard app-details page where the Battery option is always present.
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    ),
                    fallback = appDetailsIntent(ctx, packageName)
                )
            }
        )
    )

    // Google Pixel, Motorola, Nothing, Generic:
    // Stock Android — the standard battery optimization exemption (a mandatory wizard step)
    // is sufficient. No extra OEM steps.
    else -> emptyList()
}

private fun appDetailsIntent(ctx: Context, packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Tries each [intents] in order, stopping at the first that launches successfully.
 * Falls back to [fallback] if all fail. Never throws.
 */
fun Context.launchOemIntent(vararg intents: Intent, fallback: Intent? = null) {
    val candidates = if (fallback != null) intents.toList() + fallback else intents.toList()
    for (intent in candidates) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try next
        } catch (e: Exception) {
            Timber.tag(TAG).w("Intent launch failed (${intent.component}): ${e.message}")
        }
    }
    Timber.tag(TAG).w("All OEM intents exhausted — no activity found")
}

/** Opens the standard battery optimization request dialog for this package. */
fun openBatteryOptimizationSettings(ctx: Context, packageName: String) {
    ctx.launchOemIntent(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
