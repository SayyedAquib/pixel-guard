package com.pixelguard.applock.domain.model

/**
 * Explicit state machine for brute-force rate limiting and progressive lockouts.
 *
 * Models failed authentication attempts and progressive penalty tiers explicitly:
 * - 0 attempts: Unrestricted
 * - 1..4 attempts: Warning (amber caution badge when 5 - attempts <= 2)
 * - 5..7 attempts: Soft lockout (30s)
 * - 8..9 attempts: Medium lockout (60s)
 * - 10+ attempts: Extended lockout (300s)
 */
sealed interface RateLimitState {
    val failedAttempts: Int

    /**
     * Normal state. No recent failed attempts.
     */
    data object Unrestricted : RateLimitState {
        override val failedAttempts: Int = 0
    }

    /**
     * 1 to 4 failed attempts. Keypad remains active.
     * Pre-lockout warning is indicated if attemptsUntilLockout <= 2.
     */
    data class Warning(
        override val failedAttempts: Int,
        val remainingAttemptsUntilLockout: Int
    ) : RateLimitState {
        val isCautionBadgeVisible: Boolean get() = remainingAttemptsUntilLockout in 1..2
    }

    /**
     * Locked out due to consecutive failed attempts. Keypad input is rejected.
     */
    sealed interface LockedOut : RateLimitState {
        val remainingSeconds: Int
        val totalPenaltySeconds: Int

        /**
         * Tier 1: 5-7 failed attempts -> 30 seconds penalty.
         */
        data class SoftLockout(
            override val failedAttempts: Int,
            override val remainingSeconds: Int
        ) : LockedOut {
            override val totalPenaltySeconds: Int = 30
        }

        /**
         * Tier 2: 8-9 failed attempts -> 60 seconds penalty.
         */
        data class MediumLockout(
            override val failedAttempts: Int,
            override val remainingSeconds: Int
        ) : LockedOut {
            override val totalPenaltySeconds: Int = 60
        }

        /**
         * Tier 3: 10+ failed attempts -> 300 seconds (5 minutes) penalty.
         */
        data class ExtendedLockout(
            override val failedAttempts: Int,
            override val remainingSeconds: Int
        ) : LockedOut {
            override val totalPenaltySeconds: Int = 300
        }
    }

    val isLockedOut: Boolean get() = this is LockedOut

    val remainingLockoutSeconds: Int get() = when (this) {
        is LockedOut -> remainingSeconds
        else -> 0
    }

    val attemptsUntilLockout: Int get() = when (this) {
        is Warning -> remainingAttemptsUntilLockout
        is LockedOut -> 0
        is Unrestricted -> 5
    }
}
