package com.pixelguard.applock.service.usage

import android.app.usage.UsageEvents
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUsageDetectorTest {

    @Test
    fun resolveResumedPackage_returnsLatestResumedActivity() {
        val events = listOf(
            UsageEventRecord("com.android.launcher3", UsageEvents.Event.ACTIVITY_RESUMED, 1000L),
            UsageEventRecord("com.android.launcher3", UsageEvents.Event.ACTIVITY_PAUSED, 2000L),
            UsageEventRecord("com.instagram.android", UsageEvents.Event.ACTIVITY_RESUMED, 2500L),
            UsageEventRecord("com.instagram.android", UsageEvents.Event.ACTIVITY_STOPPED, 3000L),
            UsageEventRecord("com.whatsapp", UsageEvents.Event.ACTIVITY_RESUMED, 3500L)
        )

        val result = AppUsageDetector.resolveResumedPackage(events)
        assertThat(result).isEqualTo("com.whatsapp")
    }

    @Test
    fun resolveResumedPackage_whenNoResumedEvents_returnsNull() {
        val events = listOf(
            UsageEventRecord("com.android.launcher3", UsageEvents.Event.ACTIVITY_PAUSED, 1000L),
            UsageEventRecord("com.android.launcher3", UsageEvents.Event.ACTIVITY_STOPPED, 2000L)
        )

        val result = AppUsageDetector.resolveResumedPackage(events)
        assertThat(result).isNull()
    }

    @Test
    fun isTargetLocked_correctlyIdentifiesLockedAndProtectedPackages() {
        val lockedSet = setOf("com.instagram.android", "com.whatsapp")

        // Regular locked package
        assertThat(
            AppUsageDetector.isTargetLocked(
                packageName = "com.instagram.android",
                lockedPackages = lockedSet,
                isSettingsProtected = false,
                isPlayStoreProtected = false
            )
        ).isTrue()

        // Unlocked third-party package
        assertThat(
            AppUsageDetector.isTargetLocked(
                packageName = "com.spotify.music",
                lockedPackages = lockedSet,
                isSettingsProtected = false,
                isPlayStoreProtected = false
            )
        ).isFalse()

        // Android Settings when protected
        assertThat(
            AppUsageDetector.isTargetLocked(
                packageName = "com.android.settings",
                lockedPackages = lockedSet,
                isSettingsProtected = true,
                isPlayStoreProtected = false
            )
        ).isTrue()

        // Play Store when protected
        assertThat(
            AppUsageDetector.isTargetLocked(
                packageName = "com.android.vending",
                lockedPackages = lockedSet,
                isSettingsProtected = false,
                isPlayStoreProtected = true
            )
        ).isTrue()
    }

    @Test
    fun isSystemOrTransientPackage_identifiesSystemUiAndKeyboards() {
        assertThat(AppUsageDetector.isKnownSystemOrTransient("com.android.systemui")).isTrue()
        assertThat(AppUsageDetector.isKnownSystemOrTransient("com.google.android.systemui")).isTrue()
        assertThat(AppUsageDetector.isKnownSystemOrTransient("android")).isTrue()
        assertThat(AppUsageDetector.isKnownSystemOrTransient("com.instagram.android")).isFalse()
    }
}

