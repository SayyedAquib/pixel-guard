package com.pixelguard.applock.service

/**
 * Explicit state machine for AppLockForegroundService background polling and interception loop.
 *
 * Replaces implicit flags:
 * - isCachesReady
 * - isScreenInteractive
 * - monitoringJob != null
 * - lastPackage
 */
sealed interface ForegroundMonitoringState {

    /**
     * Service starting up; synchronizing initial SecuritySnapshot and DataStore flows.
     */
    data object Uninitialized : ForegroundMonitoringState

    /**
     * Screen is turned OFF (Intent.ACTION_SCREEN_OFF). Monitoring coroutines suspended
     * to conserve battery and allow CPU deep sleep.
     */
    data class StandbyScreenOff(val lastForegroundPackage: String = "") : ForegroundMonitoringState

    /**
     * Screen is interactive (ON); actively polling UsageStatsManager for target app transitions.
     */
    data class Active(
        val lastForegroundPackage: String = "",
        val isCachesReady: Boolean = false,
        val activeTargetLocked: String? = null
    ) : ForegroundMonitoringState

    /**
     * AppOps usage access permission was revoked by the user or system.
     * Monitoring loop is in backoff state (5s delay).
     */
    data object PermissionRevoked : ForegroundMonitoringState

    /**
     * Service is being destroyed or stopped.
     */
    data object Stopped : ForegroundMonitoringState
}
