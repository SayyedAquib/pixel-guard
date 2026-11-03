package com.pixelguard.applock.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
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
class AppLockForegroundServiceTest {

    private lateinit var controller: ServiceController<AppLockForegroundService>
    private lateinit var service: AppLockForegroundService

    private val instagramPackage = "com.instagram.android"
    private val bankPackage = "com.example.bank"
    private val pixelLauncherPackage = "com.google.android.apps.nexuslauncher"

    @Before
    fun setUp() {
        SessionManager.clearAllSessions()
        SessionManager.resumeProtection()
        LockActivity.isShowing = false

        controller = Robolectric.buildService(AppLockForegroundService::class.java)
        service = controller.create().get()

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
        LockActivity.isShowing = false
        controller.destroy()
    }

    @Test
    fun lockedPackage_whenNotUnlocked_launchesLockActivity() {
        val triggered = service.processForegroundPackage(instagramPackage)

        assertThat(triggered).isTrue()
        val shadowService = shadowOf(service)
        val nextIntent = shadowService.nextStartedActivity
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.component?.className).isEqualTo(LockActivity::class.java.name)
        assertThat(nextIntent.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE)).isEqualTo(instagramPackage)
    }

    @Test
    fun lockedPackage_whenTemporarilyUnlocked_doesNotLaunchLockActivity() {
        SessionManager.markAppUnlocked(instagramPackage)

        val triggered = service.processForegroundPackage(instagramPackage)

        assertThat(triggered).isFalse()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
    }

    @Test
    fun lockedPackage_whenLockActivityAlreadyShowing_doesNotLaunchDuplicate() {
        LockActivity.isShowing = true

        val triggered = service.processForegroundPackage(instagramPackage)

        assertThat(triggered).isFalse()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
    }

    @Test
    fun transientSystemPackage_isIgnored() {
        val triggered = service.processForegroundPackage("com.android.systemui")

        assertThat(triggered).isFalse()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
    }

    @Test
    fun launcherOrRecents_recordsAppSwitch() {
        val triggered = service.processForegroundPackage(pixelLauncherPackage)

        assertThat(triggered).isFalse()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNull()
        assertThat(service.getLastPackageForTest()).isEqualTo(pixelLauncherPackage)
    }

    @Test
    fun screenOff_suspendsEvaluationAndCallsSessionManager() {
        SessionManager.markAppUnlocked(instagramPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isTrue()

        val receiver = service.getScreenReceiverForTest()
        receiver.onReceive(service, Intent(Intent.ACTION_SCREEN_OFF))

        // Screen OFF must revoke unlocks
        assertThat(SessionManager.isTemporarilyUnlocked(instagramPackage, 0)).isFalse()

        // Further events while non-interactive are ignored
        val triggered = service.processForegroundPackage(instagramPackage)
        assertThat(triggered).isFalse()
    }

    @Test
    fun screenOn_resumesEvaluation() {
        val receiver = service.getScreenReceiverForTest()
        receiver.onReceive(service, Intent(Intent.ACTION_SCREEN_OFF))
        receiver.onReceive(service, Intent(Intent.ACTION_SCREEN_ON))

        val triggered = service.processForegroundPackage(instagramPackage)
        assertThat(triggered).isTrue()
    }

    @Test
    fun coldStartFailClosed_whenCachesNotReady_treatsAsLocked() {
        service.setCachesReadyForTest(false)

        val triggered = service.processForegroundPackage("com.unknown.app")
        assertThat(triggered).isTrue()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity).isNotNull()
    }

    @Test
    fun settingsProtection_whenEnabled_interceptsSettings() {
        val triggered = service.processForegroundPackage("com.android.settings")
        assertThat(triggered).isTrue()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity?.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE))
            .isEqualTo("com.android.settings")
    }

    @Test
    fun playStoreProtection_whenEnabled_interceptsPlayStore() {
        val triggered = service.processForegroundPackage("com.android.vending")
        assertThat(triggered).isTrue()
        val shadowService = shadowOf(service)
        assertThat(shadowService.nextStartedActivity?.getStringExtra(LockActivity.EXTRA_TARGET_PACKAGE))
            .isEqualTo("com.android.vending")
    }
}

