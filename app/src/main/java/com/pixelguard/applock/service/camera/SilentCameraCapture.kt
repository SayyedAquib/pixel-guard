package com.pixelguard.applock.service.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.pixelguard.applock.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SilentCameraCapture(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null
    private val isClosed = AtomicBoolean(false)

    @Volatile
    var currentState: CameraCaptureState = CameraCaptureState.Idle
        private set

    var onStateChanged: ((CameraCaptureState) -> Unit)? = null

    private fun transitionTo(newState: CameraCaptureState) {
        currentState = newState
        onStateChanged?.invoke(newState)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "CameraCaptureState -> $newState")
        }
    }

    @SuppressLint("MissingPermission")
    fun captureIntruderPhoto(onPhotoCaptured: (File) -> Unit) {
        // H-8: Verify CAMERA permission is granted before touching CameraManager
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(TAG, "CAMERA permission not granted; skipping intruder selfie capture")
            return
        }

        // Hardware Protection (BAT-001): Cooldown & concurrency mutex prevents Camera ISP thermal spikes
        if (!tryAcquireCapture()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Intruder photo capture skipped due to cooldown or concurrent capture")
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            releaseCapture()
            return
        }

        val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
        if (frontCameraId == null) {
            releaseCapture()
            return
        }

        startBackgroundThread()

        val watchdog = Runnable {
            if (BuildConfig.DEBUG) Log.w(TAG, "Intruder photo capture timed out after 4000ms; forcing hardware cleanup")
            transitionTo(CameraCaptureState.Failed("Intruder photo capture timed out after 4000ms"))
            cleanup()
        }
        watchdogRunnable = watchdog
        backgroundHandler?.postDelayed(watchdog, 4000L)

        val intruderDir = File(context.filesDir, "intruders").apply {
            if (!exists()) mkdirs()
        }
        val outputFile = File(intruderDir, "intruder_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.enc")

        imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    try {
                        transitionTo(CameraCaptureState.ProcessingImage(outputFile.name))
                        val cryptoManager = com.pixelguard.applock.AppLockApplication.instance.cryptoManager
                        val encryptedBytes = cryptoManager.encryptToFileBytes(bytes)
                        FileOutputStream(outputFile).use { it.write(encryptedBytes) }
                        transitionTo(CameraCaptureState.Completed(outputFile.absolutePath))
                        onPhotoCaptured(outputFile)
                    } catch (e: Exception) {
                        transitionTo(CameraCaptureState.Failed("Failed to save encrypted intruder selfie: ${e.message}", e))
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to save encrypted intruder selfie: ${e.message}")
                    } finally {
                        cleanup()
                    }
                }
            }, backgroundHandler)
        }

        try {
            transitionTo(CameraCaptureState.OpeningCamera(frontCameraId))
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (isClosed.get()) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    if (imageReader == null) {
                        camera.close()
                        cleanup()
                        return
                    }
                    transitionTo(CameraCaptureState.ConfiguringSession)
                    createCaptureSession(camera, outputFile)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    transitionTo(CameraCaptureState.Failed("Camera disconnected"))
                    camera.close()
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    transitionTo(CameraCaptureState.Failed("CameraDevice error code: $error"))
                    camera.close()
                    cleanup()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            transitionTo(CameraCaptureState.Failed("Error opening front camera: ${e.message}", e))
            if (BuildConfig.DEBUG) Log.e(TAG, "Error opening front camera: ${e.message}")
            cleanup()
        }
    }

    private fun createCaptureSession(camera: CameraDevice, outputFile: File) {
        val surface = imageReader?.surface
        if (surface == null) {
            cleanup()
            return
        }
        try {
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, 270) // Typical front-cam orientation for portrait
            }

            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (isClosed.get()) {
                        session.close()
                        cleanup()
                        return
                    }
                    captureSession = session
                    try {
                        transitionTo(CameraCaptureState.CapturingStill)
                        session.capture(captureRequestBuilder.build(), null, backgroundHandler)
                    } catch (e: Exception) {
                        transitionTo(CameraCaptureState.Failed("Failed to trigger capture request: ${e.message}", e))
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to trigger capture request: ${e.message}")
                        cleanup()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    transitionTo(CameraCaptureState.Failed("CameraCaptureSession configuration failed"))
                    session.close()
                    cleanup()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            transitionTo(CameraCaptureState.Failed("Session creation failed: ${e.message}", e))
            if (BuildConfig.DEBUG) Log.e(TAG, "Session creation failed: ${e.message}")
            cleanup()
        }
    }

    private fun startBackgroundThread() {
        handlerThread = HandlerThread("CameraBackgroundThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun cleanup() {
        if (!isClosed.compareAndSet(false, true)) return
        try {
            watchdogRunnable?.let { backgroundHandler?.removeCallbacks(it) }
            watchdogRunnable = null
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            handlerThread?.quitSafely()
            handlerThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error during camera cleanup: ${e.message}")
        } finally {
            releaseCapture()
        }
    }

    companion object {
        private const val TAG = "SilentCameraCapture"
        private const val CAPTURE_COOLDOWN_MS = 10_000L

        private val isCapturing = AtomicBoolean(false)
        @Volatile private var lastCaptureTimestampMs: Long = 0L

        /**
         * Evaluates the explicit [CameraCaptureState] of the camera hardware (Idle, CapturingStill, InCooldown).
         */
        fun getHardwareState(currentTimeMs: Long = System.currentTimeMillis()): CameraCaptureState {
            if (isCapturing.get()) return CameraCaptureState.CapturingStill
            val elapsed = currentTimeMs - lastCaptureTimestampMs
            return if (lastCaptureTimestampMs > 0L && elapsed < CAPTURE_COOLDOWN_MS) {
                CameraCaptureState.InCooldown(elapsed, CAPTURE_COOLDOWN_MS)
            } else {
                CameraCaptureState.Idle
            }
        }

        /**
         * Checks whether a new intruder photo can be captured based on hardware concurrency
         * lock and the 10-second thermal cooldown.
         */
        fun canCapture(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
            return getHardwareState(currentTimeMs) is CameraCaptureState.Idle
        }

        /**
         * Atomically claims the camera hardware capture lock if outside the cooldown window.
         */
        fun tryAcquireCapture(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
            if (!canCapture(currentTimeMs)) return false
            if (isCapturing.compareAndSet(false, true)) {
                lastCaptureTimestampMs = currentTimeMs
                return true
            }
            return false
        }

        private fun releaseCapture() {
            isCapturing.set(false)
        }

        @androidx.annotation.VisibleForTesting
        internal fun resetCooldownForTest() {
            isCapturing.set(false)
            lastCaptureTimestampMs = 0L
        }

        @androidx.annotation.VisibleForTesting
        internal fun setLastCaptureTimeForTest(timestamp: Long) {
            lastCaptureTimestampMs = timestamp
        }

        @androidx.annotation.VisibleForTesting
        internal fun setIsCapturingForTest(capturing: Boolean) {
            isCapturing.set(capturing)
        }
    }
}
