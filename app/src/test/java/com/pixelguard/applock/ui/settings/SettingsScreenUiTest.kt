package com.pixelguard.applock.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.repository.FakeAppLockRepository
import com.pixelguard.applock.ui.main.MainViewModel
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
class SettingsScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var fakeRepository: FakeAppLockRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeAppLockRepository(
            initialPreferences = LockPreferences(
                isBiometricsEnabled = true,
                isSettingsProtected = true,
                isRecentsMaskingEnabled = true,
                isScheduleEnabled = true,
                isQuickTileEnabled = true,
                isPerAppGraceEnabled = true,
                isAnimationsEnabled = false
            ),
            initialHasPin = true
        )
        viewModel = MainViewModel(fakeRepository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun initialRender_displaysTitleAndTitanM2Badge() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithText("Active").assertExists()
        composeTestRule.onNodeWithText("Fingerprint & Face Unlock").assertExists()
        composeTestRule.onNodeWithText("Change Passcode").assertExists()
    }

    @Test
    fun hardwareArmorBanner_shownWhenKeyProtectionsActive() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Your Apps are Protected").assertExists()
    }

    @Test
    fun securityVulnerabilityWarning_shownWhenSettingsProtectionDisabled() {
        viewModel.updatePreferences(
            LockPreferences(
                isSettingsProtected = false,
                isRecentsMaskingEnabled = true,
                isAnimationsEnabled = false
            )
        )
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.setContent {
            PixelAppLockTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Extra Protection Recommended").assertExists()
        composeTestRule.onNodeWithText("Turn on Phone Settings Protection and App Switcher Blurring to stay fully secure").assertExists()
    }

    @Test
    fun backButton_callsNavigateBackCallback() {
        var backCalled = false

        composeTestRule.setContent {
            PixelAppLockTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertThat(backCalled).isTrue()
    }

    @Test
    fun changeMasterPinRow_opensChangePinSheet() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Change Passcode").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Current 6-Digit Passcode").assertExists()
    }

    @Test
    fun changePinSheet_validatesMismatchAndSavesNewPin() {
        var savedPin: String? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                ChangePinSheetContent(
                    onDismiss = {},
                    onSavePin = { savedPin = it }
                )
            }
        }

        // Verify sheet fields exist and test mismatch validation
        composeTestRule.onNodeWithText("New 6-Digit Passcode").performTextInput("839201")
        composeTestRule.onNodeWithText("Confirm Passcode").performTextInput("999999")
        composeTestRule.onNodeWithText("Save Passcode").performClick()

        // Mismatch validation error
        composeTestRule.onNodeWithText("Passcodes do not match").assertExists()
        assertThat(savedPin).isNull()

        // Clear and enter matching PIN
        composeTestRule.onNodeWithText("Confirm Passcode").performTextClearance()
        composeTestRule.onNodeWithText("Confirm Passcode").performTextInput("839201")
        composeTestRule.onNodeWithText("Save Passcode").performClick()

        assertThat(savedPin).isEqualTo("839201")
    }

    @Test
    fun globalGracePeriodSheet_selectsGracePeriodOption() {
        var selectedSeconds: Int? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                GlobalGracePeriodSheetContent(
                    currentGracePeriodSeconds = 0,
                    onSelectGracePeriod = { selectedSeconds = it }
                )
            }
        }

        composeTestRule.onNodeWithText("After 1 minute").performClick()

        assertThat(selectedSeconds).isEqualTo(60)
    }

    @Test
    fun scheduleSheet_opensAndUpdatesHours() {
        var savedStart: Int? = null
        var savedEnd: Int? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                ScheduleSheetContent(
                    initialStartHour = 23,
                    initialEndHour = 7,
                    onDismiss = {},
                    onSaveSchedule = { start, end ->
                        savedStart = start
                        savedEnd = end
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("10:00 PM").performClick()
        composeTestRule.onNodeWithText("6:00 AM").performClick()
        composeTestRule.onNodeWithText("Save Schedule").performClick()

        assertThat(savedStart).isEqualTo(22)
        assertThat(savedEnd).isEqualTo(6)
    }

    @Test
    fun quickTilePauseSheet_selectsDuration() {
        var selectedMinutes: Int? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                QuickTilePauseSheetContent(
                    currentPauseMinutes = 15,
                    onSelectDuration = { selectedMinutes = it }
                )
            }
        }

        composeTestRule.onNodeWithText("30 minutes").performClick()

        assertThat(selectedMinutes).isEqualTo(30)
    }

    @Test
    fun securityPrivacyFaqCard_displaysQuestionsAndTogglesAccordion() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                SecurityPrivacyFaqCard()
            }
        }

        // Header and badge exist
        composeTestRule.onNodeWithText("Help & Privacy").assertExists()
        composeTestRule.onNodeWithText("FAQ").assertExists()

        // Questions exist
        val question1 = "Why does Android show a \"Review app with full device access\" alert?"
        val question2 = "Does Pixel Guard read my screen or private messages?"
        composeTestRule.onNodeWithText(question1).assertExists()
        composeTestRule.onNodeWithText(question2).assertExists()

        val answer1 = "Android regularly shows this reminder for any app that detects app launches, including password managers and app lockers. Pixel Guard is completely safe, and you can safely swipe this reminder away."

        // Answer should not exist initially
        composeTestRule.onNodeWithText(answer1).assertDoesNotExist()

        // Click question 1 to expand
        composeTestRule.onNodeWithText(question1).performClick()

        // Now answer 1 exists
        composeTestRule.onNodeWithText(answer1).assertExists()

        // Click question 1 again to collapse
        composeTestRule.onNodeWithText(question1).performClick()

        // Answer 1 is hidden again
        composeTestRule.onNodeWithText(answer1).assertDoesNotExist()
    }
}
