# Privacy Policy for Pixel Guard

**Last Updated:** November 17, 2026  
**Effective Date:** November 17, 2026  

Pixel Guard ("we", "our", or "the app") is a native Android application locker developed for Android 15+ (API 35+) and optimized for Google Pixel devices. We take your privacy and device security extremely seriously.

Our core commitment: **Pixel Guard is completely offline. We do not collect, store, transmit, or share any personal data, telemetry, or user analytics with anyone—including us.**

---

## 1. Zero Network Access (No Internet Permission)

Pixel Guard does not declare or request the `android.permission.INTERNET` permission in its Android Manifest. 
- The application has **zero network capabilities**.
- It is technically impossible for the application to transmit data over the internet, make API requests, upload crash reports, or communicate with external servers.
- There are no third-party software development kits (SDKs), advertising networks, or analytics libraries integrated into the app.

---

## 2. On-Device Data & Sensitive Permissions

To provide application locking, intrusion detection, and notification privacy, Pixel Guard requests specific Android system permissions. All data processed via these permissions remains strictly on your device and is protected by hardware-backed cryptography:

### A. App Usage Access (`android.permission.PACKAGE_USAGE_STATS`)
- **Purpose**: Pixel Guard uses Android's `UsageStatsManager` solely to detect when a user launches an application that has been selected for locking.
- **Data Handled**: Foreground package names and transition timestamps.
- **Privacy Assurance**: Pixel Guard only compares the currently active package name against your local list of locked apps. It never logs your application browsing habits, usage durations, or app activities. This data never leaves your device.

### B. Display Over Other Apps (`android.permission.SYSTEM_ALERT_WINDOW`)
- **Purpose**: To present the lock screen overlay immediately above locked applications before private content is exposed.
- **Privacy Assurance**: The overlay only intercepts touch input on the lock screen itself and does not inspect or interact with underlying app screens.

### C. Camera Access (`android.permission.CAMERA` - Optional Intruder Selfie)
- **Purpose**: If enabled in Settings, Pixel Guard captures a silent front-camera photo when someone fails PIN or biometric authentication.
- **Data Handled**: Still image captures from the front camera.
- **Security & Storage**: Intruder photos are encrypted immediately in memory using **AES-256-GCM** with hardware-bound keys generated inside the device's **Titan M2 / AndroidKeyStore** security module.
- **Sandboxing**: Encrypted image bytes are stored strictly inside Pixel Guard's sandboxed private internal storage (`context.filesDir/intruders`). They are **never** stored in public media storage (`MediaStore` or external SD cards) and cannot be accessed by other applications.
- **User Control**: You can review, decrypt on-screen, or permanently delete intruder logs at any time from the Intruder Logs dashboard.

### D. Notification Privacy Shield (`NotificationListenerService` - Optional)
- **Purpose**: To conceal sensitive notification previews and message bodies from locked applications when your device is locked.
- **Data Handled**: Notification metadata (package name, title, and preview text) for incoming alerts from apps you chose to lock.
- **Privacy Assurance**: The service only temporarily cancels or replaces notifications for locked apps while the app remains locked. It does **not** store, log, read, or transmit notification contents.

### E. Biometric Authentication (`android.permission.USE_BIOMETRIC`)
- **Purpose**: Allows you to unlock protected apps using your fingerprint or facial biometric credentials.
- **Security**: Pixel Guard delegates authentication directly to the Android System's `BiometricPrompt` targeting Class 3 (`BIOMETRIC_STRONG`).
- **Privacy Assurance**: Pixel Guard never accesses, captures, or stores your raw biometric data. All biometric matching occurs in the isolated Titan M2 hardware security module.

---

## 3. Storage and Encryption of Passcodes & Master Keys

- **Passcode (PIN)**: Your 6-digit master passcode is hashed locally using **PBKDF2WithHmacSHA256** with **200,000 iterations** and a cryptographically secure 16-byte random salt. Plaintext PINs are never stored on disk.
- **Memory Hygiene**: Passcode character arrays are explicitly zeroed out in memory immediately after hashing to prevent memory inspection attacks.
- **Hardware Keys**: Encryption keys are generated inside the AndroidKeyStore with StrongBox backing (Titan M2 HSM on Google Pixel 9).

---

## 4. Third-Party Services and SDKs

Pixel Guard does **not** include:
- Advertising SDKs (e.g. AdMob, Unity)
- Analytics or tracking tools (e.g. Google Analytics, Firebase Analytics, Mixpanel)
- Crash reporting frameworks that send telemetry off-device (e.g. Crashlytics, Sentry)
- Social media integrations or trackers

All diagnostic logs exported via the optional "Export Diagnostics" feature are presented as raw JSON for your own inspection and are saved only when you explicitly export them.

---

## 5. Children's Privacy

Pixel Guard does not knowingly collect or solicit any personal data from anyone, including children under the age of 13.

---

## 6. Data Retention and Deletion

All Pixel Guard settings, locked application lists, encrypted intruder photos, and salted passcode hashes are stored exclusively in the app's private internal storage.
- You can clear all data at any time via **Android Settings ➔ Apps ➔ Pixel Guard ➔ Clear Storage**.
- Uninstalling Pixel Guard immediately and permanently removes all stored data, encryption keys, and photos from your device.

---

## 7. Changes to This Privacy Policy

If we update this Privacy Policy, the revised version will be reflected in future app updates and published with an updated "Last Updated" date.

---

## 8. Contact

For technical questions or security inquiries regarding Pixel Guard, please open an issue on the official project repository or contact the project maintainers.
