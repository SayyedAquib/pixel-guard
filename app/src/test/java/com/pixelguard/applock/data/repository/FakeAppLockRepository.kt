package com.pixelguard.applock.data.repository

import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.model.getAppTags
import com.pixelguard.applock.data.model.getUserCustomTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-memory, deterministic fake implementation of [AppLockRepository] for unit tests.
 * Enables fast JVM testing of ViewModels, UseCases, and StateFlow pipelines without DataStore or SQLite.
 */
class FakeAppLockRepository(
    initialLockedPackages: Set<String> = emptySet(),
    initialPreferences: LockPreferences = LockPreferences(),
    initialHasPin: Boolean = false,
    initialApps: List<AppItem> = emptyList()
) : AppLockRepository {

    private val _lockedPackages = MutableStateFlow(initialLockedPackages)
    override val lockedPackages: Flow<Set<String>> = _lockedPackages.asStateFlow()

    private val _preferences = MutableStateFlow(initialPreferences)
    override val preferences: Flow<LockPreferences> = _preferences.asStateFlow()

    private val _intruderRecords = MutableStateFlow<List<IntruderRecord>>(emptyList())
    override val intruderRecords: Flow<List<IntruderRecord>> = _intruderRecords.asStateFlow()

    fun setIntruderRecords(records: List<IntruderRecord>) {
        _intruderRecords.value = records
    }

    private val _hasPinSetFlow = MutableStateFlow(initialHasPin)
    override val hasPinSetFlow: Flow<Boolean> = _hasPinSetFlow.asStateFlow()

    var installedAppsList: List<AppItem> = initialApps
    var storedPin: String? = null

    override suspend fun setAppLocked(packageName: String, locked: Boolean) {
        val current = _lockedPackages.value.toMutableSet()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        _lockedPackages.value = current
    }

    override suspend fun lockAllSensitiveApps(sensitivePackages: List<String>) {
        _lockedPackages.value = _lockedPackages.value + sensitivePackages.toSet()
    }

    override suspend fun unlockPackages(packages: List<String>) {
        _lockedPackages.value = _lockedPackages.value - packages.toSet()
    }

    override suspend fun unlockAllApps() {
        _lockedPackages.value = emptySet()
    }

    override suspend fun updatePreferences(preferences: LockPreferences) {
        _preferences.value = preferences
    }

    override suspend fun hasPinSet(): Boolean {
        return _hasPinSetFlow.value
    }

    private var storedRecoveryCode: String? = null

    override suspend fun setPin(newPin: String): String {
        storedPin = newPin
        storedRecoveryCode = "ABCD-EFGH-JKLM-NPQR"
        _hasPinSetFlow.value = true
        return storedRecoveryCode!!
    }

    override suspend fun verifyPinResult(inputPin: String, packageName: String?): VerifyPinResult {
        return if (storedPin != null && storedPin == inputPin) {
            VerifyPinResult.Success
        } else {
            VerifyPinResult.WrongPin(attemptsRemaining = 4, attemptsUntilLockout = 4)
        }
    }

    override suspend fun verifyPin(inputPin: String): Boolean {
        return storedPin != null && storedPin == inputPin
    }

    override suspend fun verifyRecoveryCode(code: String): Boolean {
        return storedRecoveryCode != null && storedRecoveryCode?.replace("-", "")?.equals(code.replace("-", ""), ignoreCase = true) == true
    }

    override suspend fun resetWithRecoveryCode(code: String): Boolean {
        return if (verifyRecoveryCode(code)) {
            storedPin = null
            storedRecoveryCode = null
            _hasPinSetFlow.value = false
            true
        } else {
            false
        }
    }

    override suspend fun getInstalledApps(): List<AppItem> {
        return installedAppsList
    }

    override suspend fun addIntruderRecord(record: IntruderRecord) {
        _intruderRecords.value = listOf(record) + _intruderRecords.value
    }

    override suspend fun deleteIntruderRecord(id: String) {
        _intruderRecords.value = _intruderRecords.value.filterNot { it.id == id }
    }

    override suspend fun clearAllIntruderRecords() {
        _intruderRecords.value = emptyList()
    }

    override suspend fun enableRecommendedShields() {
        val current = _preferences.value
        _preferences.value = current.copy(isSettingsProtected = true, isRecentsMaskingEnabled = true)
    }

    override suspend fun setCategoryOverride(packageName: String, category: AppCategory?) {
        val current = _preferences.value
        val obj = try {
            org.json.JSONObject(current.categoryOverridesJson)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        if (category == null) {
            obj.remove(packageName)
        } else {
            obj.put(packageName, category.name)
        }
        _preferences.value = current.copy(categoryOverridesJson = obj.toString())
    }

    override suspend fun createUserTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _preferences.value
        val tags = current.getUserCustomTags().toMutableList()
        if (!tags.any { it.equals(trimmed, ignoreCase = true) }) {
            tags.add(trimmed)
            val array = org.json.JSONArray(tags)
            _preferences.value = current.copy(userCustomTagsJson = array.toString())
        }
    }

    override suspend fun deleteUserTag(tag: String) {
        val current = _preferences.value
        val tags = current.getUserCustomTags().filterNot { it.equals(tag, ignoreCase = true) }
        val array = org.json.JSONArray(tags)

        val appTagsObj = try {
            org.json.JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        val keys = appTagsObj.keys()
        val newAppTagsObj = org.json.JSONObject()
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
                newAppTagsObj.put(pkg, org.json.JSONArray(updatedList))
            }
        }

        _preferences.value = current.copy(
            userCustomTagsJson = array.toString(),
            appCustomTagsJson = newAppTagsObj.toString()
        )
    }

    override suspend fun addTagToApp(packageName: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _preferences.value

        val userTags = current.getUserCustomTags().toMutableList()
        if (!userTags.any { it.equals(trimmed, ignoreCase = true) }) {
            userTags.add(trimmed)
        }

        val appTagsObj = try {
            org.json.JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        val existingTags = current.getAppTags(packageName).toMutableList()
        if (!existingTags.any { it.equals(trimmed, ignoreCase = true) }) {
            existingTags.add(trimmed)
        }
        appTagsObj.put(packageName, org.json.JSONArray(existingTags))

        _preferences.value = current.copy(
            userCustomTagsJson = org.json.JSONArray(userTags).toString(),
            appCustomTagsJson = appTagsObj.toString()
        )
    }

    override suspend fun removeTagFromApp(packageName: String, tag: String) {
        val current = _preferences.value
        val appTagsObj = try {
            org.json.JSONObject(current.appCustomTagsJson)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        val existingTags = current.getAppTags(packageName).filterNot { it.equals(tag, ignoreCase = true) }
        if (existingTags.isEmpty()) {
            appTagsObj.remove(packageName)
        } else {
            appTagsObj.put(packageName, org.json.JSONArray(existingTags))
        }
        _preferences.value = current.copy(appCustomTagsJson = appTagsObj.toString())
    }
}
