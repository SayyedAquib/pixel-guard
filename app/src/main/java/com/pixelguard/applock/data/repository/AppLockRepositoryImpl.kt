package com.pixelguard.applock.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.pixelguard.applock.data.local.CryptoManager
import com.pixelguard.applock.data.local.PreferencesDataStore
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.model.getAppTags
import com.pixelguard.applock.data.model.getUserCustomTags
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class AppLockRepositoryImpl(
    private val context: Context,
    private val preferencesDataStore: PreferencesDataStore,
    private val cryptoManager: CryptoManager = CryptoManager()
) : AppLockRepository {

    override val lockedPackages: Flow<Set<String>> = preferencesDataStore.lockedPackagesFlow
    override val preferences: Flow<LockPreferences> = preferencesDataStore.preferencesFlow
    override val hasPinSetFlow: Flow<Boolean> = preferencesDataStore.pinHashFlow.map { it != null }

    private val _intruderRecords = MutableStateFlow<List<IntruderRecord>>(emptyList())
    override val intruderRecords: Flow<List<IntruderRecord>> = _intruderRecords.asStateFlow()

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @androidx.annotation.VisibleForTesting
    fun cancelScope() {
        repoScope.cancel()
    }

    init {
        repoScope.launch {
            loadIntruderRecords()
        }
        repoScope.launch {
            kotlinx.coroutines.flow.combine(lockedPackages, preferences) { pkgs, prefs ->
                com.pixelguard.applock.data.local.SecuritySnapshot(
                    lockedPackages = pkgs,
                    isSettingsProtected = prefs.isSettingsProtected,
                    isPlayStoreProtected = prefs.isPlayStoreProtected,
                    isScheduleEnabled = prefs.isScheduleEnabled,
                    scheduleStartHour = prefs.scheduleStartHour,
                    scheduleEndHour = prefs.scheduleEndHour,
                    gracePeriodSeconds = prefs.gracePeriodSeconds,
                    isPerAppGraceEnabled = prefs.isPerAppGraceEnabled,
                    appGracePeriodsJson = prefs.appGracePeriodsJson
                )
            }.collect { snapshot ->
                com.pixelguard.applock.data.local.SecuritySnapshotManager.saveSnapshot(context, snapshot)
            }
        }
    }

    override suspend fun setAppLocked(packageName: String, locked: Boolean) {
        preferencesDataStore.togglePackageLock(packageName, locked)
        if (locked) {
            com.pixelguard.applock.domain.usecase.SessionManager.lockPackage(packageName)
        }
    }

    override suspend fun lockAllSensitiveApps(sensitivePackages: List<String>) {
        val current = lockedPackages.first().toMutableSet()
        current.addAll(sensitivePackages)
        preferencesDataStore.setLockedPackages(current)
        sensitivePackages.forEach { com.pixelguard.applock.domain.usecase.SessionManager.lockPackage(it) }
    }

    override suspend fun unlockPackages(packages: List<String>) {
        val current = lockedPackages.first().toMutableSet()
        current.removeAll(packages.toSet())
        preferencesDataStore.setLockedPackages(current)
    }

    override suspend fun unlockAllApps() {
        preferencesDataStore.setLockedPackages(emptySet())
        com.pixelguard.applock.domain.usecase.SessionManager.clearAllSessions()
    }

    override suspend fun updatePreferences(preferences: LockPreferences) {
        preferencesDataStore.updatePreferences(preferences)
    }

    override suspend fun hasPinSet(): Boolean {
        return preferencesDataStore.pinHashFlow.first() != null
    }

    override suspend fun setPin(newPin: String): String = withContext(Dispatchers.Default) {
        if (cryptoManager.isWeakPin(newPin)) {
            throw IllegalArgumentException("Weak or invalid PIN pattern: 6-digit PIN required")
        }
        val pinSalt = cryptoManager.generateSalt()
        val pinHash = cryptoManager.hashPin(newPin, pinSalt)
        val pinSaltHex = cryptoManager.byteArrayToHex(pinSalt)

        val encryptedHash = cryptoManager.encryptString(pinHash, "pin_hash")
        val encryptedSalt = cryptoManager.encryptString(pinSaltHex, "pin_salt")

        val recoveryCode = cryptoManager.generateRecoveryCode()
        val normRecoveryCode = cryptoManager.normalizeRecoveryCode(recoveryCode)
        val recoverySalt = cryptoManager.generateSalt()
        val recoveryHash = cryptoManager.hashPin(normRecoveryCode, recoverySalt)
        val recoverySaltHex = cryptoManager.byteArrayToHex(recoverySalt)

        val encryptedRecoveryHash = cryptoManager.encryptString(recoveryHash, "recovery_hash")
        val encryptedRecoverySalt = cryptoManager.encryptString(recoverySaltHex, "recovery_salt")

        withContext(Dispatchers.IO) {
            preferencesDataStore.savePinWithRecovery(
                encryptedHash,
                encryptedSalt,
                encryptedRecoveryHash,
                encryptedRecoverySalt
            )
        }
        recoveryCode
    }

    override suspend fun verifyPinResult(inputPin: String, packageName: String?): VerifyPinResult = withContext(Dispatchers.Default) {
        val targetPackage = packageName ?: context.packageName
        if (com.pixelguard.applock.domain.usecase.SessionManager.isLockedOut(targetPackage)) {
            val remainingSec = com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(targetPackage)
            return@withContext VerifyPinResult.LockedOut(remainingSec)
        }

        val creds = preferencesDataStore.pinCredentialsFlow.first()
        val rawStoredHash = creds.pinHash
        val rawStoredSalt = creds.pinSalt
        if (rawStoredHash == null || rawStoredSalt == null) {
            return@withContext VerifyPinResult.WrongPin(0, 0)
        }

        val storedHash = try {
            cryptoManager.decryptString(rawStoredHash, "pin_hash")
        } catch (_: Exception) {
            return@withContext VerifyPinResult.Unrecoverable
        }

        val storedSaltHex = try {
            cryptoManager.decryptString(rawStoredSalt, "pin_salt")
        } catch (_: Exception) {
            return@withContext VerifyPinResult.Unrecoverable
        }

        val salt = try {
            cryptoManager.hexToByteArray(storedSaltHex, 16)
        } catch (_: Exception) {
            return@withContext VerifyPinResult.Unrecoverable
        }

        val computedHash = cryptoManager.hashPin(inputPin, salt)
        val isCorrect = cryptoManager.verifyHash(computedHash, storedHash)

        if (isCorrect) {
            com.pixelguard.applock.domain.usecase.SessionManager.resetFailedAttempts(targetPackage)
            VerifyPinResult.Success
        } else {
            com.pixelguard.applock.domain.usecase.SessionManager.incrementFailedAttempts(targetPackage)
            val remainingSec = com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(targetPackage)
            if (remainingSec > 0) {
                VerifyPinResult.LockedOut(remainingSec)
            } else {
                val attemptsUntilLockout = com.pixelguard.applock.domain.usecase.SessionManager.getAttemptsUntilLockout(targetPackage)
                VerifyPinResult.WrongPin(attemptsRemaining = attemptsUntilLockout, attemptsUntilLockout = attemptsUntilLockout)
            }
        }
    }

    override suspend fun verifyRecoveryCode(code: String): Boolean = withContext(Dispatchers.Default) {
        val normCode = cryptoManager.normalizeRecoveryCode(code)
        if (normCode.length != 16) return@withContext false

        val creds = preferencesDataStore.pinCredentialsFlow.first()
        val rawRecHash = creds.recoveryHash ?: return@withContext false
        val rawRecSalt = creds.recoverySalt ?: return@withContext false

        val storedRecHash = try {
            cryptoManager.decryptString(rawRecHash, "recovery_hash")
        } catch (_: Exception) {
            return@withContext false
        }

        val storedRecSaltHex = try {
            cryptoManager.decryptString(rawRecSalt, "recovery_salt")
        } catch (_: Exception) {
            return@withContext false
        }

        val salt = try {
            cryptoManager.hexToByteArray(storedRecSaltHex, 16)
        } catch (_: Exception) {
            return@withContext false
        }

        val computedHash = cryptoManager.hashPin(normCode, salt)
        cryptoManager.verifyHash(computedHash, storedRecHash)
    }

    override suspend fun resetWithRecoveryCode(code: String): Boolean = withContext(Dispatchers.Default) {
        if (!verifyRecoveryCode(code)) return@withContext false
        withContext(Dispatchers.IO) {
            preferencesDataStore.clearPinAndCredentials()
            com.pixelguard.applock.domain.usecase.SessionManager.resetFailedAttempts(context.packageName)
        }
        true
    }

    override suspend fun getInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)
        val currentLocked = lockedPackages.first()

        val apps = mutableListOf<AppItem>()
        for (resolveInfo in resolveInfoList) {
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName == context.packageName) continue // Exclude self

            val appName = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val iconBitmap = try {
                icon?.toBitmap(width = 120, height = 120, config = Bitmap.Config.ARGB_8888)
            } catch (_: Exception) {
                null
            }
            val category = categorizePackage(pkgName, resolveInfo.activityInfo.applicationInfo)
            val isLocked = currentLocked.contains(pkgName)

            apps.add(AppItem(pkgName, appName, icon, iconBitmap, category, isLocked))
        }

        // Add Android Settings app specifically if not included in launcher list
        val settingsPkg = "com.android.settings"
        if (apps.none { it.packageName == settingsPkg }) {
            try {
                val appInfo = packageManager.getApplicationInfo(settingsPkg, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val settingsBitmap = try {
                    icon?.toBitmap(width = 120, height = 120, config = Bitmap.Config.ARGB_8888)
                } catch (_: Exception) {
                    null
                }
                apps.add(AppItem(settingsPkg, label, icon, settingsBitmap, AppCategory.SYSTEM, currentLocked.contains(settingsPkg)))
            } catch (_: Exception) {}
        }

        apps.sortedWith(compareBy({ !it.isLocked }, { it.appName.lowercase(Locale.ROOT) }))
    }

    private fun categorizePackage(packageName: String, appInfo: ApplicationInfo): AppCategory {
        val lower = packageName.lowercase(Locale.ROOT)
        return when {
            // Social / Messaging
            lower.contains("whatsapp") || lower.contains("instagram") ||
            lower.contains("telegram") || lower.contains("snapchat") ||
            lower.contains("facebook") || lower.contains("twitter") ||
            lower.contains("tiktok") || lower.contains("messenger") ||
            lower.contains("discord") || lower.contains("reddit") -> AppCategory.SOCIAL

            // Financial / Banking
            lower.contains("bank") || lower.contains("pay") ||
            lower.contains("paisa") || lower.contains("phonepe") ||
            lower.contains("upi") || lower.contains("bhim") ||
            lower.contains("wallet") || lower.contains("crypto") ||
            lower.contains("binance") || lower.contains("coinbase") ||
            lower.contains("chase") || lower.contains("revolut") -> AppCategory.FINANCIAL

            // System / Settings / Store
            packageName == "com.android.settings" ||
            packageName == "com.android.vending" ||
            packageName == "com.google.android.packageinstaller" ||
            packageName == "com.android.packageinstaller" ||
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> AppCategory.SYSTEM

            // High Sensitivity (Photos, Gallery, Files, Mail)
            lower.contains("gallery") || lower.contains("photo") ||
            lower.contains("camera") || lower.contains("file") ||
            lower.contains("drive") || lower.contains("mail") ||
            lower.contains("gmail") || lower.contains("notes") -> AppCategory.SENSITIVE

            else -> AppCategory.OTHER
        }
    }

    private fun parseRecordsJson(jsonStr: String): List<IntruderRecord> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<IntruderRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                IntruderRecord(
                    id = obj.getString("id"),
                    timestamp = obj.getLong("timestamp"),
                    targetPackageName = obj.getString("targetPackageName"),
                    targetAppName = obj.getString("targetAppName"),
                    photoPath = obj.getString("photoPath")
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun loadIntruderRecords() {
        val encFile = File(context.filesDir, "intruder_records.enc")
        val legacyJsonFile = File(context.filesDir, "intruder_records.json")

        // 1. SEC-005: Read encrypted intruder metadata
        if (encFile.exists()) {
            try {
                val encBytes = encFile.readBytes()
                val decBytes = cryptoManager.decryptFromFileBytes(encBytes)
                val jsonStr = String(decBytes, Charsets.UTF_8)
                _intruderRecords.value = parseRecordsJson(jsonStr)
                return
            } catch (_: Exception) {
                _intruderRecords.value = emptyList()
            }
        }

        // 2. Seamless Auto-Migration: If legacy plaintext JSON exists, auto-encrypt and delete legacy file
        if (legacyJsonFile.exists()) {
            try {
                val jsonStr = legacyJsonFile.readText()
                val list = parseRecordsJson(jsonStr)
                _intruderRecords.value = list
                saveIntruderRecords(list) // Writes encrypted intruder_records.enc
                legacyJsonFile.delete() // Securely remove legacy unencrypted file
            } catch (_: Exception) {
                _intruderRecords.value = emptyList()
            }
        } else {
            _intruderRecords.value = emptyList()
        }
    }

    private fun saveIntruderRecords(records: List<IntruderRecord>) {
        val encFile = File(context.filesDir, "intruder_records.enc")
        val array = JSONArray()
        records.forEach { record ->
            val obj = JSONObject().apply {
                put("id", record.id)
                put("timestamp", record.timestamp)
                put("targetPackageName", record.targetPackageName)
                put("targetAppName", record.targetAppName)
                put("photoPath", record.photoPath)
            }
            array.put(obj)
        }
        try {
            val jsonBytes = array.toString().toByteArray(Charsets.UTF_8)
            val encrypted = cryptoManager.encryptToFileBytes(jsonBytes)
            encFile.writeBytes(encrypted)
        } catch (_: Exception) {}
    }

    override suspend fun addIntruderRecord(record: IntruderRecord) {
        val updated = _intruderRecords.value.toMutableList()
        updated.add(0, record)
        _intruderRecords.value = updated
        saveIntruderRecords(updated)
    }

    override suspend fun deleteIntruderRecord(id: String) {
        val record = _intruderRecords.value.find { it.id == id }
        record?.let {
            val photoFile = File(it.photoPath)
            if (photoFile.exists()) photoFile.delete()
        }
        val updated = _intruderRecords.value.filterNot { it.id == id }
        _intruderRecords.value = updated
        saveIntruderRecords(updated)
    }

    override suspend fun clearAllIntruderRecords() {
        _intruderRecords.value.forEach {
            val photoFile = File(it.photoPath)
            if (photoFile.exists()) photoFile.delete()
        }
        _intruderRecords.value = emptyList()
        val encFile = File(context.filesDir, "intruder_records.enc")
        if (encFile.exists()) encFile.delete()
        val legacyFile = File(context.filesDir, "intruder_records.json")
        if (legacyFile.exists()) legacyFile.delete()
    }

    override suspend fun enableRecommendedShields() {
        val current = preferences.first()
        updatePreferences(current.copy(isSettingsProtected = true, isRecentsMaskingEnabled = true))
    }

    override suspend fun setCategoryOverride(packageName: String, category: AppCategory?) {
        val current = preferences.first()
        val obj = try {
            JSONObject(current.categoryOverridesJson)
        } catch (_: Exception) {
            JSONObject()
        }
        if (category == null) {
            obj.remove(packageName)
        } else {
            obj.put(packageName, category.name)
        }
        updatePreferences(current.copy(categoryOverridesJson = obj.toString()))
    }

    override suspend fun createUserTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = preferences.first()
        val tags = current.getUserCustomTags().toMutableList()
        if (!tags.any { it.equals(trimmed, ignoreCase = true) }) {
            tags.add(trimmed)
            val array = JSONArray(tags)
            updatePreferences(current.copy(userCustomTagsJson = array.toString()))
        }
    }

    override suspend fun deleteUserTag(tag: String) {
        val current = preferences.first()
        val tags = current.getUserCustomTags().filterNot { it.equals(tag, ignoreCase = true) }
        val array = JSONArray(tags)

        val appTagsObj = try {
            JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val keys = appTagsObj.keys()
        val newAppTagsObj = JSONObject()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val arr = appTagsObj.getJSONArray(pkg)
            val updatedList = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val t = arr.getString(i)
                if (!t.equals(tag, ignoreCase = true)) {
                    updatedList.add(t)
                }
            }
            if (updatedList.isNotEmpty()) {
                newAppTagsObj.put(pkg, JSONArray(updatedList))
            }
        }

        updatePreferences(current.copy(
            userCustomTagsJson = array.toString(),
            appCustomTagsJson = newAppTagsObj.toString()
        ))
    }

    override suspend fun addTagToApp(packageName: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = preferences.first()

        val userTags = current.getUserCustomTags().toMutableList()
        if (!userTags.any { it.equals(trimmed, ignoreCase = true) }) {
            userTags.add(trimmed)
        }

        val appTagsObj = try {
            JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val existingTags = current.getAppTags(packageName).toMutableList()
        if (!existingTags.any { it.equals(trimmed, ignoreCase = true) }) {
            existingTags.add(trimmed)
        }
        appTagsObj.put(packageName, JSONArray(existingTags))

        updatePreferences(current.copy(
            userCustomTagsJson = JSONArray(userTags).toString(),
            appCustomTagsJson = appTagsObj.toString()
        ))
    }

    override suspend fun removeTagFromApp(packageName: String, tag: String) {
        val current = preferences.first()
        val appTagsObj = try {
            JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val existingTags = current.getAppTags(packageName).filterNot { it.equals(tag, ignoreCase = true) }
        if (existingTags.isEmpty()) {
            appTagsObj.remove(packageName)
        } else {
            appTagsObj.put(packageName, JSONArray(existingTags))
        }
        updatePreferences(current.copy(appCustomTagsJson = appTagsObj.toString()))
    }

    override suspend fun exportEncryptedDiagnostics(recoveryKey: String): File = withContext(Dispatchers.IO) {
        val snapshot = com.pixelguard.applock.data.local.SecuritySnapshotManager.loadSnapshot(context)
        val packageManager = context.packageManager
        val pInfo = try {
            packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val appVersion = pInfo?.versionName ?: "1.0.0"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo?.longVersionCode?.toInt() ?: 1
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode ?: 1
        }
        val isLockedOut = com.pixelguard.applock.domain.usecase.SessionManager.isLockedOut(context.packageName)
        val remainingSec = com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(context.packageName)
        val failedAttempts = com.pixelguard.applock.domain.usecase.SessionManager.getGlobalFailedAttempts()
        val records = _intruderRecords.value.size

        val exporter = com.pixelguard.applock.data.local.DiagnosticExporter(cryptoManager)
        val payload = exporter.generateAuditPayload(
            appVersion = appVersion,
            versionCode = versionCode,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            androidVersion = android.os.Build.VERSION.SDK_INT,
            securityPatch = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.os.Build.VERSION.SECURITY_PATCH
            } else "N/A",
            isAccessibilityActive = true,
            isNotificationShieldActive = true,
            snapshot = snapshot,
            intruderRecordsCount = records,
            globalFailedAttempts = failedAttempts,
            isLockedOut = isLockedOut,
            remainingLockoutSeconds = remainingSec
        )

        val encryptedBytes = exporter.encryptAuditPayload(payload, recoveryKey)
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val exportFile = File(exportDir, "pixelguard_diagnostic_${System.currentTimeMillis()}.pgdiag")
        exportFile.writeBytes(encryptedBytes)
        exportFile
    }
}

