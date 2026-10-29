package com.pixelguard.applock.data.repository

import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.LockPreferences
import kotlinx.coroutines.flow.Flow

sealed interface VerifyPinResult {
    data object Success : VerifyPinResult
    data class WrongPin(val attemptsRemaining: Int, val attemptsUntilLockout: Int) : VerifyPinResult
    data class LockedOut(val remainingSeconds: Int) : VerifyPinResult
    data object Unrecoverable : VerifyPinResult

    val isSuccess: Boolean get() = this is Success
}

interface AppLockRepository {
    val lockedPackages: Flow<Set<String>>
    val preferences: Flow<LockPreferences>
    val intruderRecords: Flow<List<IntruderRecord>>
    val hasPinSetFlow: Flow<Boolean>

    suspend fun setAppLocked(packageName: String, locked: Boolean)
    suspend fun lockAllSensitiveApps(sensitivePackages: List<String>)
    suspend fun unlockPackages(packages: List<String>)
    suspend fun unlockAllApps()
    suspend fun updatePreferences(preferences: LockPreferences)
    suspend fun hasPinSet(): Boolean
    suspend fun setPin(newPin: String): String
    suspend fun verifyPinResult(inputPin: String, packageName: String? = null): VerifyPinResult
    suspend fun verifyPin(inputPin: String): Boolean = verifyPinResult(inputPin).isSuccess
    suspend fun verifyRecoveryCode(code: String): Boolean
    suspend fun resetWithRecoveryCode(code: String): Boolean
    suspend fun getInstalledApps(): List<AppItem>
    suspend fun addIntruderRecord(record: IntruderRecord)
    suspend fun deleteIntruderRecord(id: String)
    suspend fun clearAllIntruderRecords()
    suspend fun enableRecommendedShields()
    suspend fun setCategoryOverride(packageName: String, category: AppCategory?)
    suspend fun createUserTag(tag: String)
    suspend fun deleteUserTag(tag: String)
    suspend fun addTagToApp(packageName: String, tag: String)
    suspend fun removeTagFromApp(packageName: String, tag: String)
    suspend fun exportEncryptedDiagnostics(recoveryKey: String): java.io.File
}


