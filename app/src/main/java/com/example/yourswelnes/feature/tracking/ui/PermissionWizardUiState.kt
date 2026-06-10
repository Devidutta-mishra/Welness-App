package com.example.yourswelnes.feature.tracking.ui

import com.example.yourswelnes.core.tracking.OemSetupStep

/**
 * Wizard step ordering, matching the business flow for this location-tracking app:
 *   Location → Background Location → Notification → Battery Optimization → Exact Alarms → OEM Setup
 *
 * The first five are reliably verifiable, so each is checked on return. OEM steps come last
 * because they cannot be verified by Android — they are guidance only and never block the user
 * from reaching Home.
 *
 * This app collects location only; it requests NO activity-recognition, step-counter, motion,
 * or other fitness permissions — none are required by the tracking pipeline.
 */
enum class WizardStepType {
    FINE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    EXACT_ALARM,
    OEM_STEP
}

data class WizardStep(
    val id: String,
    val type: WizardStepType,
    val title: String,
    val bodyText: String,
    val noteText: String? = null,
    // Ordered, device-tailored "do this in Settings" guidance, rendered as a numbered list under
    // the body. Populated only for the consolidated OEM step (from OEMInstructionProvider); empty
    // for the verifiable Android permission steps, which need no manual instructions.
    val numberedSteps: List<String> = emptyList(),
    val primaryButtonLabel: String,
    val isMandatory: Boolean,
    val isGranted: Boolean = false,
    val oemStep: OemSetupStep? = null
)

data class PermissionWizardUiState(
    val steps: List<WizardStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val isLoading: Boolean = true,
    // Becomes true once the user has opened the battery settings screen at least once.
    // Reveals the manual "I Have Done This" fallback on the battery step so users on OEM
    // builds where isIgnoringBatteryOptimizations() never flips to true are not trapped.
    val batteryManualConfirmAvailable: Boolean = false,
    // Same fallback for the exact-alarm step: revealed once the user has opened the
    // "Alarms & reminders" settings screen, so a user who declines (or whose device delays the
    // toggle) can still proceed — the app degrades to an inexact Doze alarm rather than trapping.
    val exactAlarmManualConfirmAvailable: Boolean = false
) {
    val currentStep: WizardStep? get() = steps.getOrNull(currentStepIndex)
    val totalSteps: Int get() = steps.size
    val completedCount: Int get() = steps.count { it.isGranted }
}
