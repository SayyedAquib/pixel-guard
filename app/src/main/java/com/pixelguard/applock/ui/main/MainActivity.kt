package com.pixelguard.applock.ui.main

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixelguard.applock.AppLockApplication
import android.app.Activity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pixelguard.applock.service.overlay.LockOverlayScreen
import com.pixelguard.applock.ui.logs.IntruderLogsScreen
import com.pixelguard.applock.ui.navigation.Destinations
import com.pixelguard.applock.ui.onboarding.OnboardingScreen
import com.pixelguard.applock.ui.settings.SettingsScreen
import com.pixelguard.applock.ui.theme.PixelAppLockTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // H-5: Tapjacking & Overlay suppression
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setHideOverlayWindows(true)
            } catch (e: Exception) {
                if (com.pixelguard.applock.BuildConfig.DEBUG) {
                    android.util.Log.w("MainActivity", "Unable to setHideOverlayWindows: ${e.message}")
                }
            }
        }
        window.decorView.filterTouchesWhenObscured = true

        setContent {
            PixelAppLockTheme {
                AppLockRoot(
                    viewModel = viewModel,
                    onPromptBiometric = { onSuccess, onFailure ->
                        showBiometricPrompt(onSuccess, onFailure)
                    }
                )
            }
        }
    }



    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions(this)
        viewModel.loadApps()
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Pixel Guard")
            .setSubtitle("Confirm your fingerprint or Face Unlock to access settings")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            onFailure()
        }
    }
}

@Composable
fun AppLockRoot(
    viewModel: MainViewModel,
    onPromptBiometric: (onSuccess: () -> Unit, onFailure: () -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val isPinConfigured by viewModel.hasPinSet.collectAsState()
    var gateState by remember { mutableStateOf<AppGateState>(AppGateState.Loading) }
    var lastStopTimestamp by remember { mutableStateOf(0L) }
    val selfLockThresholdMs = 30_000L
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current
    val isNight = com.pixelguard.applock.domain.usecase.SessionManager.isNightLockdownActive(
        prefs.isScheduleEnabled,
        prefs.scheduleStartHour,
        prefs.scheduleEndHour
    )
    val isNightPinOnly = isNight && prefs.isNightLockdownPinOnly
    val isLockedOut = com.pixelguard.applock.domain.usecase.SessionManager.isLockedOut(context.packageName)
    val allowBio = prefs.isBiometricsEnabled && !isNightPinOnly && !isLockedOut

    DisposableEffect(lifecycleOwner, isPinConfigured, allowBio) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                lastStopTimestamp = android.os.SystemClock.elapsedRealtime()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isPinConfigured == true) {
                    val elapsed = if (lastStopTimestamp > 0L) {
                        android.os.SystemClock.elapsedRealtime() - lastStopTimestamp
                    } else {
                        0L
                    }
                    if (lastStopTimestamp > 0L && elapsed > selfLockThresholdMs) {
                        gateState = AppGateState.Locked(allowBio, com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(context.packageName))
                    }
                    if (gateState is AppGateState.Locked && allowBio) {
                        onPromptBiometric(
                            { gateState = AppGateState.Authenticated },
                            { /* user cancelled, stays on PIN screen */ }
                        )
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isPinConfigured, allowBio) {
        when (isPinConfigured) {
            null -> gateState = AppGateState.Loading
            false -> gateState = AppGateState.FirstTimeOnboarding
            true -> {
                if (gateState !is AppGateState.Authenticated) {
                    gateState = AppGateState.Locked(allowBio, com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(context.packageName))
                }
            }
        }
    }

    val startDestination = if (permissionStatus.isAllGranted && isPinConfigured == true) Destinations.MAIN else Destinations.ONBOARDING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val hapticManager = remember(context) { com.pixelguard.applock.ui.common.HapticManager(context) }

        LaunchedEffect(prefs.isRecentsMaskingEnabled) {
            val activity = (context as? Activity)
            if (prefs.isRecentsMaskingEnabled) {
                activity?.window?.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        if (gateState is AppGateState.Locked) {
            val scope = rememberCoroutineScope()
            var verificationState by remember { mutableStateOf(com.pixelguard.applock.service.overlay.LockVerificationState.Idle) }
            LockOverlayScreen(
                targetPackage = context.packageName,
                targetAppName = "Pixel Guard",
                targetIcon = null,
                targetIconBitmap = null,
                verificationState = verificationState,
                isDecoyEnabled = false,
                isBiometricEnabled = allowBio,
                isHapticsEnabled = prefs.isHapticsEnabled,
                isAnimationsEnabled = prefs.isAnimationsEnabled,
                lockoutSecondsProvider = { com.pixelguard.applock.domain.usecase.SessionManager.getRemainingLockoutSeconds(context.packageName) },
                attemptsUntilLockoutProvider = { com.pixelguard.applock.domain.usecase.SessionManager.getAttemptsUntilLockout(context.packageName) },
                onUnlockSuccess = {
                    if (allowBio) {
                        onPromptBiometric(
                            {
                                hapticManager.unlockSuccess(prefs.isHapticsEnabled)
                                viewModel.resetSelfFailedAttempts()
                                gateState = AppGateState.Authenticated
                            },
                            { /* user cancelled, stays on PIN screen */ }
                        )
                    }
                },
                onPinSubmitted = { pin ->
                    verificationState = com.pixelguard.applock.service.overlay.LockVerificationState.Verifying
                    scope.launch {
                        val isCorrect = viewModel.verifySelfPin(pin)
                        if (isCorrect) {
                            verificationState = com.pixelguard.applock.service.overlay.LockVerificationState.Success
                            hapticManager.unlockSuccess(prefs.isHapticsEnabled)
                            gateState = AppGateState.Authenticated
                        } else {
                            verificationState = com.pixelguard.applock.service.overlay.LockVerificationState.Failed
                            hapticManager.authFailed(prefs.isHapticsEnabled)
                        }
                    }
                },
                onExitToHome = {
                    (context as? Activity)?.finish()
                }
            )
        } else if (gateState is AppGateState.Authenticated || gateState is AppGateState.FirstTimeOnboarding) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = {
                        navController.navigate(Destinations.MAIN) {
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Destinations.MAIN) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                    onNavigateToIntruders = { navController.navigate(Destinations.INTRUDERS) },
                    onNavigateToPermissions = { navController.navigate(Destinations.ONBOARDING) }
                )
            }

            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Destinations.INTRUDERS) {
                IntruderLogsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}
