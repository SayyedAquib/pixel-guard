package com.pixelguard.applock.service.camera

/**
 * Explicit state machine for headless Camera2 front sensor capture pipeline.
 *
 * Replaces implicit lifecycle states governed across AtomicBooleans and nullable handles:
 * Idle -> InCooldown | OpeningCamera -> ConfiguringSession -> CapturingStill -> ProcessingImage -> Completed / Failed
 */
sealed interface CameraCaptureState {

    /**
     * Camera hardware is ready and idle. Cooldown has elapsed.
     */
    data object Idle : CameraCaptureState

    /**
     * Within the 10-second thermal protection cooldown window. Captures are throttled.
     */
    data class InCooldown(val elapsedMs: Long, val cooldownMs: Long = 10_000L) : CameraCaptureState

    /**
     * Asynchronously opening the front camera sensor via CameraManager.
     */
    data class OpeningCamera(val cameraId: String) : CameraCaptureState

    /**
     * CameraDevice opened; creating CameraCaptureSession with ImageReader surface.
     */
    data object ConfiguringSession : CameraCaptureState

    /**
     * Capture session configured; still capture request in flight.
     */
    data object CapturingStill : CameraCaptureState

    /**
     * Image frame received from ImageReader; performing AES-256-GCM encryption & writing to disk.
     */
    data class ProcessingImage(val outputFileName: String) : CameraCaptureState

    /**
     * Still photo captured, encrypted, and written to disk successfully.
     */
    data class Completed(val outputFileAbsolutePath: String) : CameraCaptureState

    /**
     * Capture pipeline failed or timed out. Hardware resources released cleanly.
     */
    data class Failed(val reason: String, val throwable: Throwable? = null) : CameraCaptureState

    val isBusy: Boolean
        get() = this is OpeningCamera ||
                this is ConfiguringSession ||
                this is CapturingStill ||
                this is ProcessingImage
}
