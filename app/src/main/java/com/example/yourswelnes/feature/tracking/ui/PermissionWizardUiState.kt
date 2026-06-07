package com.example.yourswelnes.feature.tracking.ui

import com.example.yourswelnes.core.tracking.OemSetupStep

/**
 * Wizard step ordering, matching the business flow for this location-tracking app:
 *   Location → Background Location → Notification → Battery Optimization → OEM Setup
 *
 * The first four are mandatory and reliably verifiable, so each blocks progression until met.
 * OEM steps come last because they cannot be verified by Android — they are guidance only and
 * never block the user from reaching Home.
 *
 * This app collects location only; it requests NO activity-recognition, step-counter, motion,
 * or other fitness permissions — none are required by the tracking pipeline.
 */
enum class WizardStepType {
    FINE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    OEM_STEP
}

data class WizardStep(
    val id: String,
    val type: WizardStepType,
    val title: String,
    val bodyText: String,
    val noteText: String? = null,
    val primaryButtonLabel: String,
    val isMandatory: Boolean,
    val isGranted: Boolean = false,
    val oemStep: OemSetupStep? = null
)

data class PermissionWizardUiState(
    val steps: List<WizardStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val isLoading: Boolean = true
) {
    val currentStep: WizardStep? get() = steps.getOrNull(currentStepIndex)
    val totalSteps: Int get() = steps.size
    val completedCount: Int get() = steps.count { it.isGranted }
}
