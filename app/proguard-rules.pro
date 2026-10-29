# ==============================================================================
# Pixel Guard ProGuard & R8 Optimization Rules
# Target: Release Builds on Android 15-17 (Pixel 9 Series)
# ==============================================================================

# General Optimization & Metadata Integrity
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn java.lang.invoke.**

# Keep all @Keep annotated members and classes
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ------------------------------------------------------------------------------
# 1. Data Models & JSON Serialization Safety
# ------------------------------------------------------------------------------
# Models serialized to DataStore JSON, encrypted containers, or SecuritySnapshot
-keep class com.pixelguard.applock.data.model.** { *; }
-keep class com.pixelguard.applock.data.local.SecuritySnapshot { *; }
-keep class com.pixelguard.applock.data.local.PinCredentials { *; }
-keep class com.pixelguard.applock.data.local.DiagnosticExporter$* { *; }

# ------------------------------------------------------------------------------
# 2. AndroidX Camera2 & Headless Intruder Capture
# ------------------------------------------------------------------------------
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-dontwarn androidx.camera.**

# ------------------------------------------------------------------------------
# 3. AndroidX Biometric & Titan M2 Hardware Security
# ------------------------------------------------------------------------------
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ------------------------------------------------------------------------------
# 4. Jetpack DataStore Preferences
# ------------------------------------------------------------------------------
-keep class androidx.datastore.preferences.core.** { *; }
-dontwarn androidx.datastore.**

# ------------------------------------------------------------------------------
# 5. Jetpack Compose UI Stability
# ------------------------------------------------------------------------------
-keepclassmembers class * implements androidx.compose.runtime.State { *; }
