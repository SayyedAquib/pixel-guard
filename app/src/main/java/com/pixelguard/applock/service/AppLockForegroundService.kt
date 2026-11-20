package com.pixelguard.applock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.BuildConfig
import com.pixelguard.applock.R
import com.pixelguard.applock.data.local.SecuritySnapshotManager
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.domain.usecase.SessionManager
import com.pixelguard.applock.service.usage.AppUsageDetector
import com.pixelguard.applock.ui.lock.LockActivity
import com.pixelguard.applock.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppLockForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitoringJob: Job? = null

    private val _monitoringState = MutableStateFlow<ForegroundMonitoringState>(ForegroundMonitoringState.Uninitialized)
    val monitoringState: StateFlow<ForegroundMonitoringState> = _monitoringState.asStateFlow()

    private var lastPackage: String = ""
    private var cachedLockedPackages: Set<String> = emptySet()
    private var cachedPreferences: LockPreferences = LockPreferences()
    private var isCachesReady: Boolean = false
    private var isScreenInteractive: Boolean = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Screen OFF event: pausing detection loop")
                    isScreenInteractive = false
                    monitoringJob?.cancel()
                    monitoringJob = null
                    _monitoringState.value = ForegroundMonitoringState.StandbyScreenOff(lastPackage)
                    SessionManager.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Screen ON event: resuming detection loop")
                    isScreenInteractive = true
                    _monitoringState.value = ForegroundMonitoringState.Active(lastPackage, isCachesReady)
                    if (monitoringJob == null || monitoringJob?.isActive == false) {
                        startMonitoringLoop()
                    }
                }
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

        // 2. Observe repository updates dynamically
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

        // 3. Screen interactivity state
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = powerManager?.isInteractive ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (monitoringJob == null || monitoringJob?.isActive == false) {
            startMonitoringLoop()
        }
        return START_STICKY
    }

    fun startMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!AppUsageDetector.hasUsageStatsPermission(this@AppLockForegroundService)) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Usage access revoked. Backing off monitoring loop.")
                    }
                    _monitoringState.value = ForegroundMonitoringState.PermissionRevoked
                    delay(5000L)
                    continue
                }
                if (isScreenInteractive) {
                    _monitoringState.value = ForegroundMonitoringState.Active(lastPackage, isCachesReady)
                    val currentPackage = AppUsageDetector.queryLatestResumedPackage(this@AppLockForegroundService)
                    if (currentPackage != null) {
                        launch(Dispatchers.Main) {
                            processForegroundPackage(currentPackage)
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Core foreground evaluation seam (tested independently).
     * Returns true if LockActivity was triggered.
     */
    fun processForegroundPackage(packageName: String): Boolean {
        if (!isScreenInteractive) return false

        // 1. Ignore Pixel Guard itself
        if (packageName == applicationContext.packageName) {
            return false
        }

        // 2. Ignore known system & transient packages
        if (AppUsageDetector.isKnownSystemOrTransient(packageName)) {
            return false
        }

        // 3. Ignore launcher / recents, record app switch for grace period
        if (AppUsageDetector.isLauncherOrRecents(this, packageName)) {
            if (lastPackage.isNotEmpty() && lastPackage != packageName) {
                SessionManager.onAppSwitched(packageName, cachedPreferences.gracePeriodSeconds)
            }
            lastPackage = packageName
            return false
        }

        // 4. Cold-start fail-closed & lock target resolution
        val isLocked = if (!isCachesReady) {
            true
        } else {
            AppUsageDetector.isTargetLocked(
                packageName = packageName,
                lockedPackages = cachedLockedPackages,
                isSettingsProtected = cachedPreferences.isSettingsProtected,
                isPlayStoreProtected = cachedPreferences.isPlayStoreProtected
            )
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "processForegroundPackage: $packageName, isLocked=$isLocked")
        }

        var lockTriggered = false
        if (isLocked) {
            val isNight = SessionManager.isNightLockdownActive(
                scheduleEnabled = cachedPreferences.isScheduleEnabled,
                startHour = cachedPreferences.scheduleStartHour,
                endHour = cachedPreferences.scheduleEndHour
            )
            val effectiveGrace = SessionManager.getEffectiveGracePeriod(
                packageName = packageName,
                defaultGraceSeconds = cachedPreferences.gracePeriodSeconds,
                isNightLockdown = isNight,
                isPerAppGraceEnabled = cachedPreferences.isPerAppGraceEnabled,
                appGracePeriodsJson = cachedPreferences.appGracePeriodsJson
            )

            val isUnlocked = SessionManager.isTemporarilyUnlocked(packageName, effectiveGrace)
            if (!isUnlocked) {
                if (!LockActivity.isShowing && !(LockActivity.isLaunching && LockActivity.activeTargetPackage == packageName)) {
                    lockTriggered = launchLockActivity(packageName)
                }
            } else {
                SessionManager.markAppResumed(packageName)
            }
        } else {
            if (packageName != lastPackage) {
                SessionManager.onAppSwitched(packageName, cachedPreferences.gracePeriodSeconds)
            }
        }

        lastPackage = packageName
        return lockTriggered
    }

    @androidx.annotation.VisibleForTesting
    internal var overlayPermissionChecker: (Context) -> Boolean = { Settings.canDrawOverlays(it) }

    private fun launchLockActivity(packageName: String): Boolean {
        if (!overlayPermissionChecker(this)) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Overlay permission (SYSTEM_ALERT_WINDOW) not granted; cannot launch lock activity")
            }
            return false
        }
        if (LockActivity.state.isShowing || (LockActivity.state.isLaunching && LockActivity.state.currentTargetPackage == packageName)) {
            return false
        }
        LockActivity.transitionToLaunching(packageName)
        val lockIntent = Intent(this, LockActivity::class.java).apply {
            putExtra(LockActivity.EXTRA_TARGET_PACKAGE, packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        return try {
            startActivity(lockIntent)
            true
        } catch (e: Exception) {
            LockActivity.transitionToIdle()
            if (BuildConfig.DEBUG) android.util.Log.e("AppLockFGS", "Failed to launch LockActivity: ${e.message}")
            false
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppLockApplication.CHANNEL_ID_PROTECTION)
            .setContentTitle(getString(R.string.foreground_service_notification_title))
            .setContentText(getString(R.string.foreground_service_notification_text))
            .setSmallIcon(R.drawable.ic_lock_status)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _monitoringState.value = ForegroundMonitoringState.Stopped
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        monitoringJob?.cancel()
        scope.cancel()
    }

    // --- Testing Hooks ---
    internal fun setCachedLockedPackagesForTest(packages: Set<String>) {
        cachedLockedPackages = packages
        isCachesReady = true
    }

    internal fun setCachedPreferencesForTest(preferences: LockPreferences) {
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

    internal fun setIsScreenInteractiveForTest(interactive: Boolean) {
        isScreenInteractive = interactive
    }

    internal fun getScreenReceiverForTest(): BroadcastReceiver = screenReceiver

    companion object {
        const val NOTIFICATION_ID = 1001
        const val POLL_INTERVAL_MS = 200L
        private const val TAG = "AppLockForegroundService"

        fun start(context: Context) {
            val intent = Intent(context, AppLockForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppLockForegroundService::class.java))
        }
    }
}
