package com.pixelguard.applock.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private val testPackage = "com.example.bank"
    private val otherPackage = "com.example.social"

    @Before
    fun setUp() {
        SessionManager.clearAllSessions()
        SessionManager.resumeProtection()
        SessionManager.resetAllLockoutsForTest()
    }

    // --- Temporary Unlock & Grace Period Tests ---

    @Test
    fun isTemporarilyUnlocked_whenNeverUnlocked_returnsFalse() {
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 30))
    }

    @Test
    fun isTemporarilyUnlocked_whenWithinGracePeriod_returnsTrue() {
        val now = System.currentTimeMillis()
        SessionManager.markAppUnlockedForTest(testPackage, now - 10_000L, inForeground = false) // 10s ago, exited

        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 30))
    }

    @Test
    fun isTemporarilyUnlocked_whenGracePeriodExpired_returnsFalse() {
        val now = System.currentTimeMillis()
        SessionManager.markAppUnlockedForTest(testPackage, now - 35_000L, inForeground = false) // 35s ago, exited

        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 30))
    }

    @Test
    fun isTemporarilyUnlocked_whenActivelyInForeground_neverExpiresWhileInUse() {
        val now = System.currentTimeMillis()
        // Unlocked 10 minutes ago, but user is still actively using it in foreground
        SessionManager.markAppUnlockedForTest(testPackage, now - 600_000L, inForeground = true)

        // Must remain unlocked regardless of grace period (0s, 30s, etc.) while actively in use
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 30))
    }

    @Test
    fun isTemporarilyUnlocked_exitAndReturn_gracePeriodCountsFromExitTime() {
        val now = System.currentTimeMillis()
        // App was unlocked 5 minutes ago and used actively
        SessionManager.markAppUnlockedForTest(testPackage, now - 300_000L, inForeground = false)
        // User switches away 10 seconds ago
        SessionManager.onAppSwitched(otherPackage, 30)
        SessionManager.markAppExitedForTest(testPackage, now - 10_000L)

        // Returning within 30s grace period -> unlocked!
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 30))

        // Exited 35s ago (grace expired) -> locked!
        SessionManager.markAppExitedForTest(testPackage, now - 35_000L)
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 30))
    }

    @Test
    fun onScreenOff_clearsAllSessionsImmediately_ignoringGracePeriods() {
        SessionManager.markAppUnlocked(testPackage)
        SessionManager.markAppUnlocked(otherPackage)
        assertEquals(otherPackage, SessionManager.currentForegroundPackage)

        // Before screen off, both are unlocked
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 300))
        assertTrue(SessionManager.isTemporarilyUnlocked(otherPackage, 300))

        // Screen turns off (power button pressed)
        SessionManager.onScreenOff()
        assertEquals(null, SessionManager.currentForegroundPackage)

        // All sessions wiped immediately, ignoring remaining grace periods
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 300))
        assertFalse(SessionManager.isTemporarilyUnlocked(otherPackage, 300))
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, SessionManager.GRACE_UNTIL_SCREEN_OFF))
    }

    @Test
    fun isTemporarilyUnlocked_immediateRelock_allows15SecondQuickSwitchBuffer() {
        val now = System.currentTimeMillis()
        SessionManager.markAppUnlockedForTest(testPackage, now - 10_000L, inForeground = true)

        // Same app in foreground with immediate relock (0s grace)
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))

        // Switch to another app 5s ago (within 15s quick-switch buffer)
        SessionManager.onAppSwitched(otherPackage, 0)
        SessionManager.markAppExitedForTest(testPackage, now - 5_000L)
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))

        // Exited 20s ago (exceeds 15s quick-switch buffer)
        SessionManager.markAppExitedForTest(testPackage, now - 20_000L)
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 0))
    }

    @Test
    fun isTemporarilyUnlocked_untilScreenOff_remainsUnlockedWhileScreenOn() {
        val now = System.currentTimeMillis()
        // Unlocked 15 minutes ago, exited 10 minutes ago
        SessionManager.markAppUnlockedForTest(testPackage, now - 900_000L, inForeground = false)
        SessionManager.markAppExitedForTest(testPackage, now - 600_000L)

        // While screen is on, GRACE_UNTIL_SCREEN_OFF stays unlocked!
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, SessionManager.GRACE_UNTIL_SCREEN_OFF))

        // Screen turns off -> immediately locked!
        SessionManager.onScreenOff()
        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, SessionManager.GRACE_UNTIL_SCREEN_OFF))
    }

    @Test
    fun isTemporarilyUnlocked_withinTransitionBuffer_protectedFromFlicker() {
        // App unlocked just now (0s elapsed)
        SessionManager.markAppUnlocked(testPackage)

        // Must remain unlocked during transition buffer
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))
        assertTrue(SessionManager.isRecentlyUnlocked(testPackage, 3000L))

        // Even if launcher momentarily fires during window dismiss, session is protected
        SessionManager.onAppSwitched("com.google.android.apps.nexuslauncher", 0)
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))
    }

    // --- Protection Pause (Quick Settings) Tests ---

    @Test
    fun isPaused_whenPauseActive_returnsTrueAndOverridesLock() {
        val now = System.currentTimeMillis()
        SessionManager.pauseProtectionUntilForTest(now + 600_000L) // 10 minutes in future

        assertTrue(SessionManager.isPaused())
        // Even with no unlock session, pause overrides lock
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))
        assertTrue(SessionManager.getRemainingPauseMinutes() > 0)
    }

    @Test
    fun isPaused_afterResumeProtection_returnsFalse() {
        SessionManager.pauseProtection(15)
        assertTrue(SessionManager.isPaused())

        SessionManager.resumeProtection()
        assertFalse(SessionManager.isPaused())
        assertEquals(0, SessionManager.getRemainingPauseMinutes())
    }

    @Test
    fun isPaused_whenPauseExpired_returnsFalse() {
        val now = System.currentTimeMillis()
        SessionManager.pauseProtectionUntilForTest(now - 1_000L) // in past

        assertFalse(SessionManager.isPaused())
        assertEquals(0, SessionManager.getRemainingPauseMinutes())
    }

    @Test
    fun pauseProtection_whenIndefinite_returnsMinusOneRemainingMinutes() {
        SessionManager.pauseProtection(-1)

        assertTrue(SessionManager.isPaused())
        assertEquals(-1, SessionManager.getRemainingPauseMinutes())
        assertTrue(SessionManager.isTemporarilyUnlocked(testPackage, 0))

        SessionManager.resumeProtection()
        assertFalse(SessionManager.isPaused())
        assertEquals(0, SessionManager.getRemainingPauseMinutes())
    }

    // --- Night Lockdown Scheduling Tests ---

    @Test
    fun isNightLockdownActive_whenDisabled_returnsFalse() {
        assertFalse(SessionManager.isNightLockdownActive(scheduleEnabled = false, startHour = 23, endHour = 7, currentHour = 2))
        assertFalse(SessionManager.isNightLockdownActive(scheduleEnabled = false, startHour = 23, endHour = 7, currentHour = 23))
    }

    @Test
    fun isNightLockdownActive_overnightSchedule_correctlyCalculatesHours() {
        val start = 23
        val end = 7

        // Before start
        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 22))

        // Exact start hour
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 23))

        // Mid-night hours
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 0))
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 3))
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 6))

        // End boundary
        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 7))

        // Daytime
        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 14))
    }

    @Test
    fun isNightLockdownActive_daytimeSchedule_correctlyCalculatesHours() {
        val start = 9
        val end = 17

        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 8))
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 9))
        assertTrue(SessionManager.isNightLockdownActive(true, start, end, currentHour = 13))
        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 17))
        assertFalse(SessionManager.isNightLockdownActive(true, start, end, currentHour = 20))
    }

    // --- Effective Grace Period Calculations ---

    @Test
    fun getEffectiveGracePeriod_duringNightLockdown_alwaysZero() {
        val result = SessionManager.getEffectiveGracePeriod(
            packageName = testPackage,
            defaultGraceSeconds = 60,
            isNightLockdown = true,
            isPerAppGraceEnabled = true,
            appGracePeriodsJson = """{"$testPackage": 120}"""
        )
        assertEquals(0, result)
    }

    @Test
    fun getEffectiveGracePeriod_perAppDisabled_returnsGlobalDefault() {
        val result = SessionManager.getEffectiveGracePeriod(
            packageName = testPackage,
            defaultGraceSeconds = 45,
            isNightLockdown = false,
            isPerAppGraceEnabled = false,
            appGracePeriodsJson = """{"$testPackage": 120}"""
        )
        assertEquals(45, result)
    }

    @Test
    fun getEffectiveGracePeriod_perAppEnabled_returnsCustomPeriod() {
        val json = """{"$testPackage": 120, "$otherPackage": 30}"""
        val result = SessionManager.getEffectiveGracePeriod(
            packageName = testPackage,
            defaultGraceSeconds = 15,
            isNightLockdown = false,
            isPerAppGraceEnabled = true,
            appGracePeriodsJson = json
        )
        assertEquals(120, result)
    }

    @Test
    fun getEffectiveGracePeriod_perAppEnabled_missingPackage_returnsDefault() {
        val json = """{"$otherPackage": 30}"""
        val result = SessionManager.getEffectiveGracePeriod(
            packageName = testPackage,
            defaultGraceSeconds = 15,
            isNightLockdown = false,
            isPerAppGraceEnabled = true,
            appGracePeriodsJson = json
        )
        assertEquals(15, result)
    }

    @Test
    fun getEffectiveGracePeriod_withMalformedJson_gracefullyReturnsDefault() {
        val result = SessionManager.getEffectiveGracePeriod(
            packageName = testPackage,
            defaultGraceSeconds = 30,
            isNightLockdown = false,
            isPerAppGraceEnabled = true,
            appGracePeriodsJson = "invalid-json"
        )
        assertEquals(30, result)
    }

    // --- Session Lifecycle Tests ---

    @Test
    fun lockPackage_clearsOnlyTargetPackageSession() {
        SessionManager.markAppUnlocked(testPackage)
        SessionManager.markAppUnlocked(otherPackage)

        SessionManager.lockPackage(testPackage)

        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 60))
        assertTrue(SessionManager.isTemporarilyUnlocked(otherPackage, 60))
    }

    @Test
    fun clearAllSessions_resetsAllPackages() {
        SessionManager.markAppUnlocked(testPackage)
        SessionManager.markAppUnlocked(otherPackage)

        SessionManager.clearAllSessions()

        assertFalse(SessionManager.isTemporarilyUnlocked(testPackage, 60))
        assertFalse(SessionManager.isTemporarilyUnlocked(otherPackage, 60))
    }

    // --- Failed Attempts & Rate Limiting Tests ---

    @Test
    fun failedAttempts_incrementsAndPersistsUntilReset() {
        assertEquals(0, SessionManager.getFailedAttempts(testPackage))

        assertEquals(1, SessionManager.incrementFailedAttempts(testPackage))
        assertEquals(2, SessionManager.incrementFailedAttempts(testPackage))
        assertEquals(2, SessionManager.getFailedAttempts(testPackage))

        SessionManager.resetFailedAttempts(testPackage)
        assertEquals(0, SessionManager.getFailedAttempts(testPackage))
    }

    @Test
    fun failedAttempts_triggersLockoutAtThreshold() {
        assertFalse(SessionManager.isLockedOut(testPackage))
        assertEquals(0, SessionManager.getRemainingLockoutSeconds(testPackage))

        repeat(4) { SessionManager.incrementFailedAttempts(testPackage) }
        assertFalse(SessionManager.isLockedOut(testPackage))

        // 5th failed attempt triggers 30s lockout
        SessionManager.incrementFailedAttempts(testPackage)
        assertTrue(SessionManager.isLockedOut(testPackage))
        assertTrue(SessionManager.getRemainingLockoutSeconds(testPackage) in 25..30)

        // Reset clears lockout
        SessionManager.resetFailedAttempts(testPackage)
        assertFalse(SessionManager.isLockedOut(testPackage))
        assertEquals(0, SessionManager.getRemainingLockoutSeconds(testPackage))
    }

    @Test
    fun failedAttempts_extendedLockoutAtEightAttempts() {
        repeat(8) { SessionManager.incrementFailedAttempts(testPackage) }
        assertTrue(SessionManager.isLockedOut(testPackage))
        assertTrue(SessionManager.getRemainingLockoutSeconds(testPackage) in 55..60)
        SessionManager.resetFailedAttempts(testPackage)
    }

    @Test
    fun getAttemptsUntilLockout_countsDownCorrectly() {
        assertEquals(5, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.incrementFailedAttempts(testPackage)
        assertEquals(4, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.incrementFailedAttempts(testPackage)
        assertEquals(3, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.incrementFailedAttempts(testPackage)
        assertEquals(2, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.incrementFailedAttempts(testPackage)
        assertEquals(1, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.incrementFailedAttempts(testPackage)
        assertEquals(0, SessionManager.getAttemptsUntilLockout(testPackage))
        SessionManager.resetFailedAttempts(testPackage)
        assertEquals(5, SessionManager.getAttemptsUntilLockout(testPackage))
    }

    @Test
    fun syncPersistedLockout_clockTampering_failsClosedToMaxLockout() {
        val now = System.currentTimeMillis()
        // Clock manipulated 2 days into the future:
        SessionManager.syncPersistedLockout(
            attempts = 10,
            wallClockExpiryMs = now + 172_800_000L,
            durationRemainingMs = 300_000L,
            currentWallTime = now
        )
        assertTrue("Future clock tampering must fail-closed (M-11)", SessionManager.isLockedOut(testPackage))
        assertTrue(SessionManager.getRemainingLockoutSeconds(testPackage) in 290..300)

        // Clock manipulated into past with duration remaining:
        SessionManager.syncPersistedLockout(
            attempts = 5,
            wallClockExpiryMs = now - 100_000L,
            durationRemainingMs = 30_000L,
            currentWallTime = now
        )
        assertTrue("Past clock tampering with remaining duration must remain locked out (M-11)", SessionManager.isLockedOut(testPackage))
        assertTrue(SessionManager.getRemainingLockoutSeconds(testPackage) in 25..30)
    }
}
