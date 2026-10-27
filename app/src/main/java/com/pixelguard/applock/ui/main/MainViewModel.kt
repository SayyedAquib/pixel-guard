package com.pixelguard.applock.ui.main

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.AppSortOrder
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.model.getAppTags
import com.pixelguard.applock.data.model.getOverriddenCategory
import com.pixelguard.applock.data.model.isCustomized
import com.pixelguard.applock.data.repository.AppLockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean,
    val hasNotificationPermission: Boolean
) {
    val isAllGranted: Boolean
        get() = hasAccessibilityPermission
}

class MainViewModel(
    private val repository: AppLockRepository = AppLockApplication.instance.repository
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val preferences: StateFlow<LockPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, LockPreferences())

    val allApps: StateFlow<List<AppItem>> = combine(_installedApps, repository.lockedPackages, preferences) { apps, locked, prefs ->
        apps.map { app ->
            val overriddenCategory = prefs.getOverriddenCategory(app.packageName)
            val tags = prefs.getAppTags(app.packageName)
            app.copy(
                isLocked = locked.contains(app.packageName),
                category = overriddenCategory ?: app.category,
                customTags = tags
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val installedApps: StateFlow<List<AppItem>> = allApps

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<AppCategory?>(null)
    val selectedCategory: StateFlow<AppCategory?> = _selectedCategory.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _isOnlyLockedFilterActive = MutableStateFlow(false)
    val isOnlyLockedFilterActive: StateFlow<Boolean> = _isOnlyLockedFilterActive.asStateFlow()

    private val _isOnlyCustomizedFilterActive = MutableStateFlow(false)
    val isOnlyCustomizedFilterActive: StateFlow<Boolean> = _isOnlyCustomizedFilterActive.asStateFlow()

    private val _permissionStatus = MutableStateFlow(
        PermissionStatus(hasOverlayPermission = false, hasAccessibilityPermission = false, hasNotificationPermission = false)
    )
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    val intruderRecords: StateFlow<List<IntruderRecord>> = repository.intruderRecords
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasPinSet: StateFlow<Boolean?> = repository.hasPinSetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class AppFilterCriteria(
        val query: String,
        val category: AppCategory?,
        val tag: String?,
        val onlyLocked: Boolean,
        val onlyCustomized: Boolean
    )

    private val _filterCriteria = combine(
        _searchQuery,
        _selectedCategory,
        _selectedTag,
        _isOnlyLockedFilterActive,
        _isOnlyCustomizedFilterActive
    ) { query, category, tag, onlyLocked, onlyCustomized ->
        AppFilterCriteria(query, category, tag, onlyLocked, onlyCustomized)
    }

    val filteredApps: StateFlow<List<AppItem>> = combine(
        allApps,
        _filterCriteria,
        preferences
    ) { appsWithLock, criteria, prefs ->
        appsWithLock
            .filter { app ->
                val matchesQuery = criteria.query.isBlank() ||
                    app.appName.contains(criteria.query, ignoreCase = true) ||
                    app.packageName.contains(criteria.query, ignoreCase = true)
                val matchesCategory = criteria.category == null || app.category == criteria.category
                val matchesTag = criteria.tag == null || app.customTags.any { it.equals(criteria.tag, ignoreCase = true) }
                val matchesLocked = !criteria.onlyLocked || app.isLocked
                val matchesCustomized = !criteria.onlyCustomized || prefs.isCustomized(app.packageName)
                matchesQuery && matchesCategory && matchesTag && matchesLocked && matchesCustomized
            }
            .let { list ->
                when (prefs.appSortOrder) {
                    AppSortOrder.NAME_ASC -> list.sortedBy { it.appName.lowercase(Locale.ROOT) }
                    AppSortOrder.LOCKED_FIRST -> list.sortedWith(
                        compareByDescending<AppItem> { it.isLocked }
                            .thenBy { it.appName.lowercase(Locale.ROOT) }
                    )
                    AppSortOrder.CATEGORY -> list.sortedWith(
                        compareBy<AppItem> { it.category.ordinal }
                            .thenBy { it.appName.lowercase(Locale.ROOT) }
                    )
                }
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _installedApps.value = repository.getInstalledApps()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: AppCategory?) {
        _selectedCategory.value = category
        if (category != null) {
            _selectedTag.value = null
        }
    }

    fun onTagSelected(tag: String?) {
        _selectedTag.value = tag
        if (tag != null) {
            _selectedCategory.value = null
        }
    }

    fun toggleOnlyLockedFilter() {
        _isOnlyLockedFilterActive.value = !_isOnlyLockedFilterActive.value
    }

    fun toggleOnlyCustomizedFilter() {
        _isOnlyCustomizedFilterActive.value = !_isOnlyCustomizedFilterActive.value
    }

    fun setSortOrder(order: AppSortOrder) {
        viewModelScope.launch {
            repository.updatePreferences(preferences.value.copy(appSortOrder = order))
        }
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _selectedTag.value = null
        _isOnlyLockedFilterActive.value = false
        _isOnlyCustomizedFilterActive.value = false
    }

    fun toggleAppLock(packageName: String, isCurrentlyLocked: Boolean) {
        viewModelScope.launch {
            repository.setAppLocked(packageName, !isCurrentlyLocked)
        }
    }

    fun protectAllSensitiveApps() {
        viewModelScope.launch {
            val sensitivePackages = _installedApps.value
                .filter { it.category == AppCategory.SENSITIVE || it.category == AppCategory.FINANCIAL || it.category == AppCategory.SOCIAL }
                .map { it.packageName }
            repository.lockAllSensitiveApps(sensitivePackages)
        }
    }

    fun lockCategory(category: AppCategory) {
        viewModelScope.launch {
            val packages = _installedApps.value
                .filter { it.category == category }
                .map { it.packageName }
            repository.lockAllSensitiveApps(packages)
        }
    }

    fun unlockCategory(category: AppCategory) {
        viewModelScope.launch {
            val packages = _installedApps.value
                .filter { it.category == category }
                .map { it.packageName }
            repository.unlockPackages(packages)
        }
    }

    fun unlockAllApps() {
        viewModelScope.launch {
            repository.unlockAllApps()
        }
    }

    fun setMasterPin(pin: String, onComplete: (String) -> Unit = { _ -> }) {
        viewModelScope.launch {
            val recoveryCode = repository.setPin(pin)
            onComplete(recoveryCode)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        return repository.verifyPin(pin)
    }

    /**
     * PG-SEC-01: Rate-limited PIN verification for administrative actions (e.g. Change Master PIN).
     * Enforces progressive lockouts and triggers intruder photo capture upon exceeding failed threshold.
     */
    suspend fun verifyMasterPinForChange(currentPin: String): Boolean {
        val appPkg = AppLockApplication.instance.packageName
        if (com.pixelguard.applock.domain.usecase.SessionManager.isLockedOut(appPkg)) {
            return false
        }
        val isCorrect = repository.verifyPin(currentPin)
        if (isCorrect) {
            com.pixelguard.applock.domain.usecase.SessionManager.resetFailedAttempts(appPkg)
            return true
        } else {
            val attempts = com.pixelguard.applock.domain.usecase.SessionManager.incrementFailedAttempts(appPkg)
            val currentPrefs = preferences.value
            if (currentPrefs.isIntruderSelfieEnabled && attempts >= currentPrefs.failedAttemptsThreshold) {
                val camera = com.pixelguard.applock.service.camera.SilentCameraCapture(AppLockApplication.instance)
                camera.captureIntruderPhoto { photoFile ->
                    viewModelScope.launch {
                        val record = IntruderRecord(
                            id = java.util.UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            targetPackageName = appPkg,
                            targetAppName = "Pixel Guard Settings",
                            photoPath = photoFile.absolutePath
                        )
                        repository.addIntruderRecord(record)
                    }
                }
            }
            return false
        }
    }

    fun armRecommendedShields() {
        viewModelScope.launch {
            repository.enableRecommendedShields()
        }
    }

    fun updatePreferences(newPrefs: LockPreferences) {
        viewModelScope.launch {
            repository.updatePreferences(newPrefs)
        }
    }

    fun setAppGracePeriod(packageName: String, seconds: Int) {
        viewModelScope.launch {
            val currentPrefs = preferences.value
            val json = try {
                org.json.JSONObject(currentPrefs.appGracePeriodsJson)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            if (seconds < 0) {
                json.remove(packageName)
            } else {
                json.put(packageName, seconds)
            }
            repository.updatePreferences(currentPrefs.copy(appGracePeriodsJson = json.toString()))
        }
    }

    fun togglePinOnly(packageName: String, requirePinOnly: Boolean) {
        viewModelScope.launch {
            val currentPrefs = preferences.value
            val set = mutableSetOf<String>()
            try {
                val array = org.json.JSONArray(currentPrefs.pinOnlyPackagesJson)
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i)
                    if (pkg.isNotBlank()) set.add(pkg)
                }
            } catch (_: Exception) {}

            if (requirePinOnly) {
                set.add(packageName)
            } else {
                set.remove(packageName)
            }
            val newArray = org.json.JSONArray(set.toList())
            repository.updatePreferences(currentPrefs.copy(pinOnlyPackagesJson = newArray.toString()))
        }
    }

    fun toggleNotificationMasking(packageName: String, isMasked: Boolean) {
        viewModelScope.launch {
            val currentPrefs = preferences.value
            val exemptSet = mutableSetOf<String>()
            try {
                val array = org.json.JSONArray(currentPrefs.notificationExemptPackagesJson)
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i)
                    if (pkg.isNotBlank()) exemptSet.add(pkg)
                }
            } catch (_: Exception) {}

            // Opt-out architecture: notificationExemptPackagesJson contains packages that should NOT be masked
            if (isMasked) {
                exemptSet.remove(packageName)
            } else {
                exemptSet.add(packageName)
            }
            val newArray = org.json.JSONArray(exemptSet.toList())
            repository.updatePreferences(currentPrefs.copy(notificationExemptPackagesJson = newArray.toString()))
        }
    }

    fun deleteIntruderRecord(id: String) {
        viewModelScope.launch {
            repository.deleteIntruderRecord(id)
        }
    }

    fun clearAllIntruders() {
        viewModelScope.launch {
            repository.clearAllIntruderRecords()
        }
    }

    fun setCategoryOverride(packageName: String, category: AppCategory?) {
        viewModelScope.launch {
            repository.setCategoryOverride(packageName, category)
        }
    }

    fun createUserTag(tag: String) {
        viewModelScope.launch {
            repository.createUserTag(tag)
        }
    }

    fun deleteUserTag(tag: String) {
        viewModelScope.launch {
            if (_selectedTag.value?.equals(tag, ignoreCase = true) == true) {
                _selectedTag.value = null
            }
            repository.deleteUserTag(tag)
        }
    }

    fun addTagToApp(packageName: String, tag: String) {
        viewModelScope.launch {
            repository.addTagToApp(packageName, tag)
        }
    }

    fun removeTagFromApp(packageName: String, tag: String) {
        viewModelScope.launch {
            repository.removeTagFromApp(packageName, tag)
        }
    }

    fun lockTag(tag: String) {
        viewModelScope.launch {
            val packages = allApps.value
                .filter { it.customTags.any { t -> t.equals(tag, ignoreCase = true) } }
                .map { it.packageName }
            repository.lockAllSensitiveApps(packages)
        }
    }

    fun unlockTag(tag: String) {
        viewModelScope.launch {
            val packages = allApps.value
                .filter { it.customTags.any { t -> t.equals(tag, ignoreCase = true) } }
                .map { it.packageName }
            repository.unlockPackages(packages)
        }
    }

    @androidx.annotation.VisibleForTesting
    var isTestingPermissions: Boolean = false

    fun checkPermissions(context: Context) {
        if (isTestingPermissions) return
        val hasOverlay = Settings.canDrawOverlays(context)
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        val notificationEnabled = isNotificationServiceEnabled(context)

        _permissionStatus.value = PermissionStatus(
            hasOverlayPermission = hasOverlay,
            hasAccessibilityPermission = accessibilityEnabled,
            hasNotificationPermission = notificationEnabled
        )
    }

    @androidx.annotation.VisibleForTesting
    fun setPermissionStatusForTest(status: PermissionStatus) {
        isTestingPermissions = true
        _permissionStatus.value = status
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/${com.pixelguard.applock.service.AppLockAccessibilityService::class.java.canonicalName}"
        val expectedShortService = "${context.packageName}/${com.pixelguard.applock.service.AppLockAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedService, ignoreCase = true) ||
                componentNameString.equals(expectedShortService, ignoreCase = true) ||
                ComponentName.unflattenFromString(componentNameString)?.packageName == context.packageName
            ) {
                return true
            }
        }
        return false
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
