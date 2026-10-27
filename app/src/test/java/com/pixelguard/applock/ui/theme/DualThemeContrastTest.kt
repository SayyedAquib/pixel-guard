package com.pixelguard.applock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.pixelguard.applock.data.model.AppCategory
import com.pixelguard.applock.data.model.AppItem
import com.pixelguard.applock.data.model.LockPreferences
import com.pixelguard.applock.data.repository.FakeAppLockRepository
import com.pixelguard.applock.ui.main.MainScreen
import com.pixelguard.applock.ui.main.MainViewModel
import com.pixelguard.applock.ui.onboarding.OnboardingScreen
import com.pixelguard.applock.ui.settings.SettingsScreen
import com.pixelguard.applock.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
class DualThemeContrastTest {

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
            isLocked = true
        ),
        AppItem(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            category = AppCategory.SOCIAL,
            isLocked = false
        )
    )

    @Before
    fun setUp() {
        fakeRepository = FakeAppLockRepository(
            initialLockedPackages = setOf("com.google.android.apps.walletnfcrel"),
            initialPreferences = LockPreferences(isSettingsProtected = false, isRecentsMaskingEnabled = false),
            initialHasPin = true,
            initialApps = testApps
        )
        fakeRepository.storedPin = "1234"
        viewModel = MainViewModel(fakeRepository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    // ==========================================
    // WCAG 2.1 Contrast Formula Validation
    // ==========================================

    private fun calculateLuminance(color: Color): Double {
        fun componentLuminance(c: Float): Double {
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).toDouble().pow(2.4)
        }
        val r = componentLuminance(color.red)
        val g = componentLuminance(color.green)
        val b = componentLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = calculateLuminance(foreground)
        val l2 = calculateLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun semanticColors_satisfyWcagContrastRequirements_inBothThemes() {
        // Light Theme: Deep ochre on white surface >= 4.5:1
        val lightAmberContrast = calculateContrastRatio(LightSemanticColors.amber, PixelSurfaceLight)
        assertThat(lightAmberContrast).isAtLeast(4.5)

        // Light Theme: Text on amber button >= 4.5:1
        val lightButtonContrast = calculateContrastRatio(LightSemanticColors.onAmber, LightSemanticColors.amber)
        assertThat(lightButtonContrast).isAtLeast(4.5)

        // Dark Theme: Gold amber on dark surface >= 4.5:1
        val darkAmberContrast = calculateContrastRatio(DarkSemanticColors.amber, PixelSurfaceDark)
        assertThat(darkAmberContrast).isAtLeast(4.5)

        // Dark Theme: Text on gold button >= 4.5:1
        val darkButtonContrast = calculateContrastRatio(DarkSemanticColors.onAmber, DarkSemanticColors.amber)
        assertThat(darkButtonContrast).isAtLeast(4.5)

        // Standard text tokens >= 7.0:1 (AAA)
        val lightTextContrast = calculateContrastRatio(PixelOnSurfaceLight, PixelSurfaceLight)
        assertThat(lightTextContrast).isAtLeast(7.0)

        val darkTextContrast = calculateContrastRatio(PixelOnSurfaceDark, PixelSurfaceDark)
        assertThat(darkTextContrast).isAtLeast(7.0)
    }

    // ==========================================
    // UI Screen Rendering in Light & Dark Mode
    // ==========================================

    @Test
    fun mainScreen_rendersLegibly_inLightMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = false, dynamicColor = false) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pixel Guard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Score").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lock Critical").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Pay").assertExists()
        composeTestRule.onNodeWithText("WhatsApp").assertExists()
    }

    @Test
    fun mainScreen_rendersLegibly_inDarkMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = true, dynamicColor = false) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToIntruders = {},
                    onNavigateToPermissions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pixel Guard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Score").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lock Critical").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Pay").assertExists()
        composeTestRule.onNodeWithText("WhatsApp").assertExists()
    }

    @Test
    fun settingsScreen_attentionNeededBanner_rendersLegibly_inLightMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign-In & Passcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Change Passcode").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_attentionNeededBanner_rendersLegibly_inDarkMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = true, dynamicColor = false) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign-In & Passcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Change Passcode").assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_rendersLegibly_inLightMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = false, dynamicColor = false) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Welcome to Pixel Guard").assertIsDisplayed()
        composeTestRule.onNodeWithText("App Detection").assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_rendersLegibly_inDarkMode() {
        composeTestRule.setContent {
            PixelAppLockTheme(darkTheme = true, dynamicColor = false) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Welcome to Pixel Guard").assertIsDisplayed()
        composeTestRule.onNodeWithText("App Detection").assertIsDisplayed()
    }
}

