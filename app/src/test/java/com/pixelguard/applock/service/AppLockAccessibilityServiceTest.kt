package com.pixelguard.applock.service

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.domain.usecase.SessionManager
import com.pixelguard.applock.ui.lock.LockActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
class AppLockAccessibilityServiceTest {

    private lateinit var controller: ServiceController<AppLockAccessibilityService>
    private lateinit var service: AppLockAccessibilityService

    private val instagramPackage = "com.instagram.android"
    private val bankPackage = "com.example.bank"
    private val pixelLauncherPackage = "com.google.android.apps.nexuslauncher"

    @Before
    fun setUp() {
        SessionManager.clearAllSessions()
        SessionManager.resumeProtection()

        controller = Robolectric.buildService(AppLockAccessibilityService::class.java)
        service = controller.create().get()

        // Set default locked packages and preferences for testing
        service.setCachedLockedPackagesForTest(setOf(instagramPackage, bankPackage))
        service.setCachedPreferencesForTest(
            LockPreferences(
                isSettingsProtected = true,
                isPlayStoreProtected = true,
                gracePeriodSeconds = 0
            )
        )
    }

    @After
    fun tearDown() {
        SessionManager.clearAllSessions()
        controller.destroy()
    }

    // --- 1. Gesture Navigation Immunity (TYPE_WINDOWS_CHANGED) Tests ---

    @Test
    fun typeWindowsChanged_fromPixelLauncherGestureBar_doesNotRelockActivePackage() {
        val now = System.currentTimeMillis()
        // User unlocked Instagram 10 seconds ago and is actively using it in foreground
        SessionManager.markAppUnlockedForTest(instagramPackage, now - 10_000L, inForeground = true)
        service.setLastPackageForTest(instagramPackage)

        // Pixel 9 gesture navigation handle fires TYPE_WINDOWS_CHANGED while scrolling Instagram
        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED
            packageName = pixelLauncherPackage
            className = "android.view.View"
        }
        service.onAccessibilityEvent(event)

        // Assert: Must NOT launch LockActivity
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()

        // Assert: Instagram remains the active foreground package and stays unlocked
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()
    }

    @Test
    fun typeWindowsChanged_fromTargetPackage_isIgnored() {
        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED
            packageName = instagramPackage
            className = "android.widget.FrameLayout"
        }
        service.onAccessibilityEvent(event)

        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
    }

    // --- 2. Locked Package Interception Tests ---

    @Test
    fun typeWindowStateChanged_lockedPackage_whenNotUnlocked_launchesLockActivity() {
        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = instagramPackage
            className = "com.instagram.mainactivity.MainActivity"
        }
        service.onAccessibilityEvent(event)

        val shadowService = shadowOf(service)
        val startedIntent = shadowService.nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.component?.className).isEqualTo(LockActivity::class.java.name)
        assertThat(startedIntent.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE)).isEqualTo(instagramPackage)
    }

    @Test
    fun typeWindowStateChanged_lockedPackage_whenAlreadyUnlocked_marksResumedAndDoesNotLaunchLock() {
        SessionManager.markAppUnlocked(instagramPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = instagramPackage
            className = "com.instagram.mainactivity.MainActivity"
        }
        service.onAccessibilityEvent(event)

        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
    }

    // --- 3. Transient System Package Immunity Tests ---

    @Test
    fun typeWindowStateChanged_gboardKeyboard_preservesActiveSession() {
        SessionManager.markAppUnlocked(instagramPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.google.android.inputmethod.latin"
            className = "android.inputmethodservice.InputMethodService"
        }
        service.onAccessibilityEvent(event)

        // Keyboard opening must NOT switch foreground app or launch lock
        assertThat(shadowOf(service).nextStartedActivity).isNull()
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()
    }

    @Test
    fun typeWindowStateChanged_systemShareSheet_preservesActiveSession() {
        SessionManager.markAppUnlocked(instagramPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.android.intentresolver"
            className = "com.android.intentresolver.ChooserActivity"
        }
        service.onAccessibilityEvent(event)

        assertThat(shadowOf(service).nextStartedActivity).isNull()
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()
    }

    @Test
    fun typeWindowStateChanged_systemPhotoPicker_preservesActiveSession() {
        SessionManager.markAppUnlocked(instagramPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.google.android.providers.media.module"
            className = "com.android.providers.media.photopicker.PhotoPickerActivity"
        }
        service.onAccessibilityEvent(event)

        assertThat(shadowOf(service).nextStartedActivity).isNull()
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()
    }

    @Test
    fun typeWindowStateChanged_pixelGuardSelfPackage_preservesActiveSession() {
        SessionManager.markAppUnlocked(instagramPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = service.applicationContext.packageName
            className = LockActivity::class.java.name
        }
        service.onAccessibilityEvent(event)

        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()
    }

    // --- 4. Launcher & Grace Period Switching Tests ---

    @Test
    fun typeWindowStateChanged_launcher_immediateRelock_keepsUnlockedWithinQuickSwitchBufferThenLocks() {
        val now = SessionManager.now()
        SessionManager.markAppUnlockedForTest(bankPackage, now - 5_000L, inForeground = true)
        service.setLastPackageForTest(bankPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = pixelLauncherPackage
            className = "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
        }
        service.onAccessibilityEvent(event)

        // Within 15s quick-switch buffer, app remains unlocked for OTP/multitasking
        assertThat(SessionManager.isTemporarilyUnlocked(bankPackage, 0)).isTrue()
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(pixelLauncherPackage)

        // After quick-switch buffer expires (16s after exit), app is locked
        SessionManager.markAppExitedForTest(bankPackage, now - 16_000L)
        assertThat(SessionManager.isTemporarilyUnlocked(bankPackage, 0)).isFalse()
    }

    @Test
    fun typeWindowStateChanged_launcher_withGracePeriod_tracksExitAndKeepsUnlockedWithinGrace() {
        val now = SessionManager.now()
        service.setCachedPreferencesForTest(LockPreferences(gracePeriodSeconds = 30))
        SessionManager.markAppUnlockedForTest(bankPackage, now - 10_000L, inForeground = true)
        service.setLastPackageForTest(bankPackage)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = pixelLauncherPackage
            className = "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
        }
        service.onAccessibilityEvent(event)

        // Grace period is active: returning within 30s keeps it unlocked
        assertThat(SessionManager.isTemporarilyUnlocked(bankPackage, 30)).isTrue()
    }

    // --- 5. System Protection (Settings & Play Store) Tests ---

    @Test
    fun typeWindowStateChanged_settingsProtected_launchesLockActivity() {
        service.setCachedPreferencesForTest(LockPreferences(isSettingsProtected = true))

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.android.settings"
            className = "com.android.settings.Settings"
        }
        service.onAccessibilityEvent(event)

        val startedIntent = shadowOf(service).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE)).isEqualTo("com.android.settings")
    }

    @Test
    fun typeWindowStateChanged_playStoreProtected_launchesLockActivity() {
        service.setCachedPreferencesForTest(LockPreferences(isPlayStoreProtected = true))

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.android.vending"
            className = "com.google.android.finsky.activities.MainActivity"
        }
        service.onAccessibilityEvent(event)

        val startedIntent = shadowOf(service).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE)).isEqualTo("com.android.vending")
    }

    // --- 6. Screen Off Lifecycle Receiver Tests ---

    @Test
    fun screenOffBroadcast_revokesForegroundSessionImmediately() {
        SessionManager.markAppUnlocked(instagramPackage)
        assertThat(SessionManager.currentForegroundPackage).isEqualTo(instagramPackage)

        val receiver = service.getScreenOffReceiverForTest()
        receiver.onReceive(service, Intent(Intent.ACTION_SCREEN_OFF))

        // Screen off immediately revokes foreground session
        assertThat(SessionManager.currentForegroundPackage).isNull()
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isFalse()
    }

    // --- 7. Cold-Start Fail-Closed Protection (C-2) Tests ---

    @Test
    fun onAccessibilityEvent_whenCachesUnready_failsClosedAndLocksUntrustedPackage() {
        service.setCachesReadyForTest(false)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.untrusted.unknownapp"
            className = "com.untrusted.unknownapp.MainActivity"
        }
        service.onAccessibilityEvent(event)

        val startedIntent = shadowOf(service).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE)).isEqualTo("com.untrusted.unknownapp")
    }

    @Test
    fun onAccessibilityEvent_whenCachesUnready_doesNotLockSystemOrTransientPackages() {
        service.setCachesReadyForTest(false)

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = pixelLauncherPackage
            className = "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
        }
        service.onAccessibilityEvent(event)

        val startedIntent = shadowOf(service).nextStartedActivity
        assertThat(startedIntent).isNull()
    }
}
