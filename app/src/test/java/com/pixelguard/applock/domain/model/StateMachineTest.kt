package com.pixelguard.applock.domain.model

import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.domain.usecase.SessionManager
import com.pixelguard.applock.service.camera.CameraCaptureState
import com.pixelguard.applock.service.camera.SilentCameraCapture
import com.pixelguard.applock.ui.lock.LockActivity
import com.pixelguard.applock.ui.lock.LockActivityState
import com.pixelguard.applock.ui.main.PermissionStatus
import com.pixelguard.applock.ui.onboarding.OnboardingStep
import org.junit.After
import org.junit.Before
import org.junit.Test

class StateMachineTest {

    private val testPkg = "com.test.target"
    private val otherPkg = "com.test.other"

    @Before
    fun setUp() {
        SessionManager.clearAllSessions()
        SessionManager.resumeProtection()
        SessionManager.resetAllLockoutsForTest()
        SilentCameraCapture.resetCooldownForTest()
        LockActivity.transitionToIdle()
    }

    @After
    fun tearDown() {
        SessionManager.clearAllSessions()
        SessionManager.resumeProtection()
        SessionManager.resetAllLockoutsForTest()
        SilentCameraCapture.resetCooldownForTest()
        LockActivity.transitionToIdle()
    }

    // =========================================================================
    // 1. AppSessionState (SM-1)
    // =========================================================================

    @Test
    fun appSessionState_whenNeverUnlocked_returnsLocked() {
        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 30)
        assertThat(state).isEqualTo(AppSessionState.Locked)
        assertThat(state.isUnlocked).isFalse()
    }

    @Test
    fun appSessionState_whenInForeground_returnsActiveForeground() {
        SessionManager.markAppUnlocked(testPkg)
        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 0)

        assertThat(state).isInstanceOf(AppSessionState.ActiveForeground::class.java)
        assertThat(state.isUnlocked).isTrue()
    }

    @Test
    fun appSessionState_withinLaunchBuffer_returnsTransitionBuffer() {
        SessionManager.markAppUnlocked(testPkg)
        SessionManager.currentForegroundPackage = null // Target app is in window transition (not yet foreground)

        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 0)
        assertThat(state).isInstanceOf(AppSessionState.TransitionBuffer::class.java)
        assertThat(state.isUnlocked).isTrue()
    }

    @Test
    fun appSessionState_whenExitedWithinGrace_returnsGracePeriod() {
        val now = System.currentTimeMillis()
        SessionManager.markAppUnlockedForTest(testPkg, now - 20_000L, inForeground = false)
        SessionManager.markAppExitedForTest(testPkg, now - 10_000L)

        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 30)
        assertThat(state).isInstanceOf(AppSessionState.GracePeriod::class.java)
        val grace = state as AppSessionState.GracePeriod
        assertThat(grace.elapsedSeconds).isEqualTo(10L)
        assertThat(grace.remainingSeconds).isEqualTo(20L)
        assertThat(state.isUnlocked).isTrue()
    }

    @Test
    fun appSessionState_whenGraceExpired_returnsExpired() {
        val now = System.currentTimeMillis()
        SessionManager.markAppUnlockedForTest(testPkg, now - 40_000L, inForeground = false)
        SessionManager.markAppExitedForTest(testPkg, now - 35_000L)

        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 30)
        assertThat(state).isInstanceOf(AppSessionState.Expired::class.java)
        assertThat(state.isUnlocked).isFalse()
    }

    @Test
    fun appSessionState_whenProtectionPaused_returnsPaused() {
        SessionManager.pauseProtection(15)
        val state = SessionManager.getSessionState(testPkg, effectiveGraceSeconds = 0)

        assertThat(state).isInstanceOf(AppSessionState.Paused::class.java)
        assertThat(state.isUnlocked).isTrue()
    }

    // =========================================================================
    // 2. RateLimitState (SM-2)
    // =========================================================================

    @Test
    fun rateLimitState_whenNoFailures_returnsUnrestricted() {
        val state = SessionManager.getRateLimitState(testPkg)
        assertThat(state).isEqualTo(RateLimitState.Unrestricted)
        assertThat(state.isLockedOut).isFalse()
        assertThat(state.attemptsUntilLockout).isEqualTo(5)
    }

    @Test
    fun rateLimitState_oneToFourAttempts_returnsWarning() {
        SessionManager.incrementFailedAttempts(testPkg)
        val state1 = SessionManager.getRateLimitState(testPkg)
        assertThat(state1).isInstanceOf(RateLimitState.Warning::class.java)
        val warn1 = state1 as RateLimitState.Warning
        assertThat(warn1.failedAttempts).isEqualTo(1)
        assertThat(warn1.attemptsUntilLockout).isEqualTo(4)
        assertThat(warn1.isCautionBadgeVisible).isFalse()

        // 3rd attempt -> 2 remaining -> caution badge visible
        SessionManager.incrementFailedAttempts(testPkg)
        SessionManager.incrementFailedAttempts(testPkg)
        val state3 = SessionManager.getRateLimitState(testPkg) as RateLimitState.Warning
        assertThat(state3.failedAttempts).isEqualTo(3)
        assertThat(state3.attemptsUntilLockout).isEqualTo(2)
        assertThat(state3.isCautionBadgeVisible).isTrue()
    }

    @Test
    fun rateLimitState_fiveAttempts_triggersSoftLockout() {
        repeat(5) { SessionManager.incrementFailedAttempts(testPkg) }
        val state = SessionManager.getRateLimitState(testPkg)

        assertThat(state).isInstanceOf(RateLimitState.LockedOut.SoftLockout::class.java)
        val soft = state as RateLimitState.LockedOut.SoftLockout
        assertThat(soft.totalPenaltySeconds).isEqualTo(30)
        assertThat(soft.remainingSeconds).isGreaterThan(0)
        assertThat(state.isLockedOut).isTrue()
    }

    @Test
    fun rateLimitState_eightAttempts_triggersMediumLockout() {
        repeat(8) { SessionManager.incrementFailedAttempts(testPkg) }
        val state = SessionManager.getRateLimitState(testPkg)

        assertThat(state).isInstanceOf(RateLimitState.LockedOut.MediumLockout::class.java)
        val med = state as RateLimitState.LockedOut.MediumLockout
        assertThat(med.totalPenaltySeconds).isEqualTo(60)
        assertThat(state.isLockedOut).isTrue()
    }

    @Test
    fun rateLimitState_tenAttempts_triggersExtendedLockout() {
        repeat(10) { SessionManager.incrementFailedAttempts(testPkg) }
        val state = SessionManager.getRateLimitState(testPkg)

        assertThat(state).isInstanceOf(RateLimitState.LockedOut.ExtendedLockout::class.java)
        val ext = state as RateLimitState.LockedOut.ExtendedLockout
        assertThat(ext.totalPenaltySeconds).isEqualTo(300)
        assertThat(state.isLockedOut).isTrue()
    }

    @Test
    fun rateLimitState_whenLockoutExpires_transitionsToWarningAndClearsLockout() {
        var baseTime = 100_000L
        SessionManager.setTimeProviderForTest { baseTime }
        repeat(5) { SessionManager.incrementFailedAttempts(testPkg) }
        val stateLocked = SessionManager.getRateLimitState(testPkg)
        assertThat(stateLocked.isLockedOut).isTrue()

        var lockoutNotified = false
        SessionManager.onLockoutChanged = { _, durationRemaining, _ ->
            if (durationRemaining == 0L) lockoutNotified = true
        }

        // Fast-forward past 30s penalty
        baseTime += 31_000L
        val stateExpired = SessionManager.getRateLimitState(testPkg)
        assertThat(stateExpired.isLockedOut).isFalse()
        assertThat(stateExpired).isInstanceOf(RateLimitState.Warning::class.java)
        assertThat(lockoutNotified).isTrue()
    }

    // =========================================================================
    // 3. CameraCaptureState (SM-7)
    // =========================================================================

    @Test
    fun cameraCaptureState_idleAndCooldownTransitions() {
        val baseTime = 1_000_000L
        assertThat(SilentCameraCapture.getHardwareState(baseTime)).isEqualTo(CameraCaptureState.Idle)

        SilentCameraCapture.tryAcquireCapture(baseTime)
        assertThat(SilentCameraCapture.getHardwareState(baseTime)).isEqualTo(CameraCaptureState.CapturingStill)

        SilentCameraCapture.setIsCapturingForTest(false)
        val stateCooldown = SilentCameraCapture.getHardwareState(baseTime + 3000L)
        assertThat(stateCooldown).isInstanceOf(CameraCaptureState.InCooldown::class.java)
        val cooldown = stateCooldown as CameraCaptureState.InCooldown
        assertThat(cooldown.elapsedMs).isEqualTo(3000L)

        // After 10s cooldown
        assertThat(SilentCameraCapture.getHardwareState(baseTime + 10_000L)).isEqualTo(CameraCaptureState.Idle)
    }

    // =========================================================================
    // 4. LockActivityState (SM-3)
    // =========================================================================

    @Test
    fun lockActivityState_transitionsMaintainSynchronizedFlags() {
        assertThat(LockActivity.state).isEqualTo(LockActivityState.Idle)
        assertThat(LockActivity.isShowing).isFalse()
        assertThat(LockActivity.isLaunching).isFalse()
        assertThat(LockActivity.activeTargetPackage).isNull()

        LockActivity.transitionToLaunching(testPkg)
        assertThat(LockActivity.state).isEqualTo(LockActivityState.Launching(testPkg))
        assertThat(LockActivity.isLaunching).isTrue()
        assertThat(LockActivity.isShowing).isFalse()
        assertThat(LockActivity.activeTargetPackage).isEqualTo(testPkg)

        LockActivity.transitionToVisible(testPkg, isBiometric = true)
        assertThat(LockActivity.state).isEqualTo(LockActivityState.Visible(testPkg, isBiometricActive = true))
        assertThat(LockActivity.isShowing).isTrue()
        assertThat(LockActivity.isLaunching).isFalse()
        assertThat(LockActivity.activeTargetPackage).isEqualTo(testPkg)

        LockActivity.transitionToIdle()
        assertThat(LockActivity.state).isEqualTo(LockActivityState.Idle)
        assertThat(LockActivity.isShowing).isFalse()
    }

    // =========================================================================
    // 5. OnboardingStep (SM-9)
    // =========================================================================

    @Test
    fun onboardingStep_resolvesStepDeterministically() {
        // Step 1: Missing usage access
        val step1 = OnboardingStep.resolve(
            PermissionStatus(hasUsagePermission = false, hasOverlayPermission = false),
            isPinConfigured = false,
            recoveryCode = null
        )
        assertThat(step1).isEqualTo(OnboardingStep.GrantUsageAccess)

        // Step 2: Usage granted, missing overlay
        val step2 = OnboardingStep.resolve(
            PermissionStatus(hasUsagePermission = true, hasOverlayPermission = false),
            isPinConfigured = false,
            recoveryCode = null
        )
        assertThat(step2).isEqualTo(OnboardingStep.GrantOverlay)

        // Step 3: Permissions granted, need PIN
        val step3 = OnboardingStep.resolve(
            PermissionStatus(hasUsagePermission = true, hasOverlayPermission = true),
            isPinConfigured = false,
            recoveryCode = null
        )
        assertThat(step3).isEqualTo(OnboardingStep.SetupMasterPin)

        // Step 4: PIN configured, recovery code confirmation dialog
        val step4 = OnboardingStep.resolve(
            PermissionStatus(hasUsagePermission = true, hasOverlayPermission = true),
            isPinConfigured = true,
            recoveryCode = "ABCD-EFGH-IJKL-MNOP"
        )
        assertThat(step4).isInstanceOf(OnboardingStep.ConfirmRecoveryCode::class.java)

        // Step 5: Everything complete
        val step5 = OnboardingStep.resolve(
            PermissionStatus(hasUsagePermission = true, hasOverlayPermission = true),
            isPinConfigured = true,
            recoveryCode = null
        )
        assertThat(step5).isEqualTo(OnboardingStep.ReadyToStart)
    }
}
