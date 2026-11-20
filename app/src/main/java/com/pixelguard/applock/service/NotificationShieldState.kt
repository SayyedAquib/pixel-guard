package com.pixelguard.applock.service

/**
 * Explicit state machine for NotificationShieldService readiness and caching.
 *
 * Replaces implicit null checks on cachedPreferences and cachedLockedPackages.
 */
sealed interface NotificationShieldState {

    /**
     * Service created; waiting for initial snapshot or DataStore emissions.
     */
    data object Uninitialized : NotificationShieldState

    /**
     * Loaded synchronous SecuritySnapshot from disk (cold-start fail-closed protection).
     */
    data class SnapshotReady(val lockedPackagesCount: Int) : NotificationShieldState

    /**
     * Reactive DataStore flows are active and keeping memory caches 100% in sync.
     */
    data class Active(
        val lockedPackagesCount: Int,
        val isShieldEnabled: Boolean
    ) : NotificationShieldState

    val isReady: Boolean
        get() = this is SnapshotReady || this is Active
}
