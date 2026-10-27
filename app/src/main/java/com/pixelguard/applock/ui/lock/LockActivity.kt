package com.pixelguard.applock.ui.lock

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.BuildConfig
import com.pixelguard.applock.data.model.IntruderRecord
import com.pixelguard.applock.data.model.isPinOnly
import com.pixelguard.applock.domain.usecase.SessionManager
import com.pixelguard.applock.service.camera.SilentCameraCapture
import com.pixelguard.applock.service.overlay.LockOverlayScreen
import com.pixelguard.applock.ui.theme.PixelAppLockTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class LockActivity : FragmentActivity() {

    private var targetPackage: String = ""
    private var appName: String = ""
    private var hasCapturedSelfieThisSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyZeroTransition()
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHomeAndFinish()
            }
        })

        val isPauseAuthRequest = intent.action == ACTION_PAUSE_REQUEST
        if (isPauseAuthRequest) {
            targetPackage = "com.pixelguard.applock"
            appName = "Pixel Guard"
        } else {
            targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
            if (targetPackage.isEmpty()) {
                finish()
                return
            }
        }

        // PG-SEC-04 Defense: Prevent split-screen / multi-window bypass
        if (isInMultiWindowMode) {
            goToHomeAndFinish()
            return
        }

        val packageManager = packageManager
        if (!isPauseAuthRequest) {
            appName = try {
                val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                targetPackage
            }
        }

        val appIcon = try {
            packageManager.getApplicationIcon(targetPackage)
        } catch (_: Exception) {
            null
        }

        val repository = AppLockApplication.instance.repository
        val hapticManager = com.pixelguard.applock.ui.common.HapticManager(this)

        // Anti-Tapjacking / UI Redressing Defense (OWASP M8):
        // Hide all non-system overlay windows while LockActivity is visible
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                window.setHideOverlayWindows(true)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.w("LockActivity", "Unable to setHideOverlayWindows: ${e.message}")
            }
        }

        setContent {
            val prefs by repository.preferences.collectAsState(initial = com.pixelguard.applock.data.model.LockPreferences())
            LaunchedEffect(prefs.isRecentsMaskingEnabled) {
                if (prefs.isRecentsMaskingEnabled) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            val isNight = SessionManager.isNightLockdownActive(
                prefs.isScheduleEnabled,
                prefs.scheduleStartHour,
                prefs.scheduleEndHour
            )
            val allowBiometrics = prefs.isBiometricsEnabled && !(isNight && prefs.isNightLockdownPinOnly) && !prefs.isPinOnly(targetPackage)
            val systemAnimationsEnabled = try {
                android.provider.Settings.Global.getFloat(
                    contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                ) != 0f
            } catch (_: Exception) {
                true
            }
            val effectiveAnimations = prefs.isAnimationsEnabled && systemAnimationsEnabled

            PixelAppLockTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F141A).copy(alpha = 0.98f))
                ) {
                    LockOverlayScreen(
                        targetPackage = targetPackage,
                        targetAppName = if (isPauseAuthRequest) "Pause Protection" else appName,
                        targetIcon = appIcon,
                        isDecoyEnabled = prefs.isDecoyCrashEnabled && !isPauseAuthRequest,
                        isBiometricEnabled = allowBiometrics,
                        isHapticsEnabled = prefs.isHapticsEnabled,
                        isAnimationsEnabled = effectiveAnimations,
                        isDynamicThemingEnabled = prefs.isDynamicAppThemingEnabled,
                        isScramblePinEnabled = prefs.isScramblePinEnabled,
                        lockoutSecondsProvider = { SessionManager.getRemainingLockoutSeconds(targetPackage) },
                        attemptsUntilLockoutProvider = { SessionManager.getAttemptsUntilLockout(targetPackage) },
                        onUnlockSuccess = {
                            if (allowBiometrics && !SessionManager.isLockedOut(targetPackage)) {
                                showBiometricPrompt()
                            }
                        },
                        onPinSubmitted = { pin ->
                            lifecycleScope.launch {
                                val currentPrefs = repository.preferences.first()
                                when (repository.verifyPinResult(pin, targetPackage)) {
                                    is com.pixelguard.applock.data.repository.VerifyPinResult.Success -> {
                                        hapticManager.unlockSuccess(currentPrefs.isHapticsEnabled)
                                        hasCapturedSelfieThisSession = false
                                        unlockAndFinish()
                                    }
                                    is com.pixelguard.applock.data.repository.VerifyPinResult.WrongPin -> {
                                        hapticManager.authFailed(currentPrefs.isHapticsEnabled)
                                        val attempts = SessionManager.getFailedAttempts(targetPackage)
                                        if (currentPrefs.isIntruderSelfieEnabled && attempts >= currentPrefs.failedAttemptsThreshold && !hasCapturedSelfieThisSession) {
                                            hasCapturedSelfieThisSession = true
                                            captureIntruder()
                                        }
                                    }
                                    is com.pixelguard.applock.data.repository.VerifyPinResult.LockedOut -> {
                                        hapticManager.authFailed(currentPrefs.isHapticsEnabled)
                                    }
                                    is com.pixelguard.applock.data.repository.VerifyPinResult.Unrecoverable -> {
                                        hapticManager.authFailed(currentPrefs.isHapticsEnabled)
                                    }
                                }
                            }
                        },
                        onExitToHome = {
                            goToHomeAndFinish()
                        }
                    )
                }
            }
        }

        // Automatically trigger Pixel 9 BiometricPrompt on launch unless disabled or Night PIN-Only active
        lifecycleScope.launch {
            val prefs = repository.preferences.first()
            val isNight = SessionManager.isNightLockdownActive(
                prefs.isScheduleEnabled,
                prefs.scheduleStartHour,
                prefs.scheduleEndHour
            )
            val allowBiometrics = prefs.isBiometricsEnabled && !(isNight && prefs.isNightLockdownPinOnly) && !prefs.isPinOnly(targetPackage)
            if (allowBiometrics && !SessionManager.isLockedOut(targetPackage)) {
                showBiometricPrompt()
            }
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometrics not available or not enrolled, graceful fallback to PIN keypad
            return
        }

        val hapticManager = com.pixelguard.applock.ui.common.HapticManager(this)
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    lifecycleScope.launch {
                        val prefs = AppLockApplication.instance.repository.preferences.first()
                        hapticManager.unlockSuccess(prefs.isHapticsEnabled)
                    }
                    unlockAndFinish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // When user cancels or chooses "Use PIN", gracefully fall back to PIN keypad
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    val attempts = SessionManager.incrementFailedAttempts(targetPackage)
                    lifecycleScope.launch {
                        val prefs = AppLockApplication.instance.repository.preferences.first()
                        hapticManager.authFailed(prefs.isHapticsEnabled)
                        if (prefs.isIntruderSelfieEnabled && attempts >= prefs.failedAttemptsThreshold) {
                            captureIntruder()
                        }
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $appName")
            .setSubtitle("Confirm fingerprint or Face Unlock to continue")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("LockActivity", "Biometric authentication error: ${e.message}")
        }
    }

    private fun applyZeroTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun unlockAndFinish() {
        if (intent.action == ACTION_PAUSE_REQUEST) {
            val minutes = intent.getIntExtra(EXTRA_PAUSE_MINUTES, 15)
            SessionManager.pauseProtection(minutes)
            finish()
            applyZeroTransition()
            return
        }
        SessionManager.markAppUnlocked(targetPackage)
        finish()
        applyZeroTransition()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) {
            goToHomeAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        // H-4 Resume Guard: Revalidate that target app was actually unlocked.
        // If an interrupting activity (phone call, system dialog) finishes and LockActivity resumes,
        // ensure target app remains locked unless an explicit successful unlock occurred.
        if (!isFinishing && targetPackage.isNotEmpty() && intent.action != ACTION_PAUSE_REQUEST) {
            if (!SessionManager.isRecentlyUnlocked(targetPackage, 3000L)) {
                SessionManager.lockPackage(targetPackage)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If user leaves lock screen (e.g. swipes to Home/Recents), immediately wipe temporary session
        // unless the app was just successfully unlocked
        if (targetPackage.isNotEmpty() && intent.action != ACTION_PAUSE_REQUEST) {
            if (!SessionManager.isRecentlyUnlocked(targetPackage, 3000L)) {
                SessionManager.lockPackage(targetPackage)
            }
        }
        // PG-SEC-05 Defense: Redirect to Home instead of popping the activity stack onto the locked app
        goToHomeAndFinish()
    }

    private fun goToHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
        applyZeroTransition()
    }

    private fun captureIntruder() {
        val cameraCapture = SilentCameraCapture(this)
        cameraCapture.captureIntruderPhoto { photoFile ->
            lifecycleScope.launch {
                val record = IntruderRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    targetPackageName = targetPackage,
                    targetAppName = appName,
                    photoPath = photoFile.absolutePath
                )
                AppLockApplication.instance.repository.addIntruderRecord(record)
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val ACTION_PAUSE_REQUEST = "com.pixelguard.applock.ACTION_PAUSE_REQUEST"
        const val EXTRA_PAUSE_MINUTES = "extra_pause_minutes"
    }
}
