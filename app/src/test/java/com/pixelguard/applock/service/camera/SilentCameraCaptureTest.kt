package com.pixelguard.applock.service.camera

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class SilentCameraCaptureTest {

    @Before
    fun setUp() {
        SilentCameraCapture.resetCooldownForTest()
    }

    @After
    fun tearDown() {
        SilentCameraCapture.resetCooldownForTest()
    }

    @Test
    fun canCapture_whenIdleAndNoPriorCaptures_returnsTrue() {
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = 100_000L)).isTrue()
    }

    @Test
    fun canCapture_whenAlreadyCapturing_returnsFalse() {
        SilentCameraCapture.setIsCapturingForTest(true)
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = 100_000L)).isFalse()
    }

    @Test
    fun canCapture_withinTenSecondCooldown_returnsFalse() {
        val baseTime = 100_000L
        SilentCameraCapture.setLastCaptureTimeForTest(baseTime)

        // 5 seconds later (within 10s cooldown)
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = baseTime + 5_000L)).isFalse()

        // 9.999 seconds later (still within cooldown)
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = baseTime + 9_999L)).isFalse()
    }

    @Test
    fun canCapture_afterTenSecondCooldown_returnsTrue() {
        val baseTime = 100_000L
        SilentCameraCapture.setLastCaptureTimeForTest(baseTime)

        // Exactly 10 seconds later
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = baseTime + 10_000L)).isTrue()

        // 15 seconds later
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = baseTime + 15_000L)).isTrue()
    }

    @Test
    fun tryAcquireCapture_claimsLockAndUpdatesTimestamp() {
        val captureTime = 200_000L
        val acquired = SilentCameraCapture.tryAcquireCapture(currentTimeMs = captureTime)

        assertThat(acquired).isTrue()

        // Immediate subsequent call must be rejected
        val secondAcquire = SilentCameraCapture.tryAcquireCapture(currentTimeMs = captureTime + 100L)
        assertThat(secondAcquire).isFalse()

        // Even after cooldown, if isCapturing is still true, capture is rejected
        assertThat(SilentCameraCapture.canCapture(currentTimeMs = captureTime + 20_000L)).isFalse()
    }

    @Test
    fun resetCooldownForTest_restoresInitialState() {
        SilentCameraCapture.setIsCapturingForTest(true)
        SilentCameraCapture.setLastCaptureTimeForTest(System.currentTimeMillis())

        SilentCameraCapture.resetCooldownForTest()

        assertThat(SilentCameraCapture.canCapture(currentTimeMs = 50_000L)).isTrue()
    }
}
