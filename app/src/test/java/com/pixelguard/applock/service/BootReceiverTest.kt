package com.pixelguard.applock.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.domain.usecase.SessionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverTest {

    private lateinit var receiver: BootReceiver
    private val testPackage = "com.instagram.android"

    @Before
    fun setUp() {
        receiver = BootReceiver()
        SessionManager.clearAllSessions()
    }

    @After
    fun tearDown() {
        SessionManager.clearAllSessions()
    }

    @Test
    fun onReceive_actionBootCompleted_startsAppLockForegroundServiceImmediately() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.nextStartedService
        assertThat(startedService).isNotNull()
        assertThat(startedService.component?.className).isEqualTo(AppLockForegroundService::class.java.name)
    }

    @Test
    fun onReceive_actionUserUnlocked_startsAppLockForegroundServiceImmediately() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(Intent.ACTION_USER_UNLOCKED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.nextStartedService
        assertThat(startedService).isNotNull()
        assertThat(startedService.component?.className).isEqualTo(AppLockForegroundService::class.java.name)
    }

    @Test
    fun onReceive_actionMyPackageReplaced_startsAppLockForegroundServiceImmediately() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.nextStartedService
        assertThat(startedService).isNotNull()
        assertThat(startedService.component?.className).isEqualTo(AppLockForegroundService::class.java.name)
    }

    @Test
    fun onReceive_clearsAllActiveSessions() {
        val context = RuntimeEnvironment.getApplication()
        SessionManager.markAppUnlocked(testPackage)
        assertThat(SessionManager.isTemporarilyUnlocked(testPackage, 0)).isTrue()

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)

        assertThat(SessionManager.isTemporarilyUnlocked(testPackage, 0)).isFalse()
    }
}
