package com.pixelguard.applock.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SecuritySnapshot(
    val lockedPackages: Set<String>,
    val isSettingsProtected: Boolean = false,
    val isPlayStoreProtected: Boolean = false,
    val isScheduleEnabled: Boolean = false,
    val scheduleStartHour: Int = 23,
    val scheduleEndHour: Int = 7,
    val gracePeriodSeconds: Int = 0,
    val isPerAppGraceEnabled: Boolean = true,
    val appGracePeriodsJson: String = "{}"
)

object SecuritySnapshotManager {
    private const val SNAPSHOT_FILE = "security_snapshot.json"

    fun loadSnapshot(context: Context): SecuritySnapshot? = synchronized(this) {
        val file = File(context.filesDir, SNAPSHOT_FILE)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val pkgsArray = json.getJSONArray("lockedPackages")
            val pkgs = mutableSetOf<String>()
            for (i in 0 until pkgsArray.length()) {
                pkgs.add(pkgsArray.getString(i))
            }
            SecuritySnapshot(
                lockedPackages = pkgs,
                isSettingsProtected = json.optBoolean("isSettingsProtected", false),
                isPlayStoreProtected = json.optBoolean("isPlayStoreProtected", false),
                isScheduleEnabled = json.optBoolean("isScheduleEnabled", false),
                scheduleStartHour = json.optInt("scheduleStartHour", 23),
                scheduleEndHour = json.optInt("scheduleEndHour", 7),
                gracePeriodSeconds = json.optInt("gracePeriodSeconds", 0),
                isPerAppGraceEnabled = json.optBoolean("isPerAppGraceEnabled", true),
                appGracePeriodsJson = json.optString("appGracePeriodsJson", "{}")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveSnapshot(context: Context, snapshot: SecuritySnapshot) = synchronized(this) {
        var tempFile: File? = null
        try {
            val json = JSONObject().apply {
                put("lockedPackages", JSONArray(snapshot.lockedPackages.toList()))
                put("isSettingsProtected", snapshot.isSettingsProtected)
                put("isPlayStoreProtected", snapshot.isPlayStoreProtected)
                put("isScheduleEnabled", snapshot.isScheduleEnabled)
                put("scheduleStartHour", snapshot.scheduleStartHour)
                put("scheduleEndHour", snapshot.scheduleEndHour)
                put("gracePeriodSeconds", snapshot.gracePeriodSeconds)
                put("isPerAppGraceEnabled", snapshot.isPerAppGraceEnabled)
                put("appGracePeriodsJson", snapshot.appGracePeriodsJson)
            }
            tempFile = File(context.filesDir, "$SNAPSHOT_FILE.${java.util.UUID.randomUUID()}.tmp")
            val targetFile = File(context.filesDir, SNAPSHOT_FILE)
            tempFile.writeText(json.toString())
            try {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
        } finally {
            try {
                tempFile?.let { if (it.exists()) it.delete() }
            } catch (_: Exception) {}
        }
    }
}

