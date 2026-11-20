package com.pixelguard.applock.ui.main

/**
 * Explicit state machine for MainActivity self-protection authentication gate.
 *
 * Replaces the implicit combinations of:
 * - isPinConfigured: Boolean?
 * - isAuthenticated: Boolean
 * - lastStopTimestamp & 30s selfLockThresholdMs
 */
sealed interface AppGateState {

    /**
     * DataStore is loading credentials state.
     */
    data object Loading : AppGateState

    /**
     * First launch: No Master PIN is configured yet. Bypasses lock gate to guide user through Onboarding.
     */
    data object FirstTimeOnboarding : AppGateState

    /**
     * Pixel Guard is locked. User must enter Master PIN or authenticate with Biometrics.
     */
    data class Locked(
        val allowBiometrics: Boolean,
        val remainingLockoutSeconds: Int = 0
    ) : AppGateState

    /**
     * User has successfully authenticated. Main app dashboard is accessible.
     */
    data object Authenticated : AppGateState
}
