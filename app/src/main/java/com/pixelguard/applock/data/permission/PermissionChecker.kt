package com.pixelguard.applock.data.permission

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.pixelguard.applock.service.usage.AppUsageDetector
import com.pixelguard.applock.ui.main.PermissionStatus

interface PermissionChecker {
    fun checkPermissions(): PermissionStatus
}

object NoOpPermissionChecker : PermissionChecker {
    override fun checkPermissions(): PermissionStatus = PermissionStatus()
}

class SystemPermissionChecker(private val context: Context) : PermissionChecker {
    override fun checkPermissions(): PermissionStatus {
        val hasOverlay = Settings.canDrawOverlays(context)
        val usageGranted = AppUsageDetector.hasUsageStatsPermission(context)
        val notificationEnabled = isNotificationServiceEnabled(context)

        return PermissionStatus(
            hasOverlayPermission = hasOverlay,
            hasUsagePermission = usageGranted,
            hasAccessibilityPermission = false,
            hasNotificationPermission = notificationEnabled
        )
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(flat)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val component = ComponentName.unflattenFromString(componentNameString)
            if (component?.packageName == context.packageName) {
                return true
            }
        }
        return false
    }
}
