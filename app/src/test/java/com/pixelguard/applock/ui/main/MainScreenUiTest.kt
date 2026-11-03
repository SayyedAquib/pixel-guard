package com.pixelguard.applock.ui.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.AppSortOrder
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.repository.FakeAppLockRepository
import com.pixelguard.applock.ui.theme.PixelAppLockTheme
import com.pixelguard.applock.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
class MainScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var fakeRepository: FakeAppLockRepository
    private lateinit var viewModel: MainViewModel

    private val testApps = listOf(
        AppItem(
            packageName = "com.google.android.apps.walletnfcrel",
            appName = "Google Pay",
            category = AppCategory.FINANCIAL,
            isLocked = false
        ),
        AppItem(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            category = AppCategory.SOCIAL,
            isLocked = false
        ),
        AppItem(
            packageName = "com.google.android.GoogleCamera",
            appName = "Camera",
            category = AppCategory.SENSITIVE,
            isLocked = false
        )
    )

    @Before
    fun setUp() {
        fakeRepository = FakeAppLockRepository(
            initialLockedPackages = emptySet(),
            initialPreferences = LockPreferences(isAnimationsEnabled = false),
            initialHasPin = true,
            initialApps = testApps
        )
        viewModel = MainViewModel(fakeRepository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun initialRender_displaysPixelGuardTitleAndHeroGauge() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pixel Guard").assertExists()
        composeTestRule.onNodeWithText("No apps locked", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
        composeTestRule.onNodeWithContentDescription("Intruder Selfies").assertExists()
        composeTestRule.onNodeWithText("Score").assertExists()
    }

    @Test
    fun permissionBanner_shownWhenIncomplete_tappingTriggersNavigation() {
        var permissionsNavigated = false
        viewModel.setPermissionStatusForTest(
            PermissionStatus(
                hasOverlayPermission = true,
                hasUsagePermission = false,
                hasNotificationPermission = false
            )
        )

        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = { permissionsNavigated = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Setup Required for Android 17").assertExists()
        composeTestRule.onNodeWithText("Setup Required for Android 17").performClick()
        assertThat(permissionsNavigated).isTrue()
    }

    @Test
    fun permissionBanner_hiddenWhenAllPermissionsGranted() {
        viewModel.setPermissionStatusForTest(
            PermissionStatus(
                hasOverlayPermission = true,
                hasUsagePermission = true,
                hasNotificationPermission = true
            )
        )

        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Setup Required for Android 17").assertDoesNotExist()
    }

    @Test
    fun searchBar_typingQueryFiltersAppList() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search apps to protect...").performTextInput("Google Pay")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Google Pay")).assertCountEquals(2)
        composeTestRule.onNodeWithText("WhatsApp").assertDoesNotExist()
        composeTestRule.onNodeWithText("Camera").assertDoesNotExist()
    }

    @Test
    fun searchBar_clearButtonRestoresFullList() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search apps to protect...").performTextInput("Google Pay")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.onNodeWithText("WhatsApp").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Clear").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Google Pay").assertExists()
        composeTestRule.onNodeWithText("WhatsApp").assertExists()
        composeTestRule.onNodeWithText("Camera").assertExists()
    }

    @Test
    fun categoryChips_filteringByFinancial() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Banking & Finance (1)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Google Pay").assertExists()
        composeTestRule.onNodeWithText("WhatsApp").assertDoesNotExist()
    }

    @Test
    fun categoryStrip_batchLockAll_protectsAllAppsInCategory() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Banking & Finance (1)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Lock All").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(fakeRepository.storedPin).isNull() // verifying pin unchanged
        composeTestRule.onNodeWithText("1 of 1 protected").assertExists()
    }

    @Test
    fun onlyLockedFilter_showsEmptyStateWhenNoAppsLocked() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Locked Only (0)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("No protected apps yet").assertExists()
        composeTestRule.onNodeWithText("Tap the lock toggle on any app in your list to protect it.").assertExists()
    }

    @Test
    fun emptySearchResult_displaysHelpfulEmptyCardAndClearButton() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search apps to protect...").performTextInput("UnknownAppXYZ")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("No apps found for \"UnknownAppXYZ\"").assertExists()

        composeTestRule.onNodeWithText("Clear search").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Google Pay").assertExists()
        composeTestRule.onNodeWithText("WhatsApp").assertExists()
    }

    @Test
    fun lockToggle_clickingUnlockedIconLocksApp() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        // Click the first "Unlocked" toggle
        composeTestRule.onNodeWithText("WhatsApp").performScrollTo()
        val unlockedIcons = composeTestRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Unlocked"))
        unlockedIcons[1].performClick() // WhatsApp toggle
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Locked Only (1)").assertExists()
    }

    @Test
    fun heroGauge_protectSensitive_locksAllSensitiveAndFinancialApps() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Lock Critical").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Locked Only (3)").assertExists()
    }

    @Test
    fun appSecurityProfileSheet_opensWhenAppRowClicked() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Google Pay").performClick()

        composeTestRule.onNodeWithText("Security Rules").assertExists()
        composeTestRule.onNodeWithText("Always Ask for Passcode").assertExists()
        composeTestRule.onNodeWithText("When to Lock This App").assertExists()
    }

    @Test
    fun sortDropdown_selectsLockedFirstSortOrder() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        // Click Sort assist chip
        composeTestRule.onNodeWithText("Sort: A–Z").performClick()

        // Select Locked First
        composeTestRule.onNodeWithText("Locked First").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.preferences.value.appSortOrder).isEqualTo(AppSortOrder.LOCKED_FIRST)
    }

    @Test
    fun categoryStrip_batchUnlockAll_unprotectsCategory() {
        // Initially lock Google Pay
        viewModel.toggleAppLock("com.google.android.apps.walletnfcrel", false)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        // Filter by Banking & Finance
        composeTestRule.onNodeWithText("Banking & Finance (1)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        // Tap "Unlock All" in contextual category strip (index 1, as index 0 is Hero Gauge)
        val unlockButtons = composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Unlock All"))
        unlockButtons[1].performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.allApps.value.none { it.isLocked }).isTrue()
    }

    @Test
    fun heroGauge_unlockAll_unprotectsAllApps() {
        // Lock all apps
        viewModel.protectAllSensitiveApps()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Unlock All").performClick()
        // Safeguard confirmation dialog
        composeTestRule.onNodeWithText("Confirm").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.allApps.value.none { it.isLocked }).isTrue()
        composeTestRule.onNodeWithText("Locked Only (0)").assertExists()
    }

    @Test
    fun appSecurityProfileSheet_configuresPerAppGraceAndPinOnly() {
        var selectedGrace: Int? = null
        var lockToggled: Boolean? = null
        var pinOnlyToggled: Boolean? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                AppSecurityProfileSheetContent(
                    target = testApps[0],
                    isTargetLocked = false,
                    currentSec = 0,
                    isPinOnlyActive = false,
                    isNotifMaskedActive = false,
                    isGlobalNotifShieldEnabled = true,
                    onToggleLock = { lockToggled = it },
                    onTogglePinOnly = { pinOnlyToggled = it },
                    onToggleNotifMasking = {},
                    onSelectGraceSeconds = { selectedGrace = it }
                )
            }
        }

        composeTestRule.onNodeWithText("30s").performScrollTo().performClick()
        assertThat(selectedGrace).isEqualTo(30)
    }

    @Test
    fun dashboard_customizedFilterChip_existsAndToggles() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        // Verify Customized filter chip exists
        composeTestRule.onNodeWithText("Customized (0)").assertExists()
        composeTestRule.onNodeWithText("Customized (0)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isOnlyCustomizedFilterActive.value).isTrue()
    }

    @Test
    fun customTagChip_filteringByTag() {
        // Create user tag and assign to test app
        viewModel.createUserTag("Finance")
        viewModel.addTagToApp("com.google.android.apps.walletnfcrel", "Finance")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.setContent {
            PixelAppLockTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        // Tag chip #Finance (1) is rendered
        composeTestRule.onNodeWithText("#Finance (1)").assertExists()
        composeTestRule.onNodeWithText("#Finance (1)").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.selectedTag.value).isEqualTo("Finance")
        composeTestRule.onNodeWithText("Google Pay").assertExists()
    }

    @Test
    fun appSecurityProfileSheet_displaysCategoryAndTagsSection() {
        var selectedCategory: AppCategory? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                AppSecurityProfileSheetContent(
                    target = testApps[0],
                    isTargetLocked = true,
                    currentSec = 0,
                    isPinOnlyActive = false,
                    isNotifMaskedActive = false,
                    isGlobalNotifShieldEnabled = true,
                    onToggleLock = {},
                    onTogglePinOnly = {},
                    onToggleNotifMasking = {},
                    onSelectGraceSeconds = {},
                    userCustomTags = listOf("Work", "Crypto"),
                    onSelectCategory = { selectedCategory = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Category & Tags").assertExists()
        composeTestRule.onNodeWithText("Category").assertExists()
        composeTestRule.onNodeWithText("Banking & Finance").assertExists()
        composeTestRule.onNodeWithText("Custom Tags").assertExists()
        composeTestRule.onNodeWithText("+ Add Tag").assertExists()
    }
}
