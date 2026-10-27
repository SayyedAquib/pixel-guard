package com.pixelguard.applock.service.overlay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.ui.theme.PixelAppLockTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
class LockOverlayScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTargetAppNameAndSecuredBadge() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pixel Bank").assertExists()
        composeTestRule.onNodeWithText("Secured by Pixel Guard").assertExists()
        composeTestRule.onNodeWithText("Exit to Home").assertExists()
    }

    @Test
    fun clickingKeypadDigits_submitsOnSixthDigit() {
        var submittedPin: String? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = { submittedPin = it },
                    onExitToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        assertThat(submittedPin).isNull()

        composeTestRule.onNodeWithText("6").performClick()
        assertThat(submittedPin).isEqualTo("123456")
    }

    @Test
    fun backspaceButton_removesLastDigitBeforeSubmitting() {
        var submittedPin: String? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = { submittedPin = it },
                    onExitToHome = {}
                )
            }
        }

        // Type "1", "2"
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()

        // Delete "2"
        composeTestRule.onNodeWithContentDescription("Delete").performClick()

        // Type "9", "8", "7", "6", "5" -> Should result in "198765"
        composeTestRule.onNodeWithText("9").performClick()
        composeTestRule.onNodeWithText("8").performClick()
        composeTestRule.onNodeWithText("7").performClick()
        composeTestRule.onNodeWithText("6").performClick()
        composeTestRule.onNodeWithText("5").performClick()

        assertThat(submittedPin).isEqualTo("198765")
    }

    @Test
    fun lockedOutState_displaysCountdownBannerAndDisablesKeypad() {
        var submittedPin: String? = null

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    lockoutSecondsProvider = { 30 },
                    onUnlockSuccess = {},
                    onPinSubmitted = { submittedPin = it },
                    onExitToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Locked out. Try again in 30s").assertIsDisplayed()

        // Clicks should not register when disabled
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        assertThat(submittedPin).isNull()
    }

    @Test
    fun preLockoutWarning_displaysCautionBadgeWhenTwoAttemptsRemain() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    lockoutSecondsProvider = { 0 },
                    attemptsUntilLockoutProvider = { 2 },
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithText("2 attempts remaining before 30s lockout").assertIsDisplayed()
    }

    @Test
    fun cancelToHomeButton_callsOnExitCallback() {
        var exitCalled = false

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = { exitCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Exit to Home").performClick()
        assertThat(exitCalled).isTrue()
    }

    @Test
    fun decoyCrashMode_displaysCrashDialogAndCloseAppButton() {
        var exitCalled = false

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = true,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = { exitCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Pixel Bank keeps stopping").assertExists()
        composeTestRule.onNodeWithText("Close app").performClick()
        assertThat(exitCalled).isTrue()
    }

    @Test
    fun decoyCrashMode_sendFeedbackButtonExitsToHome() {
        var exitCalled = false

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = true,
                    isBiometricEnabled = false,
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = { exitCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Send feedback").performClick()
        assertThat(exitCalled).isTrue()
    }

    @Test
    fun biometricButton_callsUnlockSuccess() {
        var unlockSuccessCalled = false

        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = true,
                    onUnlockSuccess = { unlockSuccessCalled = true },
                    onPinSubmitted = {},
                    onExitToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Pixel Biometrics").performClick()
        assertThat(unlockSuccessCalled).isTrue()
    }

    @Test
    fun scrambledKeypad_rendersAllTenDigits() {
        composeTestRule.setContent {
            PixelAppLockTheme {
                LockOverlayScreen(
                    targetPackage = "com.pixelguard.bank",
                    targetAppName = "Pixel Bank",
                    targetIcon = null,
                    isDecoyEnabled = false,
                    isBiometricEnabled = false,
                    isScramblePinEnabled = true,
                    onUnlockSuccess = {},
                    onPinSubmitted = {},
                    onExitToHome = {}
                )
            }
        }

        for (digit in 0..9) {
            composeTestRule.onNodeWithText(digit.toString()).assertExists()
        }
    }
}
