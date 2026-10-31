package com.pixelguard.applock.service.camera

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentCameraCaptureTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SilentCameraCapture.resetCooldownForTest()
    }

    @After
    fun tearDown() {
        SilentCameraCapture.resetCooldownForTest()
    }

    @Test
    fun canCapture_returnsTrueInitially() {
        assertThat(SilentCameraCapture.canCapture()).isTrue()
    }

    @Test
    fun cooldown_preventsRapidSubsequentCaptures() {
        val now = System.currentTimeMillis()
        SilentCameraCapture.setLastCaptureTimeForTest(now)

        // Within 10-second cooldown window: capture must be blocked
        assertThat(SilentCameraCapture.canCapture(now + 2_000L)).isFalse()
        assertThat(SilentCameraCapture.canCapture(now + 9_999L)).isFalse()

        // Beyond 10-second cooldown window: capture is permitted
        assertThat(SilentCameraCapture.canCapture(now + 10_001L)).isTrue()
    }

    @Test
    fun isCapturing_blocksConcurrentCaptureInvocations() {
        SilentCameraCapture.setIsCapturingForTest(true)
        assertThat(SilentCameraCapture.canCapture()).isFalse()

        SilentCameraCapture.setIsCapturingForTest(false)
        assertThat(SilentCameraCapture.canCapture()).isTrue()
    }
}
