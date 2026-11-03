# Product Requirements Document

## Product Overview
**Pixel Guard** is a specialized, hardware-integrated native Android security utility engineered specifically for Google Pixel devices running **Android 17 and above** (targeting the Google Pixel 9 series). It delivers zero-latency application interception, hardware-backed biometric authentication (ultrasonic fingerprint & Titan M2 Class 3 Face Unlock), 120Hz smooth display optimization, and anti-snoop privacy safeguards without relying on intrusive continuous background polling.

## Target Users
- **Pixel 9 Power Users**: Owners of Google Pixel 9 / Pixel 9 Pro / Pixel 9 Fold seeking maximum privacy for banking, social, messaging, and system apps.
- **Privacy-Conscious Individuals**: Users who share their unlocked phone with family, friends, or colleagues but want strict perimeter isolation for sensitive apps.
- **Direct Sideload Community**: Advanced Android users leveraging direct USB/sideload installation to bypass Google Play Store restrictions on Accessibility-based security tools.

## Problem Statement
Standard third-party Android app lockers suffer from four fatal flaws on modern Android flagships:
1. **High Latency & Jank**: UsageStats polling creates a 200ms–800ms window where private app content flashes on screen before being covered, accompanied by frame drops on 120Hz LTPO displays.
2. **Weak Authentication Isolation**: They frequently fall back to the phone's lock screen passcode, meaning anyone who knows the device unlock PIN can immediately access all locked apps.
3. **Severe Android 17 Restrictions**: Android 13–17 automatically disables Accessibility Services for sideloaded APKs under "Restricted settings", confusing users during onboarding.
4. **Recents & Notification Leaks**: Even when locked, app overview snapshots (Recents screen) and heads-up notifications routinely reveal private chats and bank balances.

## Goals
- **Safe & Instant Interception**: Intercept target app launches within milliseconds via `UsageStatsManager` and foreground service lifecycle monitoring, completely eliminating Google Pay and UPI banking security blocks.
- **Hardware-Enforced Security**: Authenticate via Pixel 9's Titan M2 security chip and ultrasonic fingerprint reader, backed by an isolated PBKDF2/SHA-256 salted PIN that is completely distinct from the device lockscreen PIN.
- **Locked 120Hz Rendering**: Maintain a continuous 120Hz refresh rate budget (<8.33ms per frame) during list navigation with asynchronous IO icon pre-rasterization.
- **Ambient Privacy Shielding**: Block screen captures in Recents via `FLAG_SECURE`, suppress notification previews from locked apps, and silently log intrusion attempts with front-camera capture.

## Non-Goals
- **Universal Android 8–10 Legacy Support**: Not designed for outdated Android versions lacking modern Material 3, Camera2 headless capabilities, or modern permission frameworks.
- **Accessibility Service Usage**: Will not declare or bind to `AccessibilityService`, preventing Android banking/UPI trojan heuristic blocks.
- **Remote Cloud Sync & Account Tracking**: Zero cloud servers, telemetry, or accounts; all cryptographic keys and audit logs remain strictly sandboxed on the local device.

## Core Features (MVP)
1. **Banking-Safe Foreground Interception**: Adaptive background polling loop with `UsageStatsManager` (`PACKAGE_USAGE_STATS`) and `SYSTEM_ALERT_WINDOW`.
2. **Hardware Biometrics & Dual-Layer PIN**: Seamless Pixel 9 ultrasonic fingerprint / Face Unlock auto-prompt on app launch with graceful fallback to a 6-digit master PIN.
3. **120Hz Smooth Display Pipeline**: Pre-rasterized 120x120 ARGB_8888 icons on `Dispatchers.IO`, Compose stability annotations (`@Immutable`), and LTPO variable refresh rate.
4. **App-Themed Material You Theming**: Radial ambient glow and pulse rings dynamically extracting and adapting to the locked app's signature colors (WhatsApp green, YouTube ruby, Instagram magenta, etc.).
5. **Contextual Category Batch Controls**: 1-tap "Lock All" / "Unlock All" buttons appearing upon category filter selection (Financial, Social, Sensitive, System).
6. **Per-App Grace Periods & Night Lockdown**: Granular re-lock timing per application (Instant, 30s, 1m, 5m, 15m, Until screen off) and automated strict 0s lockdown during nighttime hours.
7. **Recents Privacy Masking (`FLAG_SECURE`)**: Prevents task snapshots from appearing in Android Recents overview and blocks screenshots.
8. **Decoy "Fake Crash" Mode**: Authentic *"Unfortunately, [App] keeps stopping"* system dialog with secret long-press bypass to fool snoopers.
9. **Headless Intruder Selfie**: Captures front-camera photo via Camera2 after configurable failed PIN attempts, stored in encrypted internal app storage.
10. **Notification Privacy Shield**: Intercepts and masks sensitive notification previews from protected packages.
11. **Modern Sideload Onboarding**: Interactive permission setup clearly demarcating 2 Required permissions (App Detection / Usage Access, Display Over Other Apps) and 1 Optional (Notification Shield).

## User Flows
- **Initial Setup**: Install APK ➔ Launch Pixel Guard ➔ Review Required vs Optional permissions ➔ Enable App Detection (Usage Access) & Display Over Other Apps ➔ Set 6-digit Master PIN ➔ Main Dashboard unlocked.
- **Protecting Apps**: Open Dashboard ➔ Tap category chip (e.g., Financial) ➔ Tap "Lock All" on the Category Action Strip ➔ Status indicator updates to protected.
- **Unlocking an App**: Tap WhatsApp on Home screen ➔ Pixel Guard intercept activates ➔ Biometric prompt appears automatically ➔ Touch ultrasonic fingerprint sensor ➔ Overlay dismisses smoothly into WhatsApp.
- **Fallback PIN Entry**: Biometrics fail or user taps "Use PIN" ➔ User enters custom 4-digit PIN on glassmorphic keypad ➔ App unlocks.
- **Intruder Detection**: Snooper enters wrong PIN 3 times ➔ Front camera silently takes photo without flash ➔ Photo logged in Intruder Records with timestamp.

## Future Features (Roadmap)
- **Stealth Disguise Mode (Calculator Cloak)**: Disguise Pixel Guard launcher icon and name as a functional Calculator with secret passcode calculation entry.
- **Trusted Wi-Fi Geofence**: Automatically pause protection when connected to verified Home Wi-Fi network.
- **Encrypted Audit Log Export**: Export intruder selfies and security events as an AES-256 encrypted archive.

## Technical Constraints
- Minimum SDK: Android 8.0 (API 26). Target & Compile SDK: Android 15 / 16 / 17 (API 35+).
- Hardware Requirement: Android device with Biometric BiometricManager.Authenticators.BIOMETRIC_STRONG (optimized for Pixel 9 series).
- Permissions Required: `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`, `CAMERA`, `POST_NOTIFICATIONS`, `BIND_NOTIFICATION_LISTENER_SERVICE` (optional).

## Success Metrics
- **Frame Render Budget**: 0 dropped frames during 120Hz fling scroll (<8.33ms per frame).
- **Interception Latency**: <16ms from app tap to lock overlay presentation.
- **False-Positive Unlock Rate**: 0% credential leakage to system device password.
- **Crash Rate**: 0% unhandled ANRs or exceptions across continuous session switches.

## Acceptance Criteria
- [x] All 6 categories can be filtered and batch-locked in 1 tap.
- [x] Zero-tap biometric prompt fires automatically upon opening any locked app.
- [x] Cancelling biometrics leaves the custom PIN keypad active without closing the app.
- [x] Device lockscreen passcode cannot unlock Pixel Guard (only Master PIN or Biometrics).
- [x] Setup permission screen clearly marks Overlay/Accessibility as Required and Notification as Optional.
- [x] Main app list scrolls smoothly at 120Hz on Google Pixel 9 display.
