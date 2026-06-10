package com.example.yourswelnes.feature.tracking.ui

import com.example.yourswelnes.core.tracking.OEMInstructionProvider
import com.example.yourswelnes.core.tracking.OemManufacturer
import com.example.yourswelnes.feature.tracking.ui.PermissionWizardViewModel.Companion.shouldShowOemStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM tests for the consolidated OEM step's Resolve-Before-Show gating.
 *
 * The full `buildStepList()` is entangled with Android statics (Build.MANUFACTURER) and
 * PackageManager probing, so the gating *decision* was extracted into the pure
 * [shouldShowOemStep] predicate. These tests exercise its complete truth table plus the
 * manufacturer → guidance mapping that drives the stock-device bypass — the two pieces that
 * actually decide whether the user ever sees the OEM step.
 */
class PermissionWizardOemGatingTest {

    // ── Strategy A: redundant App-Info-only step is skipped once battery is exempt ──────────

    @Test
    fun noRealOemScreen_andBatteryExempt_isSkipped() {
        // Only thing the step could open is App Info, and battery optimization is already handled.
        assertFalse(
            shouldShowOemStep(hasGuidance = true, hasRealOemScreen = false, isBatteryExempt = true)
        )
    }

    @Test
    fun noRealOemScreen_andBatteryNotExempt_isShown() {
        // No proprietary screen, but battery is NOT yet exempt — the user still needs App Info
        // to disable optimization, so the step must remain.
        assertTrue(
            shouldShowOemStep(hasGuidance = true, hasRealOemScreen = false, isBatteryExempt = false)
        )
    }

    // ── Strategy B: a genuine auto-start screen is never dropped ────────────────────────────

    @Test
    fun realOemScreen_isShown_regardlessOfBatteryState() {
        // Auto-start / vendor battery screen exists and cannot be verified via any API, so it is
        // shown whether or not battery optimization is already exempt.
        assertTrue(
            shouldShowOemStep(hasGuidance = true, hasRealOemScreen = true, isBatteryExempt = true)
        )
        assertTrue(
            shouldShowOemStep(hasGuidance = true, hasRealOemScreen = true, isBatteryExempt = false)
        )
    }

    // ── Stock-device bypass: hasGuidance=false always skips, whatever the other inputs ──────

    @Test
    fun noGuidance_isNeverShown() {
        for (realScreen in listOf(true, false)) {
            for (batteryExempt in listOf(true, false)) {
                assertFalse(
                    "stock device must never show the OEM step " +
                        "(realScreen=$realScreen, batteryExempt=$batteryExempt)",
                    shouldShowOemStep(
                        hasGuidance = false,
                        hasRealOemScreen = realScreen,
                        isBatteryExempt = batteryExempt
                    )
                )
            }
        }
    }

    // ── Manufacturer → guidance mapping (the input that feeds hasGuidance) ──────────────────

    @Test
    fun stockManufacturers_haveNoGuidance() {
        listOf(
            OemManufacturer.GOOGLE,
            OemManufacturer.MOTOROLA,
            OemManufacturer.NOTHING,
            OemManufacturer.GENERIC
        ).forEach { manufacturer ->
            val instructions = OEMInstructionProvider.forManufacturer(manufacturer)
            assertFalse("$manufacturer should be bypassed", instructions.hasGuidance)
            assertTrue("$manufacturer should carry no steps", instructions.steps.isEmpty())
        }
    }

    @Test
    fun oemManufacturers_haveGuidanceAndNonEmptySteps() {
        listOf(
            OemManufacturer.VIVO,
            OemManufacturer.XIAOMI,
            OemManufacturer.OPPO,
            OemManufacturer.REALME,
            OemManufacturer.SAMSUNG,
            OemManufacturer.ONEPLUS
        ).forEach { manufacturer ->
            val instructions = OEMInstructionProvider.forManufacturer(manufacturer)
            assertTrue("$manufacturer should show guidance", instructions.hasGuidance)
            assertTrue("$manufacturer should have steps", instructions.steps.isNotEmpty())
            assertTrue("$manufacturer should have a why-justification", instructions.whyText.isNotBlank())
            // First step must match the wizard's primary button label so the copy is coherent.
            assertEquals(
                "Tap 'Configure Settings' below.",
                instructions.steps.first()
            )
        }
    }

    @Test
    fun oppoAndRealme_shareColorOsInstructions() {
        // ColorOS is identical on OPPO and Realme — the provider must return the same copy.
        val oppo = OEMInstructionProvider.forManufacturer(OemManufacturer.OPPO)
        val realme = OEMInstructionProvider.forManufacturer(OemManufacturer.REALME)
        assertEquals(oppo.title, realme.title)
        assertEquals(oppo.whyText, realme.whyText)
        assertEquals(oppo.steps, realme.steps)
    }

    @Test
    fun deviceSpecificCopy_usesOemTerminology() {
        // Spot-check that the dynamic text is genuinely tailored, not generic Android wording.
        assertTrue(
            OEMInstructionProvider.forManufacturer(OemManufacturer.XIAOMI)
                .steps.any { it.contains("Autostart") }
        )
        assertTrue(
            OEMInstructionProvider.forManufacturer(OemManufacturer.SAMSUNG)
                .steps.any { it.contains("Never sleeping apps") }
        )
        assertTrue(
            OEMInstructionProvider.forManufacturer(OemManufacturer.VIVO)
                .steps.any { it.contains("High Background Power Usage") }
        )
    }
}
