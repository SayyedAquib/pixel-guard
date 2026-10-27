package com.pixelguard.applock.domain.usecase

import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object SessionManager {

    // Maps packageName -> unlock timestamp (in milliseconds)
    private val unlockedSessions = ConcurrentHashMap<String, Long>()

    // Maps packageName -> exit timestamp (in milliseconds) when the user switched away
    private val exitTimestamps = ConcurrentHashMap<String, Long>()

    // Special lock frequency value: stays unlocked while the screen remains ON
    const val GRACE_UNTIL_SCREEN_OFF = -2

    // Quick-switch multitasking buffer for "Immediately" mode (e.g. copying OTPs, links, 2FA codes)
    const val QUICK_SWITCH_BUFFER_SECONDS = 15

    // Per-package failed attempt counters (persist across LockActivity recreations)
    private val failedAttempts = ConcurrentHashMap<String, Int>()

    // Per-package lockout timestamp (in milliseconds) for brute-force rate limiting
    private val lockoutTimestamps = ConcurrentHashMap<String, Long>()

    // Monotonic Clock Provider (SEC-002):
    // android.os.SystemClock.elapsedRealtime() is strictly monotonic and immune to user clock adjustments.
    // Falls back to System.currentTimeMillis() if unmocked (0L) on pure JVM unit tests.
    private var timeProvider: () -> Long = {
        try {
            val monotonic = android.os.SystemClock.elapsedRealtime()
            if (monotonic > 0L) monotonic else System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    fun now(): Long = timeProvider()

    internal fun setTimeProviderForTest(provider: () -> Long) {
        timeProvider = provider
    }

    // Current package currently being viewed/unlocked
    @Volatile
    var currentForegroundPackage: String? = null

    // Pause protection state (e.g. from Quick Settings Tile)
    @Volatile
    private var pauseUntilTimestamp: Long = 0L

    fun pauseProtection(durationMinutes: Int) {
        pauseUntilTimestamp = if (durationMinutes < 0) {
            Long.MAX_VALUE
        } else {
            now() + (durationMinutes * 60 * 1000L)
        }
    }

    internal fun pauseProtectionUntilForTest(timestampMs: Long) {
        pauseUntilTimestamp = timestampMs
    }

    fun resumeProtection() {
        pauseUntilTimestamp = 0L
    }

    fun isPaused(): Boolean {
        return now() < pauseUntilTimestamp
    }

    fun getRemainingPauseMinutes(): Int {
        if (pauseUntilTimestamp == Long.MAX_VALUE) return -1
        val diff = pauseUntilTimestamp - now()
        return if (diff > 0) ((diff / 60000) + 1).toInt() else 0
    }

    fun isNightLockdownActive(
        scheduleEnabled: Boolean,
        startHour: Int,
        endHour: Int,
        currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Boolean {
        if (!scheduleEnabled) return false
        return if (startHour > endHour) {
            currentHour >= startHour || currentHour < endHour
        } else {
            currentHour in startHour until endHour
        }
    }

    fun getEffectiveGracePeriod(
        packageName: String,
        defaultGraceSeconds: Int,
        isNightLockdown: Boolean,
        isPerAppGraceEnabled: Boolean,
        appGracePeriodsJson: String
    ): Int {
        if (isNightLockdown) return 0 // Maximum lockdown at night

        if (!isPerAppGraceEnabled) return defaultGraceSeconds

        return try {
            val json = JSONObject(appGracePeriodsJson)
            if (json.has(packageName)) json.getInt(packageName) else defaultGraceSeconds
        } catch (_: Exception) {
            defaultGraceSeconds
        }
    }

    fun isTemporarilyUnlocked(packageName: String, effectiveGraceSeconds: Int): Boolean {
        if (isPaused()) return true // Protection paused via Quick Settings tile

        val unlockTime = unlockedSessions[packageName] ?: return false

        // 1. If currently in foreground, the app is actively in use — NEVER relock while using!
        if (currentForegroundPackage == packageName) {
            return true
        }

        val nowTime = now()
        val exitTime = exitTimestamps[packageName]

        // 2. Initial launch transition buffer (3s) ONLY before the user has navigated away (exitTime == null)
        if (exitTime == null && (nowTime - unlockTime) in 0 until 3000L) {
            return true
        }

        // 3. Until Screen Off mode (-2): Stays unlocked as long as screen is on (onScreenOff wipes everything)
        if (effectiveGraceSeconds == GRACE_UNTIL_SCREEN_OFF) {
            return true
        }

        // 4. "Immediately" mode (0s or negative other than -2):
        // Allow a 15-second Quick-Switch buffer for copying OTPs/links without re-locking
        val effectiveSeconds = if (effectiveGraceSeconds <= 0) QUICK_SWITCH_BUFFER_SECONDS else effectiveGraceSeconds

        // 5. Grace period counts down from when the user exited the app
        val baseExitTime = exitTime ?: unlockTime
        val elapsedSinceExit = (nowTime - baseExitTime) / 1000
        val isUnlocked = elapsedSinceExit < effectiveSeconds
        if (!isUnlocked) {
            unlockedSessions.remove(packageName)
        }
        return isUnlocked
    }

    fun isRecentlyUnlocked(packageName: String, thresholdMs: Long = 3000L): Boolean {
        val unlockTime = unlockedSessions[packageName] ?: return false
        val diff = now() - unlockTime
        return diff in 0 until thresholdMs
    }

    fun markAppUnlocked(packageName: String) {
        val nowTime = now()
        unlockedSessions[packageName] = nowTime
        exitTimestamps.remove(packageName)
        currentForegroundPackage = packageName
    }

    fun markAppResumed(packageName: String) {
        currentForegroundPackage = packageName
        exitTimestamps.remove(packageName)
    }

    internal fun markAppUnlockedForTest(
        packageName: String,
        timestampMs: Long,
        inForeground: Boolean = true
    ) {
        unlockedSessions[packageName] = timestampMs
        if (inForeground) {
            currentForegroundPackage = packageName
            exitTimestamps.remove(packageName)
        } else {
            currentForegroundPackage = null
            exitTimestamps[packageName] = timestampMs
        }
    }

    internal fun markAppExitedForTest(packageName: String, exitTimestampMs: Long) {
        currentForegroundPackage = null
        exitTimestamps[packageName] = exitTimestampMs
    }

    private val TRANSIENT_LAUNCHER_PACKAGES = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.google.android.setupwizard",
        "com.android.systemui",
        "com.google.android.systemui"
    )

    fun isTransientLauncher(packageName: String): Boolean {
        return TRANSIENT_LAUNCHER_PACKAGES.contains(packageName) ||
                packageName.endsWith(".launcher") ||
                packageName.endsWith(".launcher3")
    }

    fun onAppSwitched(newPackageName: String, effectiveGraceSeconds: Int) {
        if (currentForegroundPackage != newPackageName) {
            currentForegroundPackage?.let { prevPkg ->
                if (unlockedSessions.containsKey(prevPkg)) {
                    val nowTime = now()
                    val inTransition = isRecentlyUnlocked(prevPkg, 3000L) && isTransientLauncher(newPackageName)
                    if (!inTransition) {
                        exitTimestamps[prevPkg] = nowTime
                    }
                }
            }
            currentForegroundPackage = newPackageName
        }
    }

    /**
     * Absolute Screen-Off Relock: The moment the power button is pressed or screen turns off,
     * immediately wipe ALL unlocked sessions across all apps, completely ignoring any remaining grace periods.
     */
    fun onScreenOff() {
        clearAllSessions()
    }

    fun clearAllSessions() {
        unlockedSessions.clear()
        exitTimestamps.clear()
        currentForegroundPackage = null
    }

    fun lockPackage(packageName: String) {
        unlockedSessions.remove(packageName)
        exitTimestamps.remove(packageName)
        if (currentForegroundPackage == packageName) {
            currentForegroundPackage = null
        }
    }

    // --- Failed Attempts & Rate Limiting API (SEC-103 Global Rate Limit & PG-SEC-02 Reboot Epoch Sync) ---

    const val GLOBAL_PACKAGE = "__GLOBAL__"

    var onLockoutChanged: ((attempts: Int, durationRemainingMs: Long, wallClockExpiryMs: Long) -> Unit)? = null

    /**
     * PG-SEC-02: Sync persisted lockout across device reboots safely using wall-clock expiration
     * and duration sanity checks, eliminating monotonic epoch mismatch DoS.
     */
    fun syncPersistedLockout(
        attempts: Int,
        wallClockExpiryMs: Long,
        durationRemainingMs: Long = 0L,
        currentWallTime: Long = System.currentTimeMillis()
    ) {
        if (attempts > 0) {
            failedAttempts[GLOBAL_PACKAGE] = attempts
        }
        if (wallClockExpiryMs > currentWallTime) {
            val remainingMs = wallClockExpiryMs - currentWallTime
            // Sanity check: progressive lockout max is 5 minutes (300,000 ms).
            // Guard against impossible future clock jumps or corrupted timestamps.
            val effectiveDuration = if (remainingMs in 1..300_000L) {
                if (durationRemainingMs in 1..remainingMs) durationRemainingMs else remainingMs
            } else {
                // Out of range future clock jump: Fail closed with maximum 5-minute penalty (M-11)
                300_000L
            }
            lockoutTimestamps[GLOBAL_PACKAGE] = now() + effectiveDuration
        } else if (durationRemainingMs > 0L) {
            // Clock was rolled backward behind currentWallTime: enforce durationRemainingMs (M-11)
            val clamped = durationRemainingMs.coerceIn(1L, 300_000L)
            lockoutTimestamps[GLOBAL_PACKAGE] = now() + clamped
        }
    }

    fun syncPersistedLockout(attempts: Int, untilMs: Long) {
        syncPersistedLockout(attempts, untilMs, 0L, System.currentTimeMillis())
    }

    fun incrementFailedAttempts(packageName: String): Int {
        val pkgCount = (failedAttempts[packageName] ?: 0) + 1
        failedAttempts[packageName] = pkgCount

        val globalCount = (failedAttempts[GLOBAL_PACKAGE] ?: 0) + 1
        failedAttempts[GLOBAL_PACKAGE] = globalCount
        val nowTime = now()

        val penalty = when {
            globalCount >= 10 -> 300_000L // 5 minutes progressive lockout
            globalCount >= 8 -> 60_000L   // 1 minute
            globalCount >= 5 -> 30_000L   // 30 seconds
            else -> 0L
        }

        if (penalty > 0L) {
            val lockoutUntil = nowTime + penalty
            lockoutTimestamps[GLOBAL_PACKAGE] = lockoutUntil
            lockoutTimestamps[packageName] = lockoutUntil
            val wallClockExpiry = System.currentTimeMillis() + penalty
            onLockoutChanged?.invoke(globalCount, penalty, wallClockExpiry)
        } else {
            onLockoutChanged?.invoke(globalCount, 0L, 0L)
        }
        return globalCount
    }

    fun getFailedAttempts(packageName: String): Int {
        return failedAttempts[packageName] ?: 0
    }

    fun getGlobalFailedAttempts(): Int {
        return failedAttempts[GLOBAL_PACKAGE] ?: 0
    }

    fun getRemainingLockoutSeconds(packageName: String): Int {
        val globalUntil = lockoutTimestamps[GLOBAL_PACKAGE] ?: 0L
        val pkgUntil = lockoutTimestamps[packageName] ?: 0L
        val effectiveUntil = maxOf(globalUntil, pkgUntil)
        if (effectiveUntil <= 0L) return 0
        val remainingMs = effectiveUntil - now()
        return if (remainingMs > 0) ((remainingMs + 999) / 1000).toInt() else 0
    }

    fun isLockedOut(packageName: String): Boolean {
        return getRemainingLockoutSeconds(packageName) > 0
    }

    fun getAttemptsUntilLockout(packageName: String): Int {
        val current = failedAttempts[GLOBAL_PACKAGE] ?: failedAttempts[packageName] ?: 0
        return if (current < 5) 5 - current else 0
    }

    fun resetFailedAttempts(packageName: String) {
        failedAttempts.remove(packageName)
        failedAttempts.remove(GLOBAL_PACKAGE)
        lockoutTimestamps.remove(packageName)
        lockoutTimestamps.remove(GLOBAL_PACKAGE)
        onLockoutChanged?.invoke(0, 0L, 0L)
    }

    internal fun resetAllLockoutsForTest() {
        failedAttempts.clear()
        lockoutTimestamps.clear()
        onLockoutChanged?.invoke(0, 0L, 0L)
    }

    internal fun setLockoutForTest(packageName: String, durationMs: Long) {
        val lockoutUntil = now() + durationMs
        lockoutTimestamps[packageName] = lockoutUntil
        lockoutTimestamps[GLOBAL_PACKAGE] = lockoutUntil
    }
}
