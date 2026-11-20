package com.pixelguard.applock.ui.onboarding

import com.pixelguard.applock.ui.main.PermissionStatus

/**
 * Explicit state machine for Onboarding wizard progression.
 *
 * Replaces complex multi-variable nested when expressions for primary button text,
 * action dispatch, and layout sections.
 */
sealed interface OnboardingStep {

    /**
     * Required App Detection (Usage Access) permission is missing.
     */
    data object GrantUsageAccess : OnboardingStep

    /**
     * Required Display Over Other Apps (Overlay) permission is missing.
     */
    data object GrantOverlay : OnboardingStep

    /**
     * Both required permissions granted, but Master PIN is not yet configured.
     */
    data object SetupMasterPin : OnboardingStep

    /**
     * Master PIN configured; presenting 16-character offline recovery code for user confirmation.
     */
    data class ConfirmRecoveryCode(val recoveryCode: String) : OnboardingStep

    /**
     * All required permissions and Master PIN configured. Ready to enter dashboard.
     */
    data object ReadyToStart : OnboardingStep

    companion object {
        fun resolve(
            permissionStatus: PermissionStatus,
            isPinConfigured: Boolean?,
            recoveryCode: String?
        ): OnboardingStep {
            if (recoveryCode != null) {
                return ConfirmRecoveryCode(recoveryCode)
            }
            if (!permissionStatus.hasUsagePermission) {
                return GrantUsageAccess
            }
            if (!permissionStatus.hasOverlayPermission) {
                return GrantOverlay
            }
            if (isPinConfigured == false) {
                return SetupMasterPin
            }
            return ReadyToStart
        }
    }
}
