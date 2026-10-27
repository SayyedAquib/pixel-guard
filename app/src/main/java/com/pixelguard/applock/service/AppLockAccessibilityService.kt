package com.pixelguard.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.BuildConfig
import com.pixelguard.applock.data.local.SecuritySnapshotManager
import com.pixelguard.applock.domain.usecase.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AppLockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastPackage: String = ""

    // In-memory cache for zero-latency accessibility interception
    private var cachedLockedPackages: Set<String> = emptySet()
    private var cachedPreferences: com.pixelguard.applock.data.model.LockPreferences = com.pixelguard.applock.data.model.LockPreferences()
    private var isCachesReady: Boolean = false

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Screen off event: resetting foreground session")
                SessionManager.onScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Cold-start fail-closed protection (C-2): Load synchronous disk snapshot immediately
        val snapshot = SecuritySnapshotManager.loadSnapshot(this)
        if (snapshot != null) {
            cachedLockedPackages = snapshot.lockedPackages
            cachedPreferences = cachedPreferences.copy(
                isSettingsProtected = snapshot.isSettingsProtected,
                isPlayStoreProtected = snapshot.isPlayStoreProtected,
                isScheduleEnabled = snapshot.isScheduleEnabled,
                scheduleStartHour = snapshot.scheduleStartHour,
                scheduleEndHour = snapshot.scheduleEndHour,
                gracePeriodSeconds = snapshot.gracePeriodSeconds,
                isPerAppGraceEnabled = snapshot.isPerAppGraceEnabled,
                appGracePeriodsJson = snapshot.appGracePeriodsJson
            )
            isCachesReady = true
        }

        val repository = AppLockApplication.instance.repository
        scope.launch {
            repository.lockedPackages
                .catch { e ->
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error collecting locked packages", e)
                }
                .collect {
                    cachedLockedPackages = it
                    isCachesReady = true
                }
        }
        scope.launch {
            repository.preferences
                .catch { e ->
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error collecting preferences", e)
                }
                .collect {
                    cachedPreferences = it
                    isCachesReady = true
                }
        }

        val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
    }

    internal fun setCachedLockedPackagesForTest(packages: Set<String>) {
        cachedLockedPackages = packages
        isCachesReady = true
    }

    internal fun setCachedPreferencesForTest(preferences: com.pixelguard.applock.data.model.LockPreferences) {
        cachedPreferences = preferences
        isCachesReady = true
    }

    internal fun setCachesReadyForTest(ready: Boolean) {
        isCachesReady = ready
    }

    internal fun setLastPackageForTest(pkg: String) {
        lastPackage = pkg
    }

    internal fun getLastPackageForTest(): String = lastPackage

    internal fun getScreenOffReceiverForTest(): android.content.BroadcastReceiver = screenOffReceiver

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Only process actual window state changes (activity/task transitions).
        // TYPE_WINDOWS_CHANGED emits continuously for gesture navigation bar, keyboard,
        // and overlay layer changes which MUST NOT trigger app relocks or session resets.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // 2. Ignore transient system packages, launchers/recents, input methods, verified system credential prompts, and Pixel Guard itself
        if (isSystemOrTransientPackage(packageName) || isLauncherOrRecents(packageName) || isCredentialOrBiometricPrompt(packageName, className)) {
            if (isLauncherOrRecents(packageName)) {
                SessionManager.onAppSwitched(packageName, cachedPreferences.gracePeriodSeconds)
                lastPackage = packageName
            }
            return
        }

        val lockedPackages = cachedLockedPackages
        val preferences = cachedPreferences

        // 3. Cold-Start Fail-Closed Protection (C-2):
        // If caches have not received their initial emission and no disk snapshot exists,
        // treat untrusted application packages as locked to eliminate cold-start race conditions.
        val isLocked = if (!isCachesReady) {
            true
        } else {
            lockedPackages.contains(packageName) ||
                (preferences.isSettingsProtected && SETTINGS_AND_INSTALLER_PACKAGES.contains(packageName)) ||
                (preferences.isPlayStoreProtected && packageName == PLAY_STORE_PACKAGE)
        }

        if (BuildConfig.DEBUG) Log.i(TAG, "Window event: $packageName, class=$className, isLocked=$isLocked")

        if (isLocked) {
            val isNight = SessionManager.isNightLockdownActive(
                scheduleEnabled = preferences.isScheduleEnabled,
                startHour = preferences.scheduleStartHour,
                endHour = preferences.scheduleEndHour
            )
            val effectiveGrace = SessionManager.getEffectiveGracePeriod(
                packageName = packageName,
                defaultGraceSeconds = preferences.gracePeriodSeconds,
                isNightLockdown = isNight,
                isPerAppGraceEnabled = preferences.isPerAppGraceEnabled,
                appGracePeriodsJson = preferences.appGracePeriodsJson
            )

            val isUnlocked = SessionManager.isTemporarilyUnlocked(packageName, effectiveGrace)
            if (!isUnlocked) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Launching LockActivity for: $packageName (nightLockdown=$isNight, grace=$effectiveGrace)")
                val lockIntent = Intent(this@AppLockAccessibilityService, com.pixelguard.applock.ui.lock.LockActivity::class.java).apply {
                    putExtra(com.pixelguard.applock.ui.lock.LockActivity.EXTRA_TARGET_PACKAGE, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                startActivity(lockIntent)
            } else {
                if (BuildConfig.DEBUG) Log.i(TAG, "Package is temporarily unlocked: $packageName")
                SessionManager.markAppResumed(packageName)
            }
        } else {
            // Switched to an unlocked user application, home launcher, or recents
            if (BuildConfig.DEBUG) Log.i(TAG, "Switched to package: $packageName")
            SessionManager.onAppSwitched(packageName, preferences.gracePeriodSeconds)
        }

        lastPackage = packageName
    }

    private fun isProtectedSystemPackage(
        packageName: String,
        preferences: com.pixelguard.applock.data.model.LockPreferences
    ): Boolean {
        return (preferences.isSettingsProtected && SETTINGS_AND_INSTALLER_PACKAGES.contains(packageName)) ||
                (preferences.isPlayStoreProtected && packageName == PLAY_STORE_PACKAGE)
    }

    private fun isSystemOrTransientPackage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName || packageName == "android") return true

        // 1. Canonical System UI
        if (packageName == "com.android.systemui" || packageName == "com.google.android.systemui") return true

        // 2. Exact known platform & framework provider packages (SEC-003 & PG-SEC-09: eliminates rogue prefix bypasses)
        if (KNOWN_SYSTEM_PACKAGES.contains(packageName)) return true

        // 3. Dynamic verification for registered system Input Method Editors (keyboards)
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        try {
            val isEnabledIme = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
            if (isEnabledIme) return true
        } catch (_: Exception) {}

        return false
    }

    private fun isLauncherOrRecents(packageName: String): Boolean {
        if (packageName == "com.google.android.apps.nexuslauncher" ||
            packageName == "com.android.launcher3" ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.systemui") {
            return true
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = try {
            packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }

        return packageName == defaultLauncher
    }

    private fun isCredentialOrBiometricPrompt(packageName: String, className: String): Boolean {
        val isSystemProvider = packageName == "com.android.settings" ||
                packageName == "com.android.systemui" ||
                packageName == "com.google.android.systemui"
        if (!isSystemProvider) return false

        // PG-SEC-09: Explicitly deny settings management and biometric enrollment wizards
        // from bypassing the lock screen when com.android.settings is locked.
        if (className.contains("Enrollment", ignoreCase = true) ||
            className.contains("Settings", ignoreCase = true) ||
            className.contains("SubSettings", ignoreCase = true)) {
            return false
        }

        // Only allow genuine system confirmation dialogs (e.g. ConfirmDeviceCredentialActivity)
        return className.contains("ConfirmDeviceCredential", ignoreCase = true) ||
                className.contains("BiometricPrompt", ignoreCase = true)
    }

    override fun onInterrupt() {
        if (BuildConfig.DEBUG) Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        scope.cancel()
    }

    companion object {
        private const val TAG = "AppLockAccessibility"

        private val SETTINGS_AND_INSTALLER_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller"
        )

        private val KNOWN_SYSTEM_PACKAGES = setOf(
            "com.google.android.gms",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.as",
            "com.android.intentresolver",
            "com.google.android.providers.media.module",
            "com.android.providers.media.module",
            "com.google.android.photopicker",
            "com.android.photopicker",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin"
        )

        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
