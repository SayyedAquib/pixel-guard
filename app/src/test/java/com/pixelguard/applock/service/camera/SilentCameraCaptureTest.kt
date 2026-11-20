package com.pixelguard.applock.service.camera

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

    @Test
    fun selectOptimalJpegSize_whenNullOrEmpty_fallsBackTo640x480() {
        val sizeNull = SilentCameraCapture.selectOptimalJpegSize(null)
        assertThat(sizeNull.width).isEqualTo(640)
        assertThat(sizeNull.height).isEqualTo(480)

        val sizeEmpty = SilentCameraCapture.selectOptimalJpegSize(emptyArray())
        assertThat(sizeEmpty.width).isEqualTo(640)
        assertThat(sizeEmpty.height).isEqualTo(480)
    }

    @Test
    fun selectOptimalJpegSize_whenTypicalSensorSizes_selectsSmallestInIdealRange() {
        val sizes = arrayOf(
            android.util.Size(3840, 2160),
            android.util.Size(1920, 1080),
            android.util.Size(1280, 720),
            android.util.Size(640, 480)
        )
        val selected = SilentCameraCapture.selectOptimalJpegSize(sizes)
        assertThat(selected.width).isEqualTo(640)
        assertThat(selected.height).isEqualTo(480)
    }

    @Test
    fun selectOptimalJpegSize_when640x480Missing_selectsSmallestSupportedIdealSize() {
        // Pixel 9 front camera without 640x480
        val sizes = arrayOf(
            android.util.Size(4000, 3000),
            android.util.Size(1920, 1080),
            android.util.Size(1280, 720)
        )
        val selected = SilentCameraCapture.selectOptimalJpegSize(sizes)
        assertThat(selected.width).isEqualTo(1280)
        assertThat(selected.height).isEqualTo(720)
    }
}
