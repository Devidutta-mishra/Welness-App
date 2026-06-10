package com.example.yourswelnes.core.tracking

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * Maps [Build.MANUFACTURER] / [Build.BRAND] to a known [OemManufacturer]. Shared by
 * [detectOemProfile] and [OEMInstructionProvider] so device detection lives in exactly one place.
 */
fun detectOemManufacturer(): OemManufacturer {
    val rawManufacturer = Build.MANUFACTURER.lowercase().trim()
    val rawBrand = Build.BRAND.lowercase().trim()
    return when {
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
}

fun detectOemProfile(packageName: String): OemProfile {
    Timber.tag(TAG).i(
        "OEM DETECTED | manufacturer='${Build.MANUFACTURER}' brand='${Build.BRAND}' model='${Build.MODEL}'"
    )

    val manufacturer = detectOemManufacturer()

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
                // FuntouchOS / iQOO moved these screens between versions. Try the newest
                // (iManager — com.iqoo.secure) targets first, then the older
                // permission-manager components, and only then fall back to the app's details
                // page. launchOemIntent tries each in order and catches
                // ActivityNotFoundException / SecurityException, so a component that does not
                // exist on a given FuntouchOS build is skipped silently instead of crashing —
                // and we never land on the wrong (battery-optimization) screen.
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    ),
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                        )
                    ),
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    ),
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
            title = "Allow Background Activity",
            description = "Permit background data usage for continuous location tracking.",
            actionLabel = "Open Background Settings",
            launchAction = { ctx ->
                // Same robust chain — the Background Activity screen lives in the same
                // FuntouchOS surfaces, so reuse the newest-to-oldest ordering.
                ctx.launchOemIntent(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                        )
                    ),
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewTabActivity"
                        )
                    ),
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

internal fun appDetailsIntent(ctx: Context, packageName: String) =
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

private fun oemComponent(pkg: String, cls: String): Intent =
    Intent().setComponent(ComponentName(pkg, cls))

/**
 * Ordered list of the PROPRIETARY OEM auto-start / background screens for [manufacturer], newest
 * component first. Deliberately EXCLUDES the generic App Info page — callers append that as the
 * final fallback via [launchOemIntent].
 *
 * Two callers, one source of truth:
 *  1. The consolidated wizard OEM step launches `launchOemIntent(*these, fallback = appDetails)`,
 *     so a real OEM screen is preferred and App Info is only the last resort.
 *  2. [resolvesAnyActivity] probes this list to answer "does a real OEM screen exist on THIS
 *     device?". If none resolve, the step could only open App Info — see the Resolve-Before-Show
 *     gating in PermissionWizardViewModel.buildStepList.
 *
 * Component names mirror those used in [buildOemSteps] (which still backs the standalone
 * TrackingSetupScreen); keep the two in sync if a vendor moves a screen between OS versions.
 */
internal fun oemBackgroundIntents(manufacturer: OemManufacturer, packageName: String): List<Intent> =
    when (manufacturer) {
        OemManufacturer.XIAOMI -> listOf(
            oemComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.appdetail.AppDetailActivity")
                .putExtra("extra_pkgname", packageName)
        )
        OemManufacturer.VIVO -> listOf(
            oemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            oemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            oemComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            oemComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
        )
        OemManufacturer.OPPO -> listOf(
            oemComponent("com.coloros.safeguard", "com.coloros.safeguard.autostart.StartupAppListActivity"),
            oemComponent("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
        )
        OemManufacturer.REALME -> listOf(
            oemComponent("com.coloros.safeguard", "com.coloros.safeguard.autostart.StartupAppListActivity")
        )
        OemManufacturer.ONEPLUS -> listOf(
            oemComponent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )
        OemManufacturer.SAMSUNG -> listOf(
            oemComponent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        // Pixel / Motorola / Nothing / generic AOSP have no proprietary background registry.
        else -> emptyList()
    }

/**
 * True if at least one of [intents] resolves to an installed activity on this device.
 *
 * Used as the Resolve-Before-Show probe: every OEM settings package is declared in the manifest
 * <queries> block (QUERY_ALL_PACKAGES was removed for Play-policy compliance), so an
 * explicit-component intent to an OEM settings activity resolves only when that screen actually
 * exists on the current ROM. When every probe returns null the wizard knows the OEM step could do
 * nothing but reopen the generic App Info page. Adding a new OEM component above requires its
 * package to be added to the manifest <queries> block too, or it will never resolve.
 */
internal fun Context.resolvesAnyActivity(intents: List<Intent>): Boolean =
    intents.any { intent ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L)) != null
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0) != null
        }
    }
