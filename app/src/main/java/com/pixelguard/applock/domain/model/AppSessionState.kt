package com.pixelguard.applock.domain.model

/**
 * Explicit state machine for per-application authentication sessions.
 *
 * Eliminates implicit state deductions based on combinations of timestamp maps
 * (unlockedSessions, exitTimestamps, currentForegroundPackage).
 */
sealed interface AppSessionState {

    /**
     * Target application is locked. User must authenticate to gain access.
     */
    data object Locked : AppSessionState

    /**
     * Target application is actively in the foreground and in use by the user.
     * Never relocks while in active use.
     */
    data class ActiveForeground(val packageName: String) : AppSessionState

    /**
     * Initial 3-second transition buffer immediately following unlock.
     * Protects against transient system launcher dismissals and activity crossfades.
     */
    data class TransitionBuffer(
        val packageName: String,
        val elapsedMs: Long,
        val bufferMs: Long = 3000L
    ) : AppSessionState

    /**
     * Application was exited, but remains temporarily unlocked within its configured
     * grace period (or 15s quick-switch buffer for "Immediately" mode).
     */
    data class GracePeriod(
        val packageName: String,
        val elapsedSeconds: Long,
        val totalGraceSeconds: Int,
        val remainingSeconds: Long
    ) : AppSessionState

    /**
     * Grace period or quick-switch buffer has elapsed since exiting.
     * Evaluates to false and triggers session cleanup.
     */
    data class Expired(
        val packageName: String,
        val elapsedSinceExitSeconds: Long,
        val allowedSeconds: Int
    ) : AppSessionState

    /**
     * Entire protection system is temporarily paused via Quick Settings tile.
     */
    data class Paused(val remainingMinutes: Int) : AppSessionState

    val isUnlocked: Boolean
        get() = when (this) {
            is ActiveForeground,
            is TransitionBuffer,
            is GracePeriod,
            is Paused -> true
            is Locked,
            is Expired -> false
        }
}
