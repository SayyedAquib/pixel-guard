package com.pixelguard.applock.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPreferencesTest {

    @Test
    fun testDefaultPreferences_haveExpectedDefaults() {
        val prefs = LockPreferences()

        // Core Authentication Defaults
        assertTrue("Biometrics should be enabled by default", prefs.isBiometricsEnabled)
        assertEquals("Lock type should default to PIN", LockType.PIN, prefs.lockType)
        assertEquals("Grace period should default to 0s (instant relock)", 0, prefs.gracePeriodSeconds)
        assertFalse("Intruder selfie should be disabled by default (H-8)", prefs.isIntruderSelfieEnabled)
        assertEquals("Failed attempts threshold should default to 3", 3, prefs.failedAttemptsThreshold)
        assertFalse("Decoy crash should be disabled by default", prefs.isDecoyCrashEnabled)
        assertTrue("Notification shield should be enabled by default", prefs.isNotificationShieldEnabled)

        // System Shielding Defaults (Must default to false to prevent double-locking third-party apps like GPay)
        assertFalse("Settings protection must default to false", prefs.isSettingsProtected)
        assertFalse("Play Store protection must default to false", prefs.isPlayStoreProtected)
        assertFalse("Scramble PIN should default to false", prefs.isScramblePinEnabled)
        assertFalse("Foreground service should default to false", prefs.isForegroundServiceEnabled)

        // Recents Privacy Masking (Must default to false to prevent black screen in scrcpy screen mirroring)
        assertFalse("Recents privacy masking must default to false for screen mirroring", prefs.isRecentsMaskingEnabled)

        // Quick Settings Tile Defaults
        assertTrue("Quick Tile should default to enabled", prefs.isQuickTileEnabled)
        assertEquals("Quick Tile pause minutes should default to 15", 15, prefs.quickTilePauseMinutes)

        // Sensory & Animation Defaults
        assertTrue("Haptics should default to enabled", prefs.isHapticsEnabled)
        assertTrue("Animations should default to enabled", prefs.isAnimationsEnabled)

        // Schedule & Sort Defaults
        assertFalse("Smart Schedule should default to disabled", prefs.isScheduleEnabled)
        assertEquals(23, prefs.scheduleStartHour)
        assertEquals(7, prefs.scheduleEndHour)
        assertFalse(prefs.isNightLockdownPinOnly)
        assertTrue(prefs.isPerAppGraceEnabled)
        assertEquals("{}", prefs.appGracePeriodsJson)
        assertTrue(prefs.isDynamicAppThemingEnabled)
        assertEquals(AppSortOrder.NAME_ASC, prefs.appSortOrder)
        assertEquals("[]", prefs.pinOnlyPackagesJson)
        assertEquals("[]", prefs.notificationExemptPackagesJson)
        assertEquals("{}", prefs.categoryOverridesJson)
        assertEquals("[]", prefs.userCustomTagsJson)
        assertEquals("{}", prefs.appCustomTagsJson)
    }

    @Test
    fun testGetOverriddenCategory_whenConfigured_returnsCategory() {
        val prefs = LockPreferences(
            categoryOverridesJson = """{"com.binance.app": "FINANCIAL", "com.custom.notes": "SENSITIVE"}"""
        )
        assertEquals(AppCategory.FINANCIAL, prefs.getOverriddenCategory("com.binance.app"))
        assertEquals(AppCategory.SENSITIVE, prefs.getOverriddenCategory("com.custom.notes"))
        assertEquals(null, prefs.getOverriddenCategory("com.unknown.app"))
    }

    @Test
    fun testGetUserCustomTags_whenConfigured_returnsTagsList() {
        val prefs = LockPreferences(
            userCustomTagsJson = """["Work", "Kids", "Crypto"]"""
        )
        val tags = prefs.getUserCustomTags()
        assertEquals(3, tags.size)
        assertEquals(listOf("Work", "Kids", "Crypto"), tags)
    }

    @Test
    fun testGetAppTags_whenConfigured_returnsAssignedTags() {
        val prefs = LockPreferences(
            appCustomTagsJson = """{"com.slack": ["Work", "Messaging"], "com.binance": ["Crypto"]}"""
        )
        assertEquals(listOf("Work", "Messaging"), prefs.getAppTags("com.slack"))
        assertEquals(listOf("Crypto"), prefs.getAppTags("com.binance"))
        assertEquals(emptyList<String>(), prefs.getAppTags("com.other.app"))
    }

    @Test
    fun testIsCustomized_identifiesCustomizedApps() {
        val defaultPrefs = LockPreferences()
        assertFalse(defaultPrefs.isCustomized("com.whatsapp"))

        val withCategoryOverride = LockPreferences(
            categoryOverridesJson = """{"com.whatsapp": "SENSITIVE"}"""
        )
        assertTrue(withCategoryOverride.isCustomized("com.whatsapp"))

        val withCustomTags = LockPreferences(
            appCustomTagsJson = """{"com.whatsapp": ["Personal"]}"""
        )
        assertTrue(withCustomTags.isCustomized("com.whatsapp"))

        val withCustomGrace = LockPreferences(
            appGracePeriodsJson = """{"com.whatsapp": 60}"""
        )
        assertTrue(withCustomGrace.isCustomized("com.whatsapp"))

        val withPinOnly = LockPreferences(
            pinOnlyPackagesJson = """["com.whatsapp"]"""
        )
        assertTrue(withPinOnly.isCustomized("com.whatsapp"))
    }

    @Test
    fun testAppSortOrderEnum_containsExpectedValues() {
        val values = AppSortOrder.values()
        assertEquals(3, values.size)
        assertEquals(AppSortOrder.NAME_ASC, AppSortOrder.valueOf("NAME_ASC"))
        assertEquals(AppSortOrder.LOCKED_FIRST, AppSortOrder.valueOf("LOCKED_FIRST"))
        assertEquals(AppSortOrder.CATEGORY, AppSortOrder.valueOf("CATEGORY"))
    }

    @Test
    fun testLockTypeEnum_containsExpectedValues() {
        val values = LockType.values()
        assertEquals(2, values.size)
        assertEquals(LockType.PIN, LockType.valueOf("PIN"))
        assertEquals(LockType.PATTERN, LockType.valueOf("PATTERN"))
    }

    @Test
    fun testLockPreferences_copy_updatesTargetFieldOnly() {
        val original = LockPreferences()
        val modified = original.copy(
            isSettingsProtected = true,
            isRecentsMaskingEnabled = true,
            appSortOrder = AppSortOrder.LOCKED_FIRST
        )

        assertTrue(modified.isSettingsProtected)
        assertTrue(modified.isRecentsMaskingEnabled)
        assertEquals(AppSortOrder.LOCKED_FIRST, modified.appSortOrder)

        // Ensure other fields remained unmutated
        assertTrue(modified.isBiometricsEnabled)
        assertEquals(0, modified.gracePeriodSeconds)
        assertTrue(modified.isHapticsEnabled)
    }

    @Test
    fun testIsPinOnly_whenConfigured_returnsTrue() {
        val prefs = LockPreferences(
            pinOnlyPackagesJson = """["com.bank.app", "com.crypto.wallet"]"""
        )
        assertTrue(prefs.isPinOnly("com.bank.app"))
        assertTrue(prefs.isPinOnly("com.crypto.wallet"))
        assertFalse(prefs.isPinOnly("com.social.app"))
    }

    @Test
    fun testIsPinOnly_whenEmpty_returnsFalse() {
        val prefs = LockPreferences(pinOnlyPackagesJson = "[]")
        assertFalse(prefs.isPinOnly("com.bank.app"))
    }

    @Test
    fun testIsNotificationMasked_byDefault_returnsTrue() {
        val prefs = LockPreferences(
            isNotificationShieldEnabled = true,
            notificationExemptPackagesJson = "[]"
        )
        assertTrue(prefs.isNotificationMasked("com.whatsapp"))
    }

    @Test
    fun testIsNotificationMasked_whenExempted_returnsFalse() {
        val prefs = LockPreferences(
            isNotificationShieldEnabled = true,
            notificationExemptPackagesJson = """["com.whatsapp"]"""
        )
        assertFalse(prefs.isNotificationMasked("com.whatsapp"))
        assertTrue(prefs.isNotificationMasked("com.telegram"))
    }

    @Test
    fun testIsNotificationMasked_whenGlobalShieldDisabled_returnsFalse() {
        val prefs = LockPreferences(
            isNotificationShieldEnabled = false,
            notificationExemptPackagesJson = "[]"
        )
        assertFalse(prefs.isNotificationMasked("com.whatsapp"))
    }
}
