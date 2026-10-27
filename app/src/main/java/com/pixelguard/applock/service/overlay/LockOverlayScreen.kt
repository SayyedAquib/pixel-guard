package com.pixelguard.applock.service.overlay

import android.graphics.drawable.Drawable
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pixelguard.applock.ui.common.HapticManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockOverlayScreen(
    targetPackage: String,
    targetAppName: String,
    targetIcon: Drawable?,
    isDecoyEnabled: Boolean,
    isBiometricEnabled: Boolean,
    isHapticsEnabled: Boolean = true,
    isAnimationsEnabled: Boolean = true,
    isDynamicThemingEnabled: Boolean = true,
    isScramblePinEnabled: Boolean = false,
    lockoutSecondsProvider: () -> Int = { 0 },
    attemptsUntilLockoutProvider: () -> Int = { 5 },
    onUnlockSuccess: () -> Unit,
    onPinSubmitted: (String) -> Unit,
    onExitToHome: () -> Unit
) {
    var showDecoy by remember { mutableStateOf(isDecoyEnabled) }
    var pinInput by remember { mutableStateOf("") }
    var scrambleKey by remember { mutableStateOf(0) }
    var currentLockout by remember { mutableStateOf(lockoutSecondsProvider()) }
    var attemptsUntilLockout by remember { mutableStateOf(attemptsUntilLockoutProvider()) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val isKeypadDisabled = currentLockout > 0 || isVerifying || isUnlocked
    val shakeOffset = remember { Animatable(0f) }
    val dotErrorAnim = remember { Animatable(0f) }
    val dotFadeAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }

    LaunchedEffect(currentLockout) {
        while (currentLockout > 0) {
            delay(1000L)
            currentLockout = lockoutSecondsProvider()
            attemptsUntilLockout = attemptsUntilLockoutProvider()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "biometric_pulse")
    val pulseScale by if (isAnimationsEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val pulseAlpha by if (isAnimationsEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }

    val defaultPrimary = Color(0xFFA8C7FA)
    val brandColor = remember(targetPackage, targetIcon, defaultPrimary, isDynamicThemingEnabled) {
        if (isDynamicThemingEnabled) {
            resolveAppBrandColor(targetPackage, targetIcon, defaultPrimary)
        } else {
            defaultPrimary
        }
    }

    val overlayColorScheme = remember(brandColor) {
        val lum = (0.2126f * brandColor.red + 0.7152f * brandColor.green + 0.0722f * brandColor.blue)
        val onPrimaryColor = if (lum > 0.45f) Color(0xFF000000) else Color(0xFFFFFFFF)
        darkColorScheme(
            primary = brandColor,
            onPrimary = onPrimaryColor,
            surface = Color(0xFF101418),
            onSurface = Color(0xFFE2E2E6),
            surfaceVariant = Color(0xFF282F37),
            onSurfaceVariant = Color(0xFFC4C7C5),
            outline = Color(0xFF8B919A),
            outlineVariant = Color(0xFF3F4750),
            error = Color(0xFFFFB4AB)
        )
    }

    MaterialTheme(colorScheme = overlayColorScheme) {
        val backgroundBrush = remember(brandColor) {
            Brush.verticalGradient(
                0.0f to brandColor.copy(alpha = 0.18f),
                0.28f to Color(0xFF0D1217),
                1.0f to Color(0xFF000000)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            // Real-time Hardware-Accelerated Frosted Glass Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                brandColor.copy(alpha = 0.26f),
                                Color.Transparent
                            ),
                            radius = 700f
                        )
                    )
            )
        if (showDecoy) {
            // Decoy Fake Crash Dialog
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    showDecoy = false // Secret unlock gesture: Long press dialog
                                }
                            )
                        },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$targetAppName keeps stopping",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "An unexpected error occurred while running $targetAppName. The system has stopped this application to protect data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onExitToHome) {
                                Text(text = "Send feedback")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onExitToHome
                            ) {
                                Text(text = "Close app", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Real Modern App Lock Interface (Frosted Glass & Ambient Biometrics)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Target App Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    // Dynamic Ambient Aura with Radial Glow & Pulse
                    Box(contentAlignment = Alignment.Center) {
                        // Ambient Radial Glow
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            (if (isUnlocked) Color(0xFF34A853) else brandColor).copy(alpha = 0.40f * pulseAlpha),
                                            (if (isUnlocked) Color(0xFF34A853) else brandColor).copy(alpha = 0.12f * pulseAlpha),
                                            Color.Transparent
                                        ),
                                        radius = 200f
                                    ),
                                    shape = CircleShape
                                )
                        )

                        if (isAnimationsEnabled) {
                            // Outer glowing ring
                            Box(
                                modifier = Modifier
                                    .size((92 * pulseScale).dp)
                                    .clip(CircleShape)
                                    .background((if (isUnlocked) Color(0xFF34A853) else brandColor).copy(alpha = pulseAlpha * 0.24f))
                            )
                            // Inner glowing ring
                            Box(
                                modifier = Modifier
                                    .size((80 * pulseScale).dp)
                                    .clip(CircleShape)
                                    .background((if (isUnlocked) Color(0xFF34A853) else brandColor).copy(alpha = pulseAlpha * 0.38f))
                            )
                        }

                        val unlockBurstScale by animateFloatAsState(
                            targetValue = if (isUnlocked) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "unlock_burst_scale"
                        )

                        // Center Lock / App Icon
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer {
                                    scaleX = unlockBurstScale
                                    scaleY = unlockBurstScale
                                }
                                .clip(CircleShape)
                                .background((if (isUnlocked) Color(0xFF34A853) else brandColor).copy(alpha = if (isUnlocked) 0.35f else 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUnlocked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Unlocked",
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier.size(38.dp)
                                )
                            } else if (targetIcon != null) {
                                val bitmap = remember(targetIcon) {
                                    try {
                                        targetIcon.toBitmap(120, 120)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = targetAppName,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = brandColor,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = brandColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = targetAppName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Secured by Pixel Guard",
                        style = MaterialTheme.typography.bodySmall,
                        color = brandColor,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    if (isKeypadDisabled) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Locked out. Try again in ${currentLockout}s",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val maxPenalty = remember(currentLockout) { if (currentLockout > 30) 60f else 30f }
                            val progress = (currentLockout.toFloat() / maxPenalty).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(3.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            )
                        }
                    } else {
                        if (attemptsUntilLockout in 1..2) {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$attemptsUntilLockout ${if (attemptsUntilLockout == 1) "attempt" else "attempts"} remaining before 30s lockout",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // PIN Dots with Shake, Error Morph and Spring-Loaded Bounce & Liquid Ripple
                        val errorRed = MaterialTheme.colorScheme.error
                        val activeDotColor = lerp(brandColor, errorRed, dotErrorAnim.value)
                        val activeAlpha = dotFadeAnim.value

                        Row(
                            modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(6) { index ->
                                val isFilled = index < pinInput.length
                                val dotScale by animateFloatAsState(
                                    targetValue = if (isFilled) 1.25f else 1.0f,
                                    animationSpec = if (isAnimationsEnabled) {
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    } else {
                                        snap()
                                    },
                                    label = "pin_dot_bounce_$index"
                                )
                                val rippleScale by animateFloatAsState(
                                    targetValue = if (isFilled) 1.6f else 1.0f,
                                    animationSpec = if (isAnimationsEnabled) spring(stiffness = Spring.StiffnessLow) else snap(),
                                    label = "pin_dot_ripple_$index"
                                )

                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFilled && isAnimationsEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer {
                                                    scaleX = rippleScale
                                                    scaleY = rippleScale
                                                    alpha = (0.35f * activeAlpha).coerceIn(0f, 1f)
                                                }
                                                .clip(CircleShape)
                                                .background(activeDotColor)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer {
                                                scaleX = dotScale
                                                scaleY = dotScale
                                                alpha = activeAlpha
                                            }
                                            .clip(CircleShape)
                                            .background(
                                                if (isFilled) activeDotColor
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f * activeAlpha)
                                            )
                                            .then(
                                                if (!isFilled) Modifier.border(1.dp, activeDotColor.copy(alpha = 0.4f * activeAlpha), CircleShape)
                                                else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Glassmorphic Numeric Keypad with Elastic Shape-Morphing
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val digits = remember(isScramblePinEnabled, scrambleKey) {
                        if (isScramblePinEnabled) {
                            listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").shuffled()
                        } else {
                            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                        }
                    }

                    val rows = listOf(
                        listOf(digits[0], digits[1], digits[2]),
                        listOf(digits[3], digits[4], digits[5]),
                        listOf(digits[6], digits[7], digits[8]),
                        listOf("bio", digits[9], "del")
                    )

                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (item in row) {
                                when (item) {
                                    "bio" -> {
                                        val bioInteraction = remember { MutableInteractionSource() }
                                        val isBioPressed by bioInteraction.collectIsPressedAsState()
                                        val bioScale by animateFloatAsState(
                                            targetValue = if (isBioPressed && isAnimationsEnabled) 0.88f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "bio_scale"
                                        )

                                        IconButton(
                                            onClick = {
                                                if (isBiometricEnabled && !isKeypadDisabled) {
                                                    hapticManager.tapClick(isHapticsEnabled)
                                                    onUnlockSuccess()
                                                }
                                            },
                                            enabled = isBiometricEnabled && !isKeypadDisabled,
                                            interactionSource = bioInteraction,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .graphicsLayer {
                                                    scaleX = bioScale
                                                    scaleY = bioScale
                                                }
                                                .clip(CircleShape)
                                                .background(
                                                    if (isBiometricEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = if (isKeypadDisabled) 0.18f else if (isBioPressed) 0.65f else 0.42f
                                                    )
                                                    else Color.Transparent
                                                )
                                        ) {
                                            if (isBiometricEnabled) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Pixel Biometrics",
                                                    tint = brandColor,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }
                                    "del" -> {
                                        val delInteraction = remember { MutableInteractionSource() }
                                        val isDelPressed by delInteraction.collectIsPressedAsState()
                                        val delScale by animateFloatAsState(
                                            targetValue = if (isDelPressed && isAnimationsEnabled) 0.88f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "del_scale"
                                        )

                                        IconButton(
                                            onClick = {
                                                if (isKeypadDisabled) {
                                                    hapticManager.authFailed(isHapticsEnabled)
                                                    return@IconButton
                                                }
                                                hapticManager.tapClick(isHapticsEnabled)
                                                if (pinInput.isNotEmpty()) {
                                                    pinInput = pinInput.dropLast(1)
                                                }
                                            },
                                            enabled = !isKeypadDisabled,
                                            interactionSource = delInteraction,
                                            modifier = Modifier
                                                .semantics { contentDescription = "Delete" }
                                                .size(72.dp)
                                                .graphicsLayer {
                                                    scaleX = delScale
                                                    scaleY = delScale
                                                }
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = if (isKeypadDisabled) 0.18f else if (isDelPressed) 0.65f else 0.45f
                                                    )
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isKeypadDisabled) 0.35f else 1f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        val digitInteraction = remember { MutableInteractionSource() }
                                        val isDigitPressed by digitInteraction.collectIsPressedAsState()
                                        val digitScale by animateFloatAsState(
                                            targetValue = if (isDigitPressed && isAnimationsEnabled) 0.88f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "digit_scale_$item"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .graphicsLayer {
                                                    scaleX = digitScale
                                                    scaleY = digitScale
                                                }
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = if (isKeypadDisabled) 0.18f else if (isDigitPressed) 0.68f else 0.42f
                                                    )
                                                )
                                                .clickable(
                                                    interactionSource = digitInteraction,
                                                    indication = null,
                                                    enabled = !isKeypadDisabled
                                                ) {
                                                    hapticManager.keypadTick(isHapticsEnabled)
                                                    if (pinInput.length < 6 && !isVerifying) {
                                                        val updated = pinInput + item
                                                        pinInput = updated
                                                        if (updated.length == 6) {
                                                            isVerifying = true
                                                            onPinSubmitted(updated)
                                                            scope.launch {
                                                                kotlinx.coroutines.delay(350L)
                                                                val lockout = lockoutSecondsProvider()
                                                                if (lockout > 0) {
                                                                    currentLockout = lockout
                                                                }
                                                                attemptsUntilLockout = attemptsUntilLockoutProvider()
                                                                if (isAnimationsEnabled) {
                                                                    // Stage 1: Error Red Morph
                                                                    launch { dotErrorAnim.animateTo(1f, tween(60)) }
                                                                    // Stage 2: Elastic Shake
                                                                    shakeOffset.animateTo(22f, tween(45))
                                                                    shakeOffset.animateTo(-22f, tween(45))
                                                                    shakeOffset.animateTo(14f, tween(45))
                                                                    shakeOffset.animateTo(-14f, tween(45))
                                                                    shakeOffset.animateTo(0f, tween(45))
                                                                    // Stage 3: Smooth Dissolve
                                                                    dotFadeAnim.animateTo(0f, tween(120))
                                                                }
                                                                pinInput = ""
                                                                if (isScramblePinEnabled) {
                                                                    scrambleKey++
                                                                }
                                                                dotErrorAnim.snapTo(0f)
                                                                dotFadeAnim.snapTo(1f)
                                                                isVerifying = false
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (isKeypadDisabled) 0.35f else 1f
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer (Back to Home button)
                TextButton(
                    onClick = onExitToHome,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(text = "Exit to Home", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
}

/**
 * Resolves signature brand color from package name or dynamically samples the app icon.
 */
fun resolveAppBrandColor(packageName: String, icon: Drawable?, defaultColor: Color): Color {
    val lower = packageName.lowercase(Locale.ROOT)
    return when {
        lower.contains("whatsapp") -> Color(0xFF25D366)
        lower.contains("instagram") -> Color(0xFFE1306C)
        lower.contains("youtube") -> Color(0xFFFF2222)
        lower.contains("telegram") -> Color(0xFF2AABEE)
        lower.contains("twitter") || lower.contains(".x") -> Color(0xFF1DA1F2)
        lower.contains("chrome") -> Color(0xFF4285F4)
        lower.contains("gmail") -> Color(0xFFEA4335)
        lower.contains("spotify") -> Color(0xFF1DB954)
        lower.contains("snapchat") -> Color(0xFFFFFC00)
        lower.contains("settings") -> Color(0xFF1A73E8)
        lower.contains("reddit") -> Color(0xFFFF4500)
        lower.contains("discord") -> Color(0xFF5865F2)
        lower.contains("facebook") -> Color(0xFF1877F2)
        lower.contains("amazon") -> Color(0xFFFF9900)
        lower.contains("netflix") -> Color(0xFFE50914)
        else -> {
            if (icon != null) {
                try {
                    val bitmap = icon.toBitmap(24, 24)
                    var bestColor = defaultColor
                    var maxSaturation = 0.2f
                    for (x in 2 until 22 step 2) {
                        for (y in 2 until 22 step 2) {
                            val pixel = bitmap.getPixel(x, y)
                            val alpha = android.graphics.Color.alpha(pixel)
                            if (alpha < 128) continue
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            val hsv = FloatArray(3)
                            android.graphics.Color.RGBToHSV(r, g, b, hsv)
                            if (hsv[1] > maxSaturation && hsv[2] > 0.25f && hsv[2] < 0.95f) {
                                maxSaturation = hsv[1]
                                bestColor = Color(pixel)
                            }
                        }
                    }
                    bestColor
                } catch (_: Exception) {
                    defaultColor
                }
            } else {
                defaultColor
            }
        }
    }
}
