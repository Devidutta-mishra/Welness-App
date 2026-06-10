package com.example.yourswelnes.core.tracking

/**
 * Device-specific onboarding copy for the consolidated OEM background-activity step of the
 * permission wizard.
 *
 * Each supported manufacturer family gets a tailored "why we need this" justification and an
 * exact, numbered set of on-screen instructions worded with the labels the user will actually
 * see in their phone's settings — MIUI "Battery Saver" / "Autostart", ColorOS "App Battery
 * Management", One UI "Never sleeping apps", FuntouchOS "High Background Power Usage" — instead
 * of generic Android wording. The first step always reads "Tap 'Configure Settings' below" so it
 * matches the wizard's primary button label exactly.
 *
 * Stock Android families (Pixel, Motorola, Nothing, generic AOSP) return [hasGuidance] = false:
 * the standard battery-optimization exemption already covers them cleanly, so the wizard skips
 * the OEM step entirely (see PermissionWizardViewModel.buildStepList).
 *
 * This is PRESENTATION ONLY. It never launches an intent, checks a permission, or touches the
 * tracking pipeline — launch/verify logic stays in [OemProfile] and PermissionChecker.
 */
data class OemInstructions(
    val manufacturer: OemManufacturer,
    val title: String,
    val whyText: String,
    val steps: List<String>,
    val hasGuidance: Boolean
)

object OEMInstructionProvider {

    /** Reads [android.os.Build] live (via [detectOemManufacturer]) and returns matching copy. */
    fun current(): OemInstructions = forManufacturer(detectOemManufacturer())

    fun forManufacturer(manufacturer: OemManufacturer): OemInstructions = when (manufacturer) {

        OemManufacturer.VIVO -> OemInstructions(
            manufacturer = manufacturer,
            title = "Device Optimization",
            whyText = "To ensure uninterrupted attendance validation.",
            steps = listOf(
                "Tap 'Configure Settings' below.",
                "Select High Background Power Usage.",
                "Turn ON Autostart for this app.",
                "Return and tap Complete Setup."
            ),
            hasGuidance = true
        )

        OemManufacturer.XIAOMI -> OemInstructions(
            manufacturer = manufacturer,
            title = "Device Optimization",
            whyText = "To ensure uninterrupted attendance validation.",
            steps = listOf(
                "Tap 'Configure Settings' below.",
                "Set Battery Saver to No Restrictions.",
                "Turn ON Autostart for this app.",
                "Return and tap Complete Setup."
            ),
            hasGuidance = true
        )

        // ColorOS is shared by OPPO and Realme — same screens, same wording.
        OemManufacturer.OPPO, OemManufacturer.REALME -> OemInstructions(
            manufacturer = manufacturer,
            title = "Device Optimization",
            whyText = "To ensure uninterrupted attendance validation.",
            steps = listOf(
                "Tap 'Configure Settings' below.",
                "Open Battery Usage / App Info.",
                "Enable Allow background activity and Auto-launch.",
                "Return and tap Complete Setup."
            ),
            hasGuidance = true
        )

        OemManufacturer.SAMSUNG -> OemInstructions(
            manufacturer = manufacturer,
            title = "Device Optimization",
            whyText = "To ensure uninterrupted attendance validation.",
            steps = listOf(
                "Tap 'Configure Settings' below.",
                "Go to Background usage limits.",
                "Add this app to Never sleeping apps.",
                "Return and tap Complete Setup."
            ),
            hasGuidance = true
        )

        // OnePlus / OxygenOS was not enumerated in the UX spec
        OemManufacturer.ONEPLUS -> OemInstructions(
            manufacturer = manufacturer,
            title = "Device Optimization",
            whyText = "To ensure uninterrupted attendance validation.",
            steps = listOf(
                "Tap 'Configure Settings' below.",
                "Set Battery to 'Don't optimize'.",
                "Turn ON 'Allow auto-launch'.",
                "Return and tap Complete Setup."
            ),
            hasGuidance = true
        )

        // Pixel / Motorola / Nothing / generic AOSP — standard battery-optimization exemption is
        // sufficient. No proprietary background registry, so the wizard skips this step entirely.
        OemManufacturer.GOOGLE,
        OemManufacturer.MOTOROLA,
        OemManufacturer.NOTHING,
        OemManufacturer.GENERIC -> OemInstructions(
            manufacturer = manufacturer,
            title = "",
            whyText = "",
            steps = emptyList(),
            hasGuidance = false
        )
    }
}
