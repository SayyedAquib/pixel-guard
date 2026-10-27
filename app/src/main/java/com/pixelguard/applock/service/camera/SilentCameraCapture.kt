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

    @SuppressLint("MissingPermission")
    fun captureIntruderPhoto(onPhotoCaptured: (File) -> Unit) {
        // H-8: Verify CAMERA permission is granted before touching CameraManager
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(TAG, "CAMERA permission not granted; skipping intruder selfie capture")
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: return

        startBackgroundThread()

        val watchdog = Runnable {
            if (BuildConfig.DEBUG) Log.w(TAG, "Intruder photo capture timed out after 4000ms; forcing hardware cleanup")
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
                        val cryptoManager = com.pixelguard.applock.AppLockApplication.instance.cryptoManager
                        val encryptedBytes = cryptoManager.encryptToFileBytes(bytes)
                        FileOutputStream(outputFile).use { it.write(encryptedBytes) }
                        onPhotoCaptured(outputFile)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to save encrypted intruder selfie: ${e.message}")
                    } finally {
                        cleanup()
                    }
                }
            }, backgroundHandler)
        }

        try {
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
                    createCaptureSession(camera, outputFile)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cleanup()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
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
                        session.capture(captureRequestBuilder.build(), null, backgroundHandler)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to trigger capture request: ${e.message}")
                        cleanup()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    cleanup()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
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
        }
    }

    companion object {
        private const val TAG = "SilentCameraCapture"
    }
}
