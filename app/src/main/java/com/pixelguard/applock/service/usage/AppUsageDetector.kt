package com.pixelguard.applock.service.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class UsageEventRecord(
    val packageName: String,
    val eventType: Int,
    val timestamp: Long
)

object AppUsageDetector {

    const val PLAY_STORE_PACKAGE = "com.android.vending"

    val SETTINGS_AND_INSTALLER_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    val KNOWN_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.as",
        "com.android.intentresolver",
        "com.google.android.providers.media.module",
        "com.google.android.photopicker",
        "com.android.photopicker",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin"
    )

    /**
     * Resolves the latest foreground package from a collection of usage event records.
     */
    fun resolveResumedPackage(events: List<UsageEventRecord>): String? {
        return events
            .filter { it.eventType == UsageEvents.Event.ACTIVITY_RESUMED }
            .maxByOrNull { it.timestamp }
            ?.packageName
    }

    /**
     * Determines whether the given package is configured as locked under current user preferences.
     */
    fun isTargetLocked(
        packageName: String,
        lockedPackages: Set<String>,
        isSettingsProtected: Boolean,
        isPlayStoreProtected: Boolean
    ): Boolean {
        if (lockedPackages.contains(packageName)) return true
        if (isSettingsProtected && SETTINGS_AND_INSTALLER_PACKAGES.contains(packageName)) return true
        if (isPlayStoreProtected && packageName == PLAY_STORE_PACKAGE) return true
        return false
    }

    /**
     * Identifies known system framework and transient UI packages that should not be intercepted.
     */
    fun isKnownSystemOrTransient(packageName: String): Boolean {
        return KNOWN_SYSTEM_PACKAGES.contains(packageName)
    }

    /**
     * Identifies home launcher or recents UI packages.
     */
    fun isLauncherOrRecents(context: Context, packageName: String): Boolean {
        if (packageName == "com.google.android.apps.nexuslauncher" ||
            packageName == "com.android.launcher3" ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.systemui") {
            return true
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = try {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }

        return packageName == defaultLauncher
    }

    /**
     * Checks whether PACKAGE_USAGE_STATS permission has been granted via AppOps.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Queries Android UsageStatsManager for the most recently resumed top-level package.
     */
    fun queryLatestResumedPackage(context: Context, lookbackWindowMs: Long = 3000L): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - lookbackWindowMs, now)
        } catch (_: Exception) {
            null
        } ?: return null

        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime: Long = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTime) {
                latestPackage = event.packageName
                latestTime = event.timeStamp
            }
        }
        return latestPackage
    }
}
