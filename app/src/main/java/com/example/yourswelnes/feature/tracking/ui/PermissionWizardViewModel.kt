package com.example.yourswelnes.feature.tracking.ui

import android.content.Context
import com.example.yourswelnes.core.datastore.PermissionOnboardingDataStore
import com.example.yourswelnes.core.permission.BatteryOptimizationManager
import com.example.yourswelnes.core.permission.PermissionChecker
import com.example.yourswelnes.core.tracking.OEMInstructionProvider
import com.example.yourswelnes.core.tracking.OemSetupStep
import com.example.yourswelnes.core.tracking.appDetailsIntent
import com.example.yourswelnes.core.tracking.detectOemProfile
import com.example.yourswelnes.core.tracking.launchOemIntent
import com.example.yourswelnes.core.tracking.oemBackgroundIntents
import com.example.yourswelnes.core.tracking.resolvesAnyActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class PermissionWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val batteryOptimizationManager: BatteryOptimizationManager,
    private val onboardingDataStore: PermissionOnboardingDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionWizardUiState())
    val uiState: StateFlow<PermissionWizardUiState> = _uiState.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch { loadAndStart() }
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Builds the step list from the LIVE permission state every time the wizard is entered.
     *
     * The wizard is only ever entered when a mandatory requirement is actually missing
     * (AppNavGraph redirects on LocationUiState.anyRequirementMissing). Once setup is complete
     * — all four mandatory requirements granted — the wizard is never entered again, so OEM
     * steps cannot "reappear on every launch". We therefore do NOT persist per-step dismissal:
     * persisting it caused OEM steps to be skipped forever after the first interaction, which
     * is the opposite problem (the user could never reach "Allow Background Activity" again).
     */
    private suspend fun loadAndStart() {
        val steps = buildStepList()
        Timber.tag("PermWizard").i(
            "WIZARD INIT | steps=${steps.size} mandatory=${steps.count { it.isMandatory }}"
        )
        if (steps.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            onboardingDataStore.setOnboardingCompleted()
            _done.send(Unit)
        } else {
            _uiState.update {
                PermissionWizardUiState(steps = steps, currentStepIndex = 0, isLoading = false)
            }
        }
    }

    // ── Resume handling ───────────────────────────────────────────────────────

    /**
     * Called by:
     *  - The DisposableEffect ON_RESUME observer (user returns from ANY settings screen)
     *  - The ActivityResultLauncher callback for battery settings (always fires on return)
     *
     * ── Battery step special handling ──────────────────────────────────────────
     * Battery optimization uses [BatteryOptimizationManager.checkAfterReturn] which
     * introduces a 400ms delay before querying PowerManager. This delay exists because
     * isIgnoringBatteryOptimizations() is NOT updated atomically on Xiaomi/Vivo/Oppo —
     * it can return false for up to 400ms after the user has already exempted the app.
     * Without this delay, the wizard stays stuck on the battery step and the user
     * re-opens settings — the exact infinite loop that was reported.
     *
     * ── Other steps ────────────────────────────────────────────────────────────
     * For Android runtime permissions (location, notification, activity recognition),
     * PackageManager updates synchronously when the user taps Allow/Deny. No delay needed.
     */
    fun onResumeRefresh() {
        val step = _uiState.value.currentStep ?: return

        // OEM steps (Vivo Auto Start / Background Activity, MIUI, etc.) are NOT verifiable —
        // Android cannot read these proprietary settings. Never check or auto-advance them on
        // resume; returning from the OEM settings screen must leave wizard state untouched.
        // The user confirms completion explicitly via the "I Have Done This" button (advance()).
        // Standard Android permissions and battery optimization below remain strictly verified.
        if (step.type == WizardStepType.OEM_STEP) return

        if (step.type == WizardStepType.BATTERY_OPTIMIZATION) {
            viewModelScope.launch {
                val isGranted = batteryOptimizationManager.checkAfterReturn()
                processStepResult(step, isGranted)
            }
        } else {
            processStepResult(step, checkStepGranted(step.type))
        }
    }

    // ── Explicit advance ──────────────────────────────────────────────────────

    /**
     * Called for:
     *  - "Skip for Now" on recommended steps (OEM)
     *  - "I've Done This" on manual OEM steps
     *  - Primary button on OEM non-manual steps (the button opens settings; advance is explicit)
     *
     * Simply moves to the next step. We intentionally do NOT persist per-step dismissal —
     * see [loadAndStart] for why.
     */
    fun advance() {
        advanceInternal()
    }

    /**
     * Called when the user opens the battery-optimization settings screen. Reveals the manual
     * "I Have Done This" fallback so they are not permanently blocked on OEM builds where
     * isIgnoringBatteryOptimizations() does not reflect the device's own battery toggle.
     * The verified auto-advance path still wins: if the system reports the exemption on return,
     * the step advances automatically and this fallback button is never needed.
     */
    fun onBatterySettingsOpened() {
        _uiState.update { it.copy(batteryManualConfirmAvailable = true) }
    }

    /**
     * Manual bypass for the battery step — only reachable after the user has visited the
     * settings screen (the button is hidden until then). Advances past battery on the user's
     * assertion that they enabled it, for devices where the system API cannot confirm it.
     * Standard runtime permissions and the verified battery auto-advance are unaffected.
     */
    fun confirmBatteryManually() {
        val step = _uiState.value.currentStep ?: return
        if (step.type != WizardStepType.BATTERY_OPTIMIZATION) return
        Timber.tag("BatteryOpt").i("BATTERY MANUAL CONFIRM | user-asserted exemption after visiting settings")
        advanceInternal()
    }

    /**
     * Reveals the manual "I Have Done This" fallback on the exact-alarm step once the user has
     * opened the Alarms & reminders screen. The verified auto-advance still wins: if
     * canScheduleExactAlarms() reports true on return, the step advances automatically.
     */
    fun onExactAlarmSettingsOpened() {
        _uiState.update { it.copy(exactAlarmManualConfirmAvailable = true) }
    }

    /**
     * Manual bypass for the exact-alarm step — only reachable after the user has visited the
     * settings screen. Lets a user who declines exact alarms proceed; the scheduler falls back to
     * an inexact Doze alarm (window may open a few minutes late). Verified auto-advance is unaffected.
     */
    fun confirmExactAlarmManually() {
        val step = _uiState.value.currentStep ?: return
        if (step.type != WizardStepType.EXACT_ALARM) return
        Timber.tag("PermWizard").i("EXACT ALARM MANUAL CONFIRM | proceeding with inexact-alarm fallback")
        advanceInternal()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun processStepResult(step: WizardStep, isGranted: Boolean) {
        val state = _uiState.value
        val current = state.currentStep

        // Stale / duplicate-callback guard. Both the ON_RESUME observer and the battery
        // ActivityResult callback call onResumeRefresh(); the battery path is delayed 400ms,
        // so two coroutines can resume against the same step. Without this guard the second
        // one would (a) overwrite the NEXT step's slot with a copy of this step after the
        // first advanced the index, and (b) send `done` twice on the last step. We ignore the
        // result if the wizard has already moved on, or this step is already marked granted.
        if (current == null || current.id != step.id || current.isGranted) {
            Timber.tag("PermWizard").d(
                "STALE RESULT | ignored ${step.id} (current=${current?.id} granted=${current?.isGranted})"
            )
            return
        }

        Timber.tag("PermWizard").i("RESUME | step=${step.type} granted=$isGranted")
        if (isGranted) {
            val updated = state.steps.toMutableList()
            updated[state.currentStepIndex] = step.copy(isGranted = true)
            _uiState.update { it.copy(steps = updated) }
            if (step.type == WizardStepType.BATTERY_OPTIMIZATION) {
                Timber.tag("BatteryOpt").i("BATTERY STEP COMPLETED")
            }
            // OEM steps have no API to verify — they require the user to explicitly tap
            // "I've Done This" which calls advance(). Auto-advance on all other types.
            if (step.type != WizardStepType.OEM_STEP) {
                advanceInternal()
            }
        } else if (step.type == WizardStepType.BATTERY_OPTIMIZATION) {
            Timber.tag("BatteryOpt").i("BATTERY STEP BLOCKED")
        }
    }

    private fun advanceInternal() {
        val state = _uiState.value
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex >= state.steps.size) {
            Timber.tag("PermWizard").i("WIZARD DONE")
            viewModelScope.launch {
                onboardingDataStore.setOnboardingCompleted()
                _done.send(Unit)
            }
        } else {
            Timber.tag("PermWizard").i(
                "ADVANCE | ${state.currentStep?.type} → ${state.steps[nextIndex].type}"
            )
            _uiState.update { it.copy(currentStepIndex = nextIndex) }
        }
    }

    private fun checkStepGranted(type: WizardStepType): Boolean = when (type) {
        WizardStepType.FINE_LOCATION -> permissionChecker.hasFineLocation()
        WizardStepType.BACKGROUND_LOCATION -> permissionChecker.hasBackgroundLocation()
        WizardStepType.NOTIFICATION -> permissionChecker.hasNotifications()
        WizardStepType.BATTERY_OPTIMIZATION -> batteryOptimizationManager.checkNow()
        WizardStepType.EXACT_ALARM -> permissionChecker.canScheduleExactAlarms()
        WizardStepType.OEM_STEP -> false
    }

    // ── Step list builder ─────────────────────────────────────────────────────

    /**
     * Builds the ordered list of steps the user still needs to complete.
     *
     * Order (business flow for a location-tracking app):
     *  1. Fine Location        — mandatory, verifiable, blocks progression
     *  2. Background Location   — mandatory, verifiable, requested only after fine is granted
     *  3. Notification          — mandatory, verifiable
     *  4. Battery Optimization  — verifiable via isIgnoringBatteryOptimizations(); manual
     *                             "I Have Done This" fallback for OEMs whose API lags the toggle
     *  5. Exact Alarms          — verifiable via canScheduleExactAlarms(); needed for the
     *                             Doze-proof window start. Manual fallback → inexact alarm if declined
     *  6. OEM background step   — ONE consolidated, device-tailored step (recommended only;
     *                             Android cannot verify it, so it NEVER blocks). Skipped for stock
     *                             devices, and skipped via Resolve-Before-Show gating when it could
     *                             only reopen App Info and battery is already exempt (see below).
     *
     * No activity-recognition / step-counter / fitness permission is requested — this app
     * only collects location.
     *
     * Every step reflects the LIVE permission state: only steps for missing items are added,
     * so a step the user has already satisfied never shows.
     */
    private fun buildStepList(): List<WizardStep> {
        val hasFine = permissionChecker.hasFineLocation()
        val hasBg = permissionChecker.hasBackgroundLocation()
        val hasNotif = permissionChecker.hasNotifications()
        val isBatteryExempt = permissionChecker.isBatteryOptimizationExempt()
        val oemProfile = detectOemProfile(context.packageName)

        Timber.tag("PermWizard").i(
            "STATE | fine=$hasFine bg=$hasBg notif=$hasNotif " +
            "battery=$isBatteryExempt oem=${oemProfile.displayName}"
        )

        val steps = mutableListOf<WizardStep>()

        if (!hasFine) steps += WizardStep(
            id = "fine_location",
            type = WizardStepType.FINE_LOCATION,
            title = "Club Check-in Automation",
            bodyText = "Verify club presence to automate your attendance logs so you never have to manually check in when arriving for your session.",
            primaryButtonLabel = "Allow Location Access",
            isMandatory = true
        )

        // PROMINENT DISCLOSURE (Google Play background-location policy). The body text below is
        // compliance-reviewed wording and must keep the literal phrases "collects location data",
        // "collected", and "even when the app is closed or not in use" — the location-permissions
        // declaration review checks for exactly these elements appearing BEFORE the runtime
        // permission request. Do not reword without re-clearing Play compliance.
        if (!hasBg) steps += WizardStep(
            id = "background_location",
            type = WizardStepType.BACKGROUND_LOCATION,
            title = "Automated Club Check-In",
            bodyText = "Yours Wellness Center collects location data to automate your attendance " +
                "logs and verify your presence at the club zone seamlessly. This location data is " +
                "collected and processed even when the app is closed or not in use. This data is " +
                "required to ensure your check-ins are recorded accurately without requiring " +
                "manual intervention.",
            noteText = "On the next screen, select \"Allow all the time\".",
            primaryButtonLabel = "Grant Access",
            isMandatory = true
        )

        if (!hasNotif) steps += WizardStep(
            id = "notification",
            type = WizardStepType.NOTIFICATION,
            title = "Stay Connected",
            bodyText = "We send real-time updates on your automated check-in status, attendance summaries, and important messages from your club.",
            primaryButtonLabel = "Enable Notifications",
            isMandatory = true
        )

        // Battery optimization — MANDATORY and verifiable.
        if (!isBatteryExempt) steps += WizardStep(
            id = "battery",
            type = WizardStepType.BATTERY_OPTIMIZATION,
            title = "Uninterrupted Automation",
            bodyText = "To prevent the Android operating system from pausing your automated check-ins when the phone is locked or deep sleeping.",
            primaryButtonLabel = "Allow Optimization",
            isMandatory = true
        )

        // Exact alarms
        if (!permissionChecker.canScheduleExactAlarms()) steps += WizardStep(
            id = "exact_alarm",
            type = WizardStepType.EXACT_ALARM,
            title = "Scheduled Hours",
            bodyText = "To begin validating your presence the moment your active booking window opens, this app needs permission to schedule precise automation tasks.",
            noteText = "On the next screen, turn on \"Allow setting alarms and reminders\".",
            primaryButtonLabel = "Allow Precise Timing",
            isMandatory = true
        )

        // OEM background-activity step — ONE consolidated, device-tailored step (recommended only,
        // Android cannot verify it). This replaces the old per-OEM chain that could send the user
        // to the App Info page multiple times. Three rules from the onboarding audit apply:
        //
        //  • Stock families (Pixel / Motorola / Nothing / AOSP) return hasGuidance=false → no step.
        //    The mandatory battery-optimization exemption above already covers them cleanly.
        //
        //  • Resolve-Before-Show gating (Strategy A): probe whether ANY proprietary OEM screen
        //    (auto-start / vendor battery manager) actually resolves on this device. If none do,
        //    the step could only reopen the generic App Info page — so if battery optimization is
        //    ALREADY exempt, the step is pure redundancy and is skipped.
        //
        //  • Keep genuine auto-start (Strategy B): if a proprietary screen resolves, the step is
        //    kept regardless of battery state, because that auto-start registry is a separate
        //    system that cannot be verified through any standard Android API.
        val oemInstructions = OEMInstructionProvider.forManufacturer(oemProfile.manufacturer)
        if (oemInstructions.hasGuidance) {
            val backgroundIntents = oemBackgroundIntents(oemProfile.manufacturer, context.packageName)
            val hasRealOemScreen = context.resolvesAnyActivity(backgroundIntents)
            val show = shouldShowOemStep(
                hasGuidance = true,
                hasRealOemScreen = hasRealOemScreen,
                isBatteryExempt = isBatteryExempt
            )

            Timber.tag("PermWizard").i(
                "OEM STEP | ${oemProfile.displayName} realScreen=$hasRealOemScreen " +
                "batteryExempt=$isBatteryExempt → ${if (show) "SHOW" else "SKIP"}"
            )

            if (show) {
                steps += WizardStep(
                    // Stable ID (no per-substep index) so the consolidated step is addressed once.
                    id = "oem_${oemProfile.manufacturer.name.lowercase()}",
                    type = WizardStepType.OEM_STEP,
                    title = oemInstructions.title,
                    bodyText = oemInstructions.whyText,
                    numberedSteps = oemInstructions.steps,
                    primaryButtonLabel = "Configure Settings",
                    isMandatory = false,
                    oemStep = OemSetupStep(
                        title = oemInstructions.title,
                        description = oemInstructions.whyText,
                        actionLabel = "Configure Settings",
                        isManual = false,
                        // Safety fallback (Strategy 3): launchOemIntent tries each proprietary OEM
                        // screen in order, catching ActivityNotFoundException AND SecurityException,
                        // and lands on App Info exactly once if every proprietary screen fails —
                        // never a dead end.
                        launchAction = { ctx ->
                            ctx.launchOemIntent(
                                *backgroundIntents.toTypedArray(),
                                fallback = appDetailsIntent(ctx, ctx.packageName)
                            )
                        }
                    )
                )
            }
        }

        return steps
    }

    companion object {
        /**
         * Resolve-Before-Show gating decision for the consolidated OEM step. Extracted as a pure,
         * side-effect-free function so it is unit-testable without Android statics (Build,
         * PackageManager). Encodes all three audit rules in one place:
         *
         *  • [hasGuidance] == false → stock device (Pixel/Motorola/Nothing/AOSP): never show.
         *  • [hasRealOemScreen] == false && [isBatteryExempt] == true → the step could only reopen
         *    the generic App Info page and battery optimization is already handled: redundant,
         *    so skip (Strategy A).
         *  • [hasRealOemScreen] == true → a genuine auto-start / vendor battery screen exists and
         *    cannot be verified by any API: always show, regardless of battery state (Strategy B).
         */
        internal fun shouldShowOemStep(
            hasGuidance: Boolean,
            hasRealOemScreen: Boolean,
            isBatteryExempt: Boolean
        ): Boolean = hasGuidance && !(!hasRealOemScreen && isBatteryExempt)
    }
}
