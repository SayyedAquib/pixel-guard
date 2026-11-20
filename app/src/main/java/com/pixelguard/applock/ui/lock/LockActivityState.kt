package com.pixelguard.applock.ui.lock

/**
 * Explicit state machine for LockActivity lifecycle and presentation state.
 *
 * Replaces disconnected static companion booleans (isShowing, isLaunching, activeTargetPackage)
 * and instance flags (isBiometricPromptActive, hasCapturedSelfieThisSession) with an immutable,
 * unified state representation.
 */
sealed interface LockActivityState {

    /**
     * No lock activity is currently showing or preparing to launch.
     */
    data object Idle : LockActivityState

    /**
     * Intent dispatched; LockActivity is launching in background/transition.
     */
    data class Launching(val targetPackage: String) : LockActivityState

    /**
     * LockActivity is currently visible on screen in the foreground.
     */
    data class Visible(
        val targetPackage: String,
        val isBiometricActive: Boolean = false,
        val hasCapturedIntruderPhoto: Boolean = false
    ) : LockActivityState

    /**
     * LockActivity has completed authentication or exited to Home and is finishing.
     */
    data class Dismissed(val targetPackage: String) : LockActivityState

    val isShowing: Boolean
        get() = this is Visible

    val isLaunching: Boolean
        get() = this is Launching

    val currentTargetPackage: String?
        get() = when (this) {
            is Launching -> targetPackage
            is Visible -> targetPackage
            is Dismissed -> targetPackage
            is Idle -> null
        }
}
