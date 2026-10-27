# Testing Strategy

## Testing Philosophy
Pixel Guard enforces a pragmatic, high-assurance testing strategy. Because it is a system-level security utility directly interacting with Android OS primitives (Accessibility, Biometrics, SurfaceFlinger, Camera2), testing emphasizes **deterministic unit testing** for business logic, cryptography, and timing, combined with **real-device ADB smoke testing** on actual hardware (Google Pixel 9).

## Test Stack
- **Unit & Compose UI Testing Framework**: JUnit 4 with Android Gradle Plugin test runner (`unitTests.isIncludeAndroidResources = true`)
- **Compose UI Test Framework**: `androidx.compose.ui:ui-test-junit4` with `androidx.compose.ui:ui-test-manifest` (debug)
- **Fast Local JVM Test Runner**: Robolectric (`4.14.1`) configured with Pixel 9 target geometry (`@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")`).
- **Coroutines & Flow Testing**:
  - `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0`: `StandardTestDispatcher`, `runTest`, `advanceUntilIdle`, and `MainDispatcherRule` (swaps `Dispatchers.Main`).
  - `app.cash.turbine:turbine:1.2.0`: Flow assertion library for deterministic emission testing of reactive `StateFlow` and `SharedFlow`.
- **Fluent Assertions**: Google Truth (`com.google.truth:truth:1.4.4`) for expressive, readable assertion failures matching modern AndroidX standards.
- **Mocking & Test Doubles**:
  - `FakeAppLockRepository`: In-memory clean architecture test double for zero-latency, deterministic ViewModel and UI flow verification without DataStore disk IO.
  - `io.mockk:mockk:1.13.13`: Kotlin-first mocking for Android platform components and context services.
- **Device Hardware Testing**: Android Debug Bridge (ADB) via PowerShell on Google Pixel 9 (Android 17, 120Hz display).

## Test Suite Structure (164 Total Tests, 100% Passing)

### 1. Presentation & Compose UI Tests (58 tests)
All UI tests run via Compose UI Rule + Robolectric on the JVM with exact Pixel 9 display metrics:
- **`MainScreenUiTest`** (19 tests):
  - TopAppBar header, Titan M2 armor branding, and Intruder selfie count badge.
  - Permission Banner conditionally displayed when accessibility/overlay is missing, and tap triggers navigation.
  - Permission Banner hidden when all permissions are granted.
  - Search query filtering, live text input, and clear search button recovery.
  - App category filtering (`Banking & Finance`, `Social & Messaging`, `Sensitive & Recommended`).
  - Contextual category batch action strip (`Lock All` in category).
  - Contextual category batch action strip (`Unlock All` in category).
  - "Locked Only" toggle filter and contextual empty state verification.
  - "Customized" toggle filter chip and customized count badge.
  - Custom tag chip `#Finance (1)` filtering and app visibility.
  - Contextual empty state recovery button ("Clear search" / "Reset filters").
  - Lock icon toggle interaction flipping locked state.
  - Hero Security Health Gauge batch action (`Lock Critical`).
  - Hero Security Health Gauge batch action (`Unlock All`).
  - Sort dropdown assist chip selecting `Locked First` sort order.
  - AppSecurityProfileSheet bottom sheet opening when app row is clicked.
  - AppSecurityProfileSheetContent selects grace period duration (e.g. 30 seconds).
  - AppSecurityProfileSheetContent displays Category & Tags section, interactive category selector capsule, and custom tags.
- **`LockOverlayScreenUiTest`** (10 tests):
  - Target application name, icon, and "Secured by Pixel Guard" shield badge.
  - 6-digit PIN keypad input and automatic submission on 6th digit (`MASTER_PIN_LENGTH = 6`).
  - Backspace button semantics and digit removal prior to submission.
  - Pre-lockout warning banner ("2 attempts remaining before 30s lockout").
  - Lockout countdown banner and disabled keypad state.
  - "Exit to Home" callback invocation.
  - Decoy crash dialog mode ("Pixel Bank keeps stopping"), "Send feedback", and "Close app" dismissal.
  - Biometric fingerprint icon button click triggering `onUnlockSuccess`.
  - Scrambled PIN keypad mode rendering all 10 digits (0-9) and re-scrambling per attempt.
- **`OnboardingScreenUiTest`** (11 tests):
  - Initial permission cards with "Required" and "Optional" badges.
  - Step 2 Master Passcode card appearance once required permissions are granted.
  - Submit button disabled state until both passcode fields have 6 digits.
  - Passcode mismatch validation ("Passcodes do not match").
  - Weak PIN rejection ("Passcode is too simple. Avoid repetitive or sequential digits.").
  - Successful Master Passcode setup, DataStore persistence, and 16-character recovery key modal presentation.
  - Recovery key modal dialog displaying formatted `XXXX-XXXX-XXXX-XXXX`, copy button, and completion on confirmation.
  - Optional Notification Privacy Shield "Later" skip button behavior.
  - Notification permission launcher trigger for Android 13+ `POST_NOTIFICATIONS`.
  - Bottom action button enabled state and permission settings trigger capability.
  - Soft keyboard insets handling with `imePadding` and Step 2 auto-scroll visibility.
- **`SettingsScreenUiTest`** (10 tests):
  - Screen title, Hardware-backed Titan M2 badge, and Biometrics toggle.
  - Dynamic Security Status Hero banner ("Hardware Armor Active").
  - Security Vulnerability Attention banner when settings protection or recents masking is disabled.
  - TopAppBar Back navigation callback.
  - Change Master Passcode row click opens bottom sheet.
  - ChangePinSheetContent mismatch validation and save new 6-digit passcode.
  - GlobalGracePeriodSheetContent selection of global grace period (e.g. 1 minute).
  - ScheduleSheetContent configuration of Night Lockdown hours (e.g. 10:00 PM – 6:00 AM).
  - QuickTilePauseSheetContent selection of quick settings pause duration (e.g. 30 minutes).
  - SecurityPrivacyFaqCard accordion rendering, header, badge, questions, initial collapsed state, expand click revealing answer, and collapse click hiding answer.
- **`IntruderLogsScreenUiTest`** (6 tests):
  - "No Break-In Attempts" empty state card when zero logs exist.
  - Intruder record card rendering (attempted app name, timestamp, failure count).
  - Single record deletion from logs list.
  - Batch "Clear All" confirmation dialog and cancellation.
  - Batch "Clear All" confirmation dialog and record clearance.
  - Photo preview modal dialog opening and closing with pinch-to-zoom evidence viewer.
  - Back navigation callback.
- **`DualThemeContrastTest`** (2 tests):
  - Verification of WCAG 2.1 AA/AAA contrast ratios across light and dark theme surfaces.

### 2. Unit, Domain, Repository & Service Tests (106 tests)
Unit and service tests validate cryptography, rate limiting, background interception resilience, and data persistence:
- **`CryptoManagerTest`** (15 tests):
  - PBKDF2/SHA-256 (200k iterations) hashing and constant-time verification.
  - Strict `Locale.ROOT` byte-array-to-hex and hex-to-byte-array transformations (H-2).
  - Weak-PIN detection rejecting repeating digits ("000000", "111111"), sequential digits ("123456", "654321"), repeating 2/3-digit sequences, and common blocklist PINs (H-1).
  - 16-character unambiguous base32 recovery key generation and normalization (H-3).
  - Authenticated storage envelope encryption and decryption with Associated Authenticated Data (AAD: `pin_hash`, `pin_salt`, `recovery_hash`, `recovery_salt`) (M-10).
  - Rejection of unauthenticated or tampered ciphertext envelopes.
- **`AppLockRepositoryTest`** (9 tests):
  - 6-digit PIN persistence returning formatted 16-character recovery key.
  - Single-snapshot atomic reads for PIN verification preventing TOCTOU race conditions (M-2).
  - Fail-closed handling and `VerifyPinResult.Unrecoverable` return on corrupted ciphertext.
  - Rate-limited verification returning attempts remaining and progressive lockout countdowns (H-1).
  - Recovery key validation and offline credential reset (`resetWithRecoveryCode`).
- **`SessionManagerTest`** (28 tests):
  - Progressive rate-limiting lockout penalties (30s, 60s, 300s, 900s).
  - Clock tampering defense (`syncPersistedLockout`): clamps forward clock jumps to 300s max penalty and preserves remaining duration on backward roll (M-11).
  - Grace period math (immediate 15s quick-switch buffer, screen-off relock, per-app overrides).
  - Night lockdown schedule enforcement and pin-only mode override.
- **`AppLockAccessibilityServiceTest`** (15 tests):
  - Synchronous disk snapshot loading via `SecuritySnapshotManager` (C-2).
  - `SupervisorJob()` and `.catch {}` coroutine resilience preventing permanent collector cancellation (C-2).
  - Fail-closed interception: blocks non-system packages when caches are unready.
  - Whitelist immunity for launcher (`com.google.android.apps.nexuslauncher`), recents, and transient system UI.
  - Screen-off broadcast receiver session revocation.
- **`SessionManagerTest`** (27 tests): Grace period math, Night Lockdown scheduling boundaries, per-app priority, flicker buffer protection, active foreground session retention during continuous in-app usage, exit-based grace countdown, absolute screen-off all-session revocation, 15s quick-switch buffer, and "Until Screen Off" persistence.
- **`LockPreferencesTest`** (13 tests): Default value invariants (`isSettingsProtected == false`, `isRecentsMaskingEnabled == false`, `isBiometricsEnabled == true`, etc.), `AppSortOrder` and `LockType` enum completeness, mutation copy isolation, JSON-based PIN-only and notification mask evaluations, category overrides parsing, custom user & app tag serialization, and `isCustomized()` policy checks.
- **`MainViewModelTest`** (21 tests): Reactive state flow combining, dual search query matching (`appName` and `packageName`), category filtering and dynamic category override, custom tag lifecycle & batch actions, customized filter, sorting, batch actions, PIN configuration, grace period serialization.
- **`DualThemeContrastTest`** (8 tests): Mathematical WCAG 2.1 AA/AAA contrast ratios and theme rendering across screens in Light and Dark modes.

## Integration Testing
- **DataStore Integration**: Validates reading and writing to in-memory `DataStore` preferences without deadlocks.
- **Icon Pre-Rasterization**: Tests that `AppLockRepositoryImpl` handles missing or invalid application drawables without throwing unhandled exceptions.

## End-to-End Testing
- Performed on target hardware (connected Pixel 9) via ADB test scripts:
  - **App Interception Test**: Launch locked package (`adb shell am start -n com.whatsapp/.Main`) ➔ Verify `LockActivity` is top-most window within <16ms.
  - **Biometric Prompt Test**: Confirm `BiometricPrompt` displays automatically on app launch.
  - **Keypad Fallback Test**: Cancel biometrics ➔ Verify custom PIN keypad is responsive.
  - **120Hz Scroll Benchmark**: Reset `gfxinfo` ➔ Inject swipe gesture ➔ Verify `0 Janky frames` at 120Hz.

## API Testing
- Not applicable: Pixel Guard contains no external HTTP/REST APIs. All interfaces are local Android OS IPC services.

## Database Testing
- Preferences DataStore testing using temporary test directories (`testScope` and `DataStoreFactory`).

## Mocking Strategy
- Mock `Context`, `PackageManager`, and `ApplicationInfo` in unit tests using MockK.
- Avoid mocking `SessionManager` or `CryptoManager` in their own test suites; test the real implementations with controlled inputs.

## Test Data
- Test packages: `com.example.bank`, `com.example.social`, `com.example.system`.
- Master PIN inputs: Valid 4-digit numeric strings (`"1234"`, `"8942"`), boundary values, and invalid characters.

## Test Naming Conventions
Follow the `given_when_then` or `method_condition_expectedResult` naming pattern:
```kotlin
@Test
fun hashPin_withDifferentSalts_producesDifferentHashes()

@Test
fun isTemporarilyUnlocked_whenGracePeriodExpired_returnsFalse()

@Test
fun isNightLockdownActive_duringActiveNightHours_returnsTrue()
```

## Coverage Requirements
- **100% Coverage**: `CryptoManager` (security hashing and salt validation).
- **90%+ Coverage**: `SessionManager` (grace periods, night lockdown windows).
- **80%+ Coverage**: `MainViewModel` business logic and state flow combinations.

## Test Performance & Execution Profiles
To maintain an instant developer feedback loop, Pixel Guard provides two testing profiles:

| Command | Profile | Scope | Typical Test Time |
| :--- | :--- | :--- | :--- |
| `.\gradlew.bat testFast` | **Fast Unit Mode** | Runs pure unit, domain, repository, and ViewModel tests (82 tests). Automatically excludes heavyweight Compose UI tests. | **~15s** (parallelized) |
| `.\gradlew.bat testDebugUnitTest` | **Full Assurance Suite** | Runs all 143 unit and Robolectric Compose UI tests across all screens and dual themes. | **~44s** (parallelized) |
| `.\gradlew.bat testDebugUnitTest --tests "ClassName"` | **Targeted Class** | Runs a single test class (e.g. `SessionManagerTest`). | **<3s** |

### Execution Optimizations
- **Multi-Core Test Parallelization**: Configured with `maxParallelForks = (availableProcessors / 2)` in `app/build.gradle.kts` to execute test suites concurrently.
- **Worker Heap Sizing**: Forked test JVMs are sized with `-Xmx1024m` and `-XX:+UseParallelGC` to eliminate memory paging on 8GB host machines.
- **Daemon Memory Tuning**: `gradle.properties` is configured with `org.gradle.jvmargs=-Xmx2048m` with `org.gradle.daemon=true` to prevent OS memory exhaustion.

## Required Checks
Before marking any task complete or committing code:
1. **Compilation Check**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   Must finish with `BUILD SUCCESSFUL` (exit code 0).
2. **Unit Test Pass**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   Must pass with 0 failures.
3. **Hardware Deployment & Verification**:
   ```powershell
   adb install -r -d "app\build\outputs\apk\release\app-release.apk"
   ```
   Verify launch, biometric prompt, and 120Hz scrolling on device.

## CI Testing
When setting up automated GitHub Actions / GitLab CI:
- Use JDK 17 (`actions/setup-java@v4`).
- Cache Gradle dependencies (`~/.gradle/caches`).
- Run `./gradlew testDebugUnitTest assembleDebug`.

## Definition of Done
A feature or bugfix is considered **Done** only when:
- [x] Code strictly adheres to [RULES.md](file:///a:/Development/app-lock/docs/RULES.md) and [DESIGN.md](file:///a:/Development/app-lock/docs/DESIGN.md).
- [x] No UI-thread disk, bitmap, or package queries exist.
- [x] All unit tests pass.
- [x] Verified on target Google Pixel 9 hardware via ADB.
- [x] Documentation in `MEMORY.md` and `PRD.md` updated if requirements or state changed.
