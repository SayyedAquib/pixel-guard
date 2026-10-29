package com.pixelguard.applock.ui.main

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.AppSortOrder
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.repository.FakeAppLockRepository
import com.pixelguard.applock.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeAppLockRepository
    private lateinit var viewModel: MainViewModel

    private val appBank = AppItem(
        packageName = "com.pixelguard.bank",
        appName = "Pixel Bank",
        category = AppCategory.FINANCIAL
    )
    private val appChat = AppItem(
        packageName = "com.pixelguard.chat",
        appName = "Secure Chat",
        category = AppCategory.SOCIAL
    )
    private val appGallery = AppItem(
        packageName = "com.pixelguard.gallery",
        appName = "Secret Photos",
        category = AppCategory.SENSITIVE
    )
    private val appSettings = AppItem(
        packageName = "com.android.settings",
        appName = "System Settings",
        category = AppCategory.SYSTEM
    )
    private val appNotes = AppItem(
        packageName = "com.pixelguard.notes",
        appName = "Daily Notes",
        category = AppCategory.OTHER
    )

    private val allSampleApps = listOf(appBank, appChat, appGallery, appSettings, appNotes)

    @Before
    fun setUp() {
        fakeRepository = FakeAppLockRepository(
            initialLockedPackages = setOf("com.pixelguard.bank"),
            initialApps = allSampleApps
        )
        viewModel = MainViewModel(fakeRepository)
    }

    @Test
    fun initialLoading_mapsLockedStatusFromRepository() = runTest {
        viewModel.allApps.test {
            // Initial stateIn emission
            assertThat(awaitItem()).isEmpty()

            // After advanceUntilIdle, loadApps executes and combine emits
            advanceUntilIdle()
            val apps = awaitItem()
            assertThat(apps).hasSize(5)

            val bankApp = apps.first { it.packageName == "com.pixelguard.bank" }
            val chatApp = apps.first { it.packageName == "com.pixelguard.chat" }
            assertThat(bankApp.isLocked).isTrue()
            assertThat(chatApp.isLocked).isFalse()
        }
    }

    @Test
    fun searchQuery_filtersAppsCaseInsensitive() = runTest {
        viewModel.filteredApps.test {
            awaitItem() // initial empty
            advanceUntilIdle()
            awaitItem() // initial loaded apps

            // Search for "chat"
            viewModel.onSearchQueryChanged("chat")
            advanceUntilIdle()
            val result = awaitItem()
            assertThat(result).hasSize(1)
            assertThat(result.first().packageName).isEqualTo("com.pixelguard.chat")

            // Case-insensitive search "PHOTOS"
            viewModel.onSearchQueryChanged("PHOTOS")
            advanceUntilIdle()
            val photoResult = awaitItem()
            assertThat(photoResult).hasSize(1)
            assertThat(photoResult.first().packageName).isEqualTo("com.pixelguard.gallery")

            // Empty query resets to full list
            viewModel.onSearchQueryChanged("")
            advanceUntilIdle()
            val allResult = awaitItem()
            assertThat(allResult).hasSize(5)
        }
    }

    @Test
    fun categoryFilter_filtersBySelectedCategory() = runTest {
        viewModel.filteredApps.test {
            awaitItem() // initial
            advanceUntilIdle()
            awaitItem() // loaded

            // Filter Social
            viewModel.onCategorySelected(AppCategory.SOCIAL)
            advanceUntilIdle()
            val socialApps = awaitItem()
            assertThat(socialApps).hasSize(1)
            assertThat(socialApps.first().packageName).isEqualTo("com.pixelguard.chat")

            // Filter System
            viewModel.onCategorySelected(AppCategory.SYSTEM)
            advanceUntilIdle()
            val sysApps = awaitItem()
            assertThat(sysApps).hasSize(1)
            assertThat(sysApps.first().packageName).isEqualTo("com.android.settings")

            // Clear category
            viewModel.onCategorySelected(null)
            advanceUntilIdle()
            val allApps = awaitItem()
            assertThat(allApps).hasSize(5)
        }
    }

    @Test
    fun toggleOnlyLockedFilter_showsOnlyLockedApps() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Toggle only locked on
            viewModel.toggleOnlyLockedFilter()
            advanceUntilIdle()
            val lockedApps = awaitItem()
            assertThat(lockedApps).hasSize(1)
            assertThat(lockedApps.first().packageName).isEqualTo("com.pixelguard.bank")

            // Toggle back off
            viewModel.toggleOnlyLockedFilter()
            advanceUntilIdle()
            val allApps = awaitItem()
            assertThat(allApps).hasSize(5)
        }
    }

    @Test
    fun resetFilters_clearsSearchCategoryAndOnlyLocked() = runTest {
        viewModel.onSearchQueryChanged("bank")
        viewModel.onCategorySelected(AppCategory.FINANCIAL)
        viewModel.toggleOnlyLockedFilter()

        assertThat(viewModel.searchQuery.value).isEqualTo("bank")
        assertThat(viewModel.selectedCategory.value).isEqualTo(AppCategory.FINANCIAL)
        assertThat(viewModel.isOnlyLockedFilterActive.value).isTrue()

        viewModel.resetFilters()

        assertThat(viewModel.searchQuery.value).isEmpty()
        assertThat(viewModel.selectedCategory.value).isNull()
        assertThat(viewModel.isOnlyLockedFilterActive.value).isFalse()
    }

    @Test
    fun sortOrder_lockedFirst_sortsLockedAppsToTop() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            viewModel.setSortOrder(AppSortOrder.LOCKED_FIRST)
            advanceUntilIdle()
            val sorted = awaitItem()

            // Bank is locked -> should be first
            assertThat(sorted.first().packageName).isEqualTo("com.pixelguard.bank")
            assertThat(sorted.first().isLocked).isTrue()
        }
    }

    @Test
    fun sortOrder_nameAsc_sortsAlphabeticallyByDefault() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            val sorted = awaitItem()

            val names = sorted.map { it.appName }
            assertThat(names).containsExactly(
                "Daily Notes",
                "Pixel Bank",
                "Secret Photos",
                "Secure Chat",
                "System Settings"
            ).inOrder()
        }
    }

    @Test
    fun sortOrder_category_sortsByCategoryOrdinal() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            viewModel.setSortOrder(AppSortOrder.CATEGORY)
            advanceUntilIdle()
            val sorted = awaitItem()

            val categories = sorted.map { it.category.ordinal }
            assertThat(categories).isInOrder()
        }
    }

    @Test
    fun toggleAppLock_updatesRepositoryAndStateFlow() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Lock Chat app
            viewModel.toggleAppLock("com.pixelguard.chat", isCurrentlyLocked = false)
            advanceUntilIdle()
            val updated = awaitItem()
            val chat = updated.first { it.packageName == "com.pixelguard.chat" }
            assertThat(chat.isLocked).isTrue()

            // Unlock Bank app
            viewModel.toggleAppLock("com.pixelguard.bank", isCurrentlyLocked = true)
            advanceUntilIdle()
            val afterUnlock = awaitItem()
            val bank = afterUnlock.first { it.packageName == "com.pixelguard.bank" }
            assertThat(bank.isLocked).isFalse()
        }
    }

    @Test
    fun protectAllSensitiveApps_locksSensitiveSocialAndFinancial() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            viewModel.protectAllSensitiveApps()
            advanceUntilIdle()
            val updated = awaitItem()

            val bank = updated.first { it.packageName == "com.pixelguard.bank" }
            val chat = updated.first { it.packageName == "com.pixelguard.chat" }
            val gallery = updated.first { it.packageName == "com.pixelguard.gallery" }
            val settings = updated.first { it.packageName == "com.android.settings" }
            val notes = updated.first { it.packageName == "com.pixelguard.notes" }

            assertThat(bank.isLocked).isTrue()
            assertThat(chat.isLocked).isTrue()
            assertThat(gallery.isLocked).isTrue()
            // System and other should not be locked by sensitive protection
            assertThat(settings.isLocked).isFalse()
            assertThat(notes.isLocked).isFalse()
        }
    }

    @Test
    fun lockCategory_locksOnlyAppsInGivenCategory() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            viewModel.lockCategory(AppCategory.SOCIAL)
            advanceUntilIdle()
            val updated = awaitItem()

            val chat = updated.first { it.packageName == "com.pixelguard.chat" }
            assertThat(chat.isLocked).isTrue()
        }
    }

    @Test
    fun unlockCategory_unlocksOnlyAppsInGivenCategory() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Bank is in Financial category and initially locked
            viewModel.unlockCategory(AppCategory.FINANCIAL)
            advanceUntilIdle()
            val updated = awaitItem()

            val bank = updated.first { it.packageName == "com.pixelguard.bank" }
            assertThat(bank.isLocked).isFalse()
        }
    }

    @Test
    fun unlockAllApps_clearsAllLockedPackages() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            viewModel.unlockAllApps()
            advanceUntilIdle()
            val updated = awaitItem()

            assertThat(updated.none { it.isLocked }).isTrue()
        }
    }

    @Test
    fun setMasterPin_savesPinAndNotifiesCallback() = runTest {
        var callbackInvoked = false
        viewModel.setMasterPin("987654") {
            callbackInvoked = true
        }
        advanceUntilIdle()

        assertThat(fakeRepository.storedPin).isEqualTo("987654")
        assertThat(fakeRepository.hasPinSet()).isTrue()
        assertThat(callbackInvoked).isTrue()
    }

    @Test
    fun setAppGracePeriod_storesValidJsonInPreferences() = runTest {
        viewModel.preferences.test {
            val initial = awaitItem()
            advanceUntilIdle()

            // Set 30 seconds for Bank
            viewModel.setAppGracePeriod("com.pixelguard.bank", 30)
            advanceUntilIdle()
            val updated = awaitItem()

            val json = JSONObject(updated.appGracePeriodsJson)
            assertThat(json.getInt("com.pixelguard.bank")).isEqualTo(30)

            // Remove grace period (-1)
            viewModel.setAppGracePeriod("com.pixelguard.bank", -1)
            advanceUntilIdle()
            val removed = awaitItem()
            val jsonRemoved = JSONObject(removed.appGracePeriodsJson)
            assertThat(jsonRemoved.has("com.pixelguard.bank")).isFalse()
        }
    }

    @Test
    fun togglePinOnly_updatesPinOnlyPackagesJson() = runTest {
        viewModel.preferences.test {
            awaitItem()
            advanceUntilIdle()

            // Enable PIN-only for Bank
            viewModel.togglePinOnly("com.pixelguard.bank", requirePinOnly = true)
            advanceUntilIdle()
            val added = awaitItem()
            assertThat(added.pinOnlyPackagesJson).contains("com.pixelguard.bank")

            // Disable PIN-only for Bank
            viewModel.togglePinOnly("com.pixelguard.bank", requirePinOnly = false)
            advanceUntilIdle()
            val removed = awaitItem()
            assertThat(removed.pinOnlyPackagesJson).doesNotContain("com.pixelguard.bank")
        }
    }

    @Test
    fun deleteAndClearIntruderRecords_updatesRepository() = runTest {
        val record1 = IntruderRecord(
            id = "rec-1",
            timestamp = 1000L,
            targetPackageName = "com.pixelguard.bank",
            targetAppName = "Pixel Bank",
            photoPath = "/path/1.jpg"
        )
        val record2 = IntruderRecord(
            id = "rec-2",
            timestamp = 2000L,
            targetPackageName = "com.pixelguard.chat",
            targetAppName = "Secure Chat",
            photoPath = "/path/2.jpg"
        )
        fakeRepository.addIntruderRecord(record1)
        fakeRepository.addIntruderRecord(record2)

        viewModel.intruderRecords.test {
            awaitItem() // initial
            advanceUntilIdle()
            val loaded = awaitItem()
            assertThat(loaded).hasSize(2)

            // Delete rec-1
            viewModel.deleteIntruderRecord("rec-1")
            advanceUntilIdle()
            val remaining = awaitItem()
            assertThat(remaining).hasSize(1)
            assertThat(remaining.first().id).isEqualTo("rec-2")

            // Clear all
            viewModel.clearAllIntruders()
            advanceUntilIdle()
            val emptyList = awaitItem()
            assertThat(emptyList).isEmpty()
        }
    }

    @Test
    fun search_matchesBothAppNameAndPackageName() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Search by package name substring "gallery" (absent from appName "Secret Photos")
            viewModel.onSearchQueryChanged("gallery")
            advanceUntilIdle()
            val matches = awaitItem()
            assertThat(matches).hasSize(1)
            assertThat(matches.first().packageName).isEqualTo("com.pixelguard.gallery")

            // Clear search
            viewModel.onSearchQueryChanged("")
            advanceUntilIdle()
            awaitItem()
        }
    }

    @Test
    fun categoryOverride_dynamicallyUpdatesCategoryInAllApps() = runTest {
        viewModel.allApps.test {
            awaitItem()
            advanceUntilIdle()
            val initial = awaitItem()
            val notesInitial = initial.first { it.packageName == "com.pixelguard.notes" }
            assertThat(notesInitial.category).isEqualTo(AppCategory.OTHER)

            // Reclassify Daily Notes from OTHER to FINANCIAL
            viewModel.setCategoryOverride("com.pixelguard.notes", AppCategory.FINANCIAL)
            advanceUntilIdle()
            val updated = awaitItem()
            val notesUpdated = updated.first { it.packageName == "com.pixelguard.notes" }
            assertThat(notesUpdated.category).isEqualTo(AppCategory.FINANCIAL)

            // Reset back to default
            viewModel.setCategoryOverride("com.pixelguard.notes", null)
            advanceUntilIdle()
            val reset = awaitItem()
            val notesReset = reset.first { it.packageName == "com.pixelguard.notes" }
            assertThat(notesReset.category).isEqualTo(AppCategory.OTHER)
        }
    }

    @Test
    fun customTags_lifecycle_creationAssignmentFilteringAndBatchLocking() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Assign tag "Personal" to Secure Chat
            viewModel.addTagToApp("com.pixelguard.chat", "Personal")
            advanceUntilIdle()
            val withTag = awaitItem()
            val chatApp = withTag.first { it.packageName == "com.pixelguard.chat" }
            assertThat(chatApp.customTags).contains("Personal")

            // Filter by tag "Personal"
            viewModel.onTagSelected("Personal")
            advanceUntilIdle()
            val filteredByTag = awaitItem()
            assertThat(filteredByTag).hasSize(1)
            assertThat(filteredByTag.first().packageName).isEqualTo("com.pixelguard.chat")

            // Batch lock tag
            viewModel.lockTag("Personal")
            advanceUntilIdle()
            val lockedTagApps = awaitItem()
            assertThat(lockedTagApps.first().isLocked).isTrue()

            // Batch unlock tag
            viewModel.unlockTag("Personal")
            advanceUntilIdle()
            val unlockedTagApps = awaitItem()
            assertThat(unlockedTagApps.first().isLocked).isFalse()

            // Delete tag "Personal"
            viewModel.deleteUserTag("Personal")
            advanceUntilIdle()
            // Selected tag should automatically be cleared
            assertThat(viewModel.selectedTag.value).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onlyCustomizedFilter_filtersOnlyAppsWithConfiguredPolicies() = runTest {
        viewModel.filteredApps.test {
            awaitItem()
            advanceUntilIdle()
            awaitItem()

            // Daily Notes has a custom grace period
            viewModel.setAppGracePeriod("com.pixelguard.notes", 60)
            advanceUntilIdle()

            // Activate customized filter
            viewModel.toggleOnlyCustomizedFilter()
            advanceUntilIdle()
            val customizedOnly = awaitItem()

            // Only Daily Notes should appear
            assertThat(customizedOnly.map { it.packageName }).containsExactly("com.pixelguard.notes")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun exportDiagnostics_callsRepositoryAndReturnsExportFile() = runTest {
        var exportedFile: java.io.File? = null
        viewModel.exportDiagnostics("ABCD-EFGH-JKLM-NPQR") { file ->
            exportedFile = file
        }
        advanceUntilIdle()

        assertThat(exportedFile).isNotNull()
        assertThat(exportedFile?.name).endsWith(".pgdiag")
        assertThat(exportedFile?.readText()).isEqualTo("fake_encrypted_payload")
    }
}
