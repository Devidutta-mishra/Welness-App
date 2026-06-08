package com.example.yourswelnes.feature.biometric

import com.example.yourswelnes.feature.biometric.data.BiometricRepository
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import com.example.yourswelnes.feature.biometric.ui.AuthRequirement
import com.example.yourswelnes.feature.biometric.ui.BiometricViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies how the lock screen decides to verify the user. The three branches map the device's
 * security state to an action:
 *  - an authenticator exists           -> show the biometric / device-credential prompt,
 *  - no screen lock at all             -> bypass (nothing to verify against),
 *  - secure but no usable authenticator-> unavailable.
 */
class BiometricViewModelTest {

    /** Hand-rolled fake so the test stays a fast JVM unit test (no Android framework, no mockk). */
    private class FakeBiometricRepository(
        private val canAuth: Boolean,
        private val deviceSecure: Boolean
    ) : BiometricRepository {
        override fun canAuthenticate(): Boolean = canAuth
        override fun isDeviceSecure(): Boolean = deviceSecure
    }

    private fun viewModel(canAuth: Boolean, deviceSecure: Boolean) =
        BiometricViewModel(FakeBiometricRepository(canAuth, deviceSecure), AppLockManager())

    @Test
    fun biometricOrCredentialAvailable_showsPrompt() {
        // Fingerprint enrolled (or a PIN as fall-back) — always prompt.
        assertEquals(
            AuthRequirement.BIOMETRIC_PROMPT,
            viewModel(canAuth = true, deviceSecure = true).resolveAuthRequirement()
        )
    }

    @Test
    fun noScreenLock_bypasses() {
        // No fingerprint AND no PIN/pattern/password — nothing to verify against, so let the user in.
        assertEquals(
            AuthRequirement.BYPASS,
            viewModel(canAuth = false, deviceSecure = false).resolveAuthRequirement()
        )
    }

    @Test
    fun secureButNoUsableAuthenticator_isUnavailable() {
        // Device reports a secure lock screen but no authenticator is currently usable (edge case).
        assertEquals(
            AuthRequirement.UNAVAILABLE,
            viewModel(canAuth = false, deviceSecure = true).resolveAuthRequirement()
        )
    }
}
