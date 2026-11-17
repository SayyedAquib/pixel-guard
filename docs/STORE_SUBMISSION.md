# Google Play Store Submission Package — Pixel Guard

**Target Platform:** Android 15+ (API 35+), Google Pixel 9 Optimized  
**Version:** 1.0.0 (Version Code: 1)  
**Package Name:** `com.pixelguard.applock`  

---

## 1. Foreground Service (FGS) `specialUse` Declaration

Under Android 14+ (API 34+) and Google Play Policy, foreground services utilizing `android:foregroundServiceType="specialUse"` require an explicit declaration in Google Play Console with technical justification for why existing standard FGS types are unsuitable.

### Manifest Configuration:
```xml
<service
    android:name=".service.AppLockForegroundService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Real-time application security enforcement and intrusion prevention" />
</service>
```

### Play Console Form Answers:

#### Q1: Why does your app need to run a foreground service?
> **Answer:**  
> Pixel Guard is a native security utility designed to protect sensitive apps (e.g. banking, messaging, photos) by presenting an immediate lock screen when protected applications are launched. The foreground service runs `AppLockForegroundService`, which monitors foreground application transitions using Android's `UsageStatsManager` in real-time. It requires an active foreground service with a persistent user notification to prevent the Android operating system from terminating the security monitor while the user is actively using their device.

#### Q2: Why cannot this task be handled by any other existing foreground service type?
> **Answer:**  
> None of the pre-defined foreground service types (`camera`, `connectedDevice`, `dataSync`, `health`, `location`, `mediaPlayback`, `mediaProjection`, `microphone`, `phoneCall`, `remoteMessaging`, `shortService`, `systemExempted`) fit application security enforcement:
> 1. It is not synchronizing network data (`dataSync` is restricted to network upload/download tasks).
> 2. It does not play media, record audio, or track location.
> 3. It cannot use `shortService` because security monitoring must remain active while the screen is interactive and user apps are being launched.
> 4. `specialUse` is the only supported category for local security and app protection utilities requiring continuous low-latency foreground state detection.

#### Q3: How is battery consumption minimized?
> **Answer:**  
> `AppLockForegroundService` registers a dynamic broadcast receiver for `Intent.ACTION_SCREEN_OFF`. When the device screen turns off, the monitoring loop coroutine is cancelled immediately, releasing CPU resources and allowing the device to enter full deep sleep (Doze). Monitoring resumes only when `Intent.ACTION_SCREEN_ON` is received. Furthermore, default launcher resolution is cached with a 60-second TTL to avoid redundant IPC calls to PackageManager.

---

## 2. 60-Second Video Demonstration Script (For Google Play Reviewers)

Google Play requires a video demonstrating the user-facing functionality of the Foreground Service:

| Timestamp | Screen Action | Voiceover / Text Overlay |
|---|---|---|
| **0:00 - 0:10** | Open Pixel Guard onboarding screen. Tap "Grant Usage Access" and "Grant Overlay". | "Pixel Guard guides the user through granting Usage Access and Overlay permissions with explicit in-app disclosures." |
| **0:10 - 0:20** | Create a 6-digit passcode. Back on the main screen, lock a target app (e.g. Google Chrome or Files). | "The user configures a 6-digit master passcode and selects sensitive applications to lock." |
| **0:20 - 0:30** | Swipe down the notification shade showing the persistent notification: *"Pixel Guard Active — Protecting your private apps"*. | "A persistent foreground service notification clearly informs the user that security protection is actively running." |
| **0:30 - 0:45** | Launch the locked app. The Pixel Guard lock overlay renders instantly before target content appears. Authenticate via PIN. | "When the user launches the protected app, AppLockForegroundService detects the transition and displays the lock screen." |
| **0:45 - 0:60** | Lock the device screen. Turn the screen on, demonstrating responsive waking and zero background battery drain during screen off. | "The service pauses completely when the screen is turned off to preserve battery life, resuming instantly on wake." |

---

## 3. Google Play Data Safety Declaration

Pixel Guard is **100% offline** and has **NO internet permission** (`android.permission.INTERNET` is not present in AndroidManifest.xml).

### Questionnaire Responses:
- **Does your app collect or share any of the required user data types?**  
  ➔ **No** (Pixel Guard does not collect or share any user data).
- **Is all of the user data collected by your app encrypted in transit?**  
  ➔ **Not applicable** (No data leaves the device; zero network communication).
- **Do you provide a way for users to request that their data be deleted?**  
  ➔ **Yes** (Users can delete all encrypted intruder photos, clear app data in Settings, or uninstall the app to permanently wipe all stored data).
- **Does the app collect Location data?** ➔ **No**
- **Does the app collect Personal Info (Name, Email, etc.)?** ➔ **No**
- **Does the app collect Financial info?** ➔ **No**
- **Does the app collect Health & Fitness info?** ➔ **No**
- **Does the app collect Photos & Videos?** ➔ **No** (Intruder selfies are taken strictly locally upon failed unlock attempts, encrypted with AES-256-GCM via Titan M2 HSM, stored in private app sandbox, and never shared or uploaded).
- **Does the app collect App Activity (App interactions, Installed apps)?** ➔ **No** (Foreground app transitions are inspected strictly in-memory on-device to trigger the lock overlay; no history or analytics are stored or exported).

---

## 4. App Permissions Declaration Table

| Permission | Protection Level | Purpose & Necessity |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Special App Access | Required to detect when a user launches a locked application in real time via `UsageStatsManager`. Completely replaces accessibility services to avoid banking/UPI app blocks. |
| `SYSTEM_ALERT_WINDOW` | Special App Access | Required to draw the hardware-accelerated Compose lock screen (`LockActivity`) over protected applications. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Signature / System | Optional feature to shield and conceal private message previews from locked applications in the system notification shade. |
| `CAMERA` | Dangerous (Runtime) | Optional feature for intruder detection: captures a silent front-camera photo after repeated failed unlock attempts. Stored encrypted on-device. |
| `POST_NOTIFICATIONS` | Dangerous (Runtime) | Required on Android 13+ (API 33+) to display the ongoing Foreground Service status notification. |
| `FOREGROUND_SERVICE` | Normal | Allows running the persistent background protection service. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | Declares specialized security enforcement FGS type for Android 14+. |
| `RECEIVE_BOOT_COMPLETED` | Normal | Restarts protection automatically after the device is rebooted. |
| `USE_BIOMETRIC` | Normal | Enables Titan M2 Class 3 (`BIOMETRIC_STRONG`) fingerprint and face unlock. |
| `VIBRATE` | Normal | Provides haptic feedback during PIN keypad entry and authentication events. |

---

## 5. Store Listing Metadata

### App Title:
`Pixel Guard - App Locker`

### Short Description (80 characters):
`Zero-latency native app locker & intruder detection. 100% offline & private.`

### Full Description:
```
Pixel Guard is a native, privacy-first application locker engineered specifically for Google Pixel devices and modern Android 15+. Protect your banking apps, messaging, galleries, and private files with zero-latency lock overlays, hardware-accelerated biometrics, and zero cloud tracking.

🔒 100% OFFLINE & PRIVATE
Pixel Guard does NOT require internet access. It has zero network code, zero analytics SDKs, and zero cloud connections. Your passcodes, settings, and encrypted intruder photos never leave your device.

⚡ ZERO-LATENCY OVERLAYS
Engineered with native Jetpack Compose and optimized for 120Hz Smooth Displays. Protected apps are locked instantly upon launch without stutter or delay.

🛡️ BANKING & UPI FRIENDLY
Unlike traditional app lockers that rely on intrusive Accessibility Services—often triggering fraud security warnings in banking and payment apps (Google Pay, PhonePe, Paytm)—Pixel Guard uses modern, sanctioned Android UsageStats detection.

📸 SILENT INTRUDER SELFIE
Catch anyone trying to snoop on your device. Pixel Guard takes a silent front-camera photo after failed unlock attempts. All photos are hardware-encrypted with AES-256-GCM using Google Titan M2 KeyStore security.

🔔 NOTIFICATION PRIVACY SHIELD
Prevent private chat previews and sensitive notifications from appearing on your lock screen or status bar for locked apps.

✨ MATERIAL YOU DYNAMIC THEMING
Seamlessly matches your Pixel wallpaper and system accent colors across Light and Dark themes with full high-contrast accessibility compliance.

KEY FEATURES:
• 6-digit master passcode with PBKDF2 (200,000 rounds) key stretching
• Class 3 Strong Biometric unlock (Fingerprint & Face)
• Configurable grace period (immediately, 30s, 1m, 5m, or until screen off)
• Night lockdown mode for enhanced overnight security
• Anti-shoulder surfing randomized numeric keypad
• 16-character master recovery key
• Quick Settings tile toggle
• Material 3 expressive design
```
