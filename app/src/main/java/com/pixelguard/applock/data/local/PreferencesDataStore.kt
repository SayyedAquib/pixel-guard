package com.pixelguard.applock.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pixelguard.applock.data.model.AppSortOrder
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.model.LockType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixel_app_lock_prefs")

data class PinCredentials(
    val pinHash: String?,
    val pinSalt: String?,
    val recoveryHash: String?,
    val recoverySalt: String?
)

class PreferencesDataStore(private val context: Context) {

    val lockedPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOCKED_PACKAGES] ?: emptySet()
    }

    val pinHashFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PIN_HASH]
    }

    val pinSaltFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PIN_SALT]
    }

    val recoveryCodeHashFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_RECOVERY_CODE_HASH]
    }

    val recoveryCodeSaltFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_RECOVERY_CODE_SALT]
    }

    val pinCredentialsFlow: Flow<PinCredentials> = context.dataStore.data.map { preferences ->
        PinCredentials(
            pinHash = preferences[KEY_PIN_HASH],
            pinSalt = preferences[KEY_PIN_SALT],
            recoveryHash = preferences[KEY_RECOVERY_CODE_HASH],
            recoverySalt = preferences[KEY_RECOVERY_CODE_SALT]
        )
    }

    suspend fun savePinWithRecovery(
        pinHash: String,
        pinSalt: String,
        recoveryHash: String,
        recoverySalt: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PIN_HASH] = pinHash
            preferences[KEY_PIN_SALT] = pinSalt
            preferences[KEY_RECOVERY_CODE_HASH] = recoveryHash
            preferences[KEY_RECOVERY_CODE_SALT] = recoverySalt
        }
    }

    suspend fun clearPinAndCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PIN_HASH)
            preferences.remove(KEY_PIN_SALT)
            preferences.remove(KEY_RECOVERY_CODE_HASH)
            preferences.remove(KEY_RECOVERY_CODE_SALT)
        }
    }

    val globalFailedAttemptsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_GLOBAL_FAILED_ATTEMPTS] ?: 0
    }

    val globalLockoutUntilTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_GLOBAL_LOCKOUT_UNTIL_TIMESTAMP] ?: 0L
    }

    val globalLockoutWallClockTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_GLOBAL_LOCKOUT_WALL_CLOCK_TIMESTAMP] ?: 0L
    }

    val globalLockoutDurationMsFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_GLOBAL_LOCKOUT_DURATION_MS] ?: 0L
    }

    suspend fun saveGlobalLockout(failedAttempts: Int, durationMs: Long, wallClockExpiryMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GLOBAL_FAILED_ATTEMPTS] = failedAttempts
            preferences[KEY_GLOBAL_LOCKOUT_DURATION_MS] = durationMs
            preferences[KEY_GLOBAL_LOCKOUT_WALL_CLOCK_TIMESTAMP] = wallClockExpiryMs
            preferences[KEY_GLOBAL_LOCKOUT_UNTIL_TIMESTAMP] = wallClockExpiryMs
        }
    }

    suspend fun saveGlobalLockout(failedAttempts: Int, lockoutUntilTimestamp: Long) {
        saveGlobalLockout(failedAttempts, 0L, lockoutUntilTimestamp)
    }

    suspend fun clearGlobalLockout() {
        context.dataStore.edit { preferences ->
            preferences[KEY_GLOBAL_FAILED_ATTEMPTS] = 0
            preferences[KEY_GLOBAL_LOCKOUT_DURATION_MS] = 0L
            preferences[KEY_GLOBAL_LOCKOUT_WALL_CLOCK_TIMESTAMP] = 0L
            preferences[KEY_GLOBAL_LOCKOUT_UNTIL_TIMESTAMP] = 0L
        }
    }

    val preferencesFlow: Flow<LockPreferences> = context.dataStore.data.map { preferences ->
        LockPreferences(
            isBiometricsEnabled = preferences[KEY_BIOMETRICS_ENABLED] ?: true,
            lockType = LockType.valueOf(preferences[KEY_LOCK_TYPE] ?: LockType.PIN.name),
            gracePeriodSeconds = preferences[KEY_GRACE_PERIOD_SECONDS] ?: 0,
            isIntruderSelfieEnabled = preferences[KEY_INTRUDER_SELFIE_ENABLED] ?: false,
            failedAttemptsThreshold = preferences[KEY_FAILED_ATTEMPTS_THRESHOLD] ?: 3,
            isDecoyCrashEnabled = preferences[KEY_DECOY_CRASH_ENABLED] ?: false,
            isNotificationShieldEnabled = preferences[KEY_NOTIFICATION_SHIELD_ENABLED] ?: true,
            isSettingsProtected = preferences[KEY_SETTINGS_PROTECTED] ?: false,
            isPlayStoreProtected = preferences[KEY_PLAY_STORE_PROTECTED] ?: false,
            isScramblePinEnabled = preferences[KEY_SCRAMBLE_PIN_ENABLED] ?: false,
            isForegroundServiceEnabled = preferences[KEY_FOREGROUND_SERVICE_ENABLED] ?: false,
            isRecentsMaskingEnabled = preferences[KEY_RECENTS_MASKING_ENABLED] ?: false,
            isQuickTileEnabled = preferences[KEY_QUICK_TILE_ENABLED] ?: true,
            quickTilePauseMinutes = preferences[KEY_QUICK_TILE_PAUSE_MINUTES] ?: 15,
            isHapticsEnabled = preferences[KEY_HAPTICS_ENABLED] ?: true,
            isAnimationsEnabled = preferences[KEY_ANIMATIONS_ENABLED] ?: true,
            isScheduleEnabled = preferences[KEY_SCHEDULE_ENABLED] ?: false,
            scheduleStartHour = preferences[KEY_SCHEDULE_START_HOUR] ?: 23,
            scheduleEndHour = preferences[KEY_SCHEDULE_END_HOUR] ?: 7,
            isNightLockdownPinOnly = preferences[KEY_NIGHT_LOCKDOWN_PIN_ONLY] ?: false,
            isPerAppGraceEnabled = preferences[KEY_PER_APP_GRACE_ENABLED] ?: true,
            appGracePeriodsJson = preferences[KEY_APP_GRACE_PERIODS_JSON] ?: "{}",
            isDynamicAppThemingEnabled = preferences[KEY_DYNAMIC_APP_THEMING_ENABLED] ?: true,
            appSortOrder = try {
                AppSortOrder.valueOf(preferences[KEY_APP_SORT_ORDER] ?: AppSortOrder.NAME_ASC.name)
            } catch (_: Exception) {
                AppSortOrder.NAME_ASC
            },
            pinOnlyPackagesJson = preferences[KEY_PIN_ONLY_PACKAGES_JSON] ?: "[]",
            notificationExemptPackagesJson = preferences[KEY_NOTIFICATION_EXEMPT_PACKAGES_JSON] ?: "[]",
            categoryOverridesJson = preferences[KEY_CATEGORY_OVERRIDES_JSON] ?: "{}",
            userCustomTagsJson = preferences[KEY_USER_CUSTOM_TAGS_JSON] ?: "[]",
            appCustomTagsJson = preferences[KEY_APP_CUSTOM_TAGS_JSON] ?: "{}"
        )
    }

    suspend fun setLockedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCKED_PACKAGES] = packages
        }
    }

    suspend fun togglePackageLock(packageName: String, locked: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_LOCKED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (locked) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            preferences[KEY_LOCKED_PACKAGES] = current
        }
    }

    suspend fun savePin(hash: String, saltHex: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PIN_HASH] = hash
            preferences[KEY_PIN_SALT] = saltHex
        }
    }

    suspend fun updatePreferences(prefs: LockPreferences) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRICS_ENABLED] = prefs.isBiometricsEnabled
            preferences[KEY_LOCK_TYPE] = prefs.lockType.name
            preferences[KEY_GRACE_PERIOD_SECONDS] = prefs.gracePeriodSeconds
            preferences[KEY_INTRUDER_SELFIE_ENABLED] = prefs.isIntruderSelfieEnabled
            preferences[KEY_FAILED_ATTEMPTS_THRESHOLD] = prefs.failedAttemptsThreshold
            preferences[KEY_DECOY_CRASH_ENABLED] = prefs.isDecoyCrashEnabled
            preferences[KEY_NOTIFICATION_SHIELD_ENABLED] = prefs.isNotificationShieldEnabled
            preferences[KEY_SETTINGS_PROTECTED] = prefs.isSettingsProtected
            preferences[KEY_PLAY_STORE_PROTECTED] = prefs.isPlayStoreProtected
            preferences[KEY_SCRAMBLE_PIN_ENABLED] = prefs.isScramblePinEnabled
            preferences[KEY_FOREGROUND_SERVICE_ENABLED] = prefs.isForegroundServiceEnabled
            preferences[KEY_RECENTS_MASKING_ENABLED] = prefs.isRecentsMaskingEnabled
            preferences[KEY_QUICK_TILE_ENABLED] = prefs.isQuickTileEnabled
            preferences[KEY_QUICK_TILE_PAUSE_MINUTES] = prefs.quickTilePauseMinutes
            preferences[KEY_HAPTICS_ENABLED] = prefs.isHapticsEnabled
            preferences[KEY_ANIMATIONS_ENABLED] = prefs.isAnimationsEnabled
            preferences[KEY_SCHEDULE_ENABLED] = prefs.isScheduleEnabled
            preferences[KEY_SCHEDULE_START_HOUR] = prefs.scheduleStartHour
            preferences[KEY_SCHEDULE_END_HOUR] = prefs.scheduleEndHour
            preferences[KEY_NIGHT_LOCKDOWN_PIN_ONLY] = prefs.isNightLockdownPinOnly
            preferences[KEY_PER_APP_GRACE_ENABLED] = prefs.isPerAppGraceEnabled
            preferences[KEY_APP_GRACE_PERIODS_JSON] = prefs.appGracePeriodsJson
            preferences[KEY_DYNAMIC_APP_THEMING_ENABLED] = prefs.isDynamicAppThemingEnabled
            preferences[KEY_APP_SORT_ORDER] = prefs.appSortOrder.name
            preferences[KEY_PIN_ONLY_PACKAGES_JSON] = prefs.pinOnlyPackagesJson
            preferences[KEY_NOTIFICATION_EXEMPT_PACKAGES_JSON] = prefs.notificationExemptPackagesJson
            preferences[KEY_CATEGORY_OVERRIDES_JSON] = prefs.categoryOverridesJson
            preferences[KEY_USER_CUSTOM_TAGS_JSON] = prefs.userCustomTagsJson
            preferences[KEY_APP_CUSTOM_TAGS_JSON] = prefs.appCustomTagsJson
        }
    }

    suspend fun resetAllAppGracePeriods() {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_GRACE_PERIODS_JSON] = "{}"
        }
    }

    companion object {
        private val KEY_LOCKED_PACKAGES = stringSetPreferencesKey("locked_packages")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_RECOVERY_CODE_HASH = stringPreferencesKey("recovery_code_hash")
        private val KEY_RECOVERY_CODE_SALT = stringPreferencesKey("recovery_code_salt")
        private val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        private val KEY_LOCK_TYPE = stringPreferencesKey("lock_type")
        private val KEY_GRACE_PERIOD_SECONDS = intPreferencesKey("grace_period_seconds")
        private val KEY_INTRUDER_SELFIE_ENABLED = booleanPreferencesKey("intruder_selfie_enabled")
        private val KEY_FAILED_ATTEMPTS_THRESHOLD = intPreferencesKey("failed_attempts_threshold")
        private val KEY_DECOY_CRASH_ENABLED = booleanPreferencesKey("decoy_crash_enabled")
        private val KEY_NOTIFICATION_SHIELD_ENABLED = booleanPreferencesKey("notification_shield_enabled")
        private val KEY_SETTINGS_PROTECTED = booleanPreferencesKey("settings_protected")
        private val KEY_PLAY_STORE_PROTECTED = booleanPreferencesKey("play_store_protected")
        private val KEY_SCRAMBLE_PIN_ENABLED = booleanPreferencesKey("scramble_pin_enabled")
        private val KEY_FOREGROUND_SERVICE_ENABLED = booleanPreferencesKey("foreground_service_enabled")
        private val KEY_RECENTS_MASKING_ENABLED = booleanPreferencesKey("recents_masking_enabled")
        private val KEY_QUICK_TILE_ENABLED = booleanPreferencesKey("quick_tile_enabled")
        private val KEY_QUICK_TILE_PAUSE_MINUTES = intPreferencesKey("quick_tile_pause_minutes")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        private val KEY_SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        private val KEY_SCHEDULE_START_HOUR = intPreferencesKey("schedule_start_hour")
        private val KEY_SCHEDULE_END_HOUR = intPreferencesKey("schedule_end_hour")
        private val KEY_NIGHT_LOCKDOWN_PIN_ONLY = booleanPreferencesKey("night_lockdown_pin_only")
        private val KEY_PER_APP_GRACE_ENABLED = booleanPreferencesKey("per_app_grace_enabled")
        private val KEY_APP_GRACE_PERIODS_JSON = stringPreferencesKey("app_grace_periods_json")
        private val KEY_DYNAMIC_APP_THEMING_ENABLED = booleanPreferencesKey("dynamic_app_theming_enabled")
        private val KEY_APP_SORT_ORDER = stringPreferencesKey("app_sort_order")
        private val KEY_PIN_ONLY_PACKAGES_JSON = stringPreferencesKey("pin_only_packages_json")
        private val KEY_NOTIFICATION_EXEMPT_PACKAGES_JSON = stringPreferencesKey("notification_exempt_packages_json")
        private val KEY_CATEGORY_OVERRIDES_JSON = stringPreferencesKey("category_overrides_json")
        private val KEY_USER_CUSTOM_TAGS_JSON = stringPreferencesKey("user_custom_tags_json")
        private val KEY_APP_CUSTOM_TAGS_JSON = stringPreferencesKey("app_custom_tags_json")
        private val KEY_GLOBAL_FAILED_ATTEMPTS = intPreferencesKey("global_failed_attempts")
        private val KEY_GLOBAL_LOCKOUT_UNTIL_TIMESTAMP = longPreferencesKey("global_lockout_until_timestamp")
        private val KEY_GLOBAL_LOCKOUT_WALL_CLOCK_TIMESTAMP = longPreferencesKey("global_lockout_wall_clock_timestamp")
        private val KEY_GLOBAL_LOCKOUT_DURATION_MS = longPreferencesKey("global_lockout_duration_ms")
    }
}
