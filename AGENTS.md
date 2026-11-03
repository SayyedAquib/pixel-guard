# AGENTS.md

## Project Overview
Pixel Guard is a native Android application locker developed in Kotlin and Jetpack Compose, specifically optimized for Google Pixel 9 running Android 17+. It provides zero-latency app interception via `AccessibilityService`, hardware-accelerated Compose overlays (`TYPE_APPLICATION_OVERLAY` & `LockActivity`), Titan M2 biometric integration (`BiometricPrompt`), and Material 3 / Material You dynamic theming.

## Architecture Overview
The project follows **Clean Architecture** and **Unidirectional Data Flow (UDF)**:
- **UI Layer (`ui/`)**: Pure Jetpack Compose with Material 3. Screens observe `StateFlow` from `MainViewModel`.
- **Domain Layer (`domain/`)**: In-memory state coordinators and business rules (`SessionManager`).
- **Data Layer (`data/`)**: Repository pattern (`AppLockRepositoryImpl`), encrypted preferences via Jetpack DataStore (`PreferencesDataStore`), and hardware crypto (`CryptoManager`).
- **Service Layer (`service/`)**: Background Android system components (`AppLockForegroundService`, `NotificationShieldService`, `SilentCameraCapture`, `AppUsageDetector`).

## Repository Structure
```
app-lock/
├── app/
│   ├── build.gradle.kts           # App-level build config, dependencies, target API 35
│   └── src/main/
│       ├── AndroidManifest.xml    # Permissions, services, receivers, activities
│       ├── java/com/pixelguard/applock/
│       │   ├── AppLockApplication.kt
│       │   ├── data/              # Models, DataStore, Keystore Crypto, Repositories
│       │   ├── domain/usecase/    # SessionManager, GracePeriod calculations
│       │   ├── service/           # Accessibility, Overlay, Camera2, Notifications
│       │   └── ui/                # Main, Lock, Settings, Onboarding, Logs, Theme
│       └── res/                   # Drawables, XML metadata, app icons
├── docs/                              # Project governance and specification docs
│   ├── PRD.md                         # Product requirements & user stories
│   ├── DESIGN.md                      # Design system, tokens, and components
│   ├── ARCHITECTURE.md                # In-depth architectural blueprint
│   ├── RULES.md                       # Engineering constraints & rules
│   ├── MEMORY.md                      # Active session state & history
│   ├── DECISIONS.md                   # Architecture decision records (ADRs)
│   └── TESTING.md                     # QA strategy & test requirements
├── gradle/libs.versions.toml          # Dependency catalog
├── gradlew.bat                        # Gradle wrapper script (Windows)
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Project settings & repositories
├── CLAUDE.md                          # Claude agent entry point
├── README.md                          # Project documentation
└── AGENTS.md                          # Core AI agent instructions and conventions
```

## Development Commands
All terminal commands run in PowerShell on Windows. Environment variables must be set prior to Gradle invocation:
```powershell
# Set Java and Android SDK environment variables
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# (Optional) Reconnect wirelessly to Pixel 9 if disconnected
adb connect <device-ip>:<port>

# Deploy to connected Google Pixel 9 via ADB (USB or Wireless)
adb install -r -d "app\build\outputs\apk\release\app-release.apk"

# Restart application on device
adb shell am force-stop com.pixelguard.applock
adb shell am start -n com.pixelguard.applock/.ui.main.MainActivity
```

## Build Commands
```powershell
# Clean build
.\gradlew.bat clean

# Compile debug APK
.\gradlew.bat assembleDebug

# Compile production release APK (ALWAYS used for device deployment)
.\gradlew.bat assembleRelease

# Check lint and syntax
.\gradlew.bat lintDebug
```

## Testing Commands
```powershell
# Run all unit and Robolectric UI tests (parallelized across CPU cores)
.\gradlew.bat testDebugUnitTest

# Run fast unit tests only (<15s, excludes slow Robolectric Compose UI tests)
.\gradlew.bat testFast

# Run a specific test class instantly
.\gradlew.bat testDebugUnitTest --tests "SessionManagerTest"

# Run connected Android instrumentation tests (requires device/emulator)
.\gradlew.bat connectedDebugAndroidTest
```

## Code Conventions
- **Language**: Kotlin 2.0.21 with Compose compiler plugin.
- **Compose Stability**: All data classes passed into `LazyColumn` items must be annotated with `@Immutable` (e.g. `AppItem`).
- **Compose Keys & Content Types**: Always provide unique `key = { ... }` and `contentType = { ... }` in Lazy layouts to maximize viewpool recycling.
- **Asynchronous IO**: Heavy operations (package queries, icon rasterization, JSON parsing, disk IO) must run on `Dispatchers.IO`. Never perform `toBitmap()` synchronously inside `@Composable` items.
- **Naming**: UpperCamelCase for Composables and Classes, lowerCamelCase for functions, variables, and properties. Constants in SCREAMING_SNAKE_CASE.

## Architecture Conventions
- ViewModels must not directly access Android system services (e.g. `WindowManager`, `CameraManager`). Delegate to Repositories or Services.
- UI state must be exposed as immutable `StateFlow<T>` or Compose `State<T>`. Never expose mutable states directly to Composable views.
- Temporary unlock sessions are coordinated exclusively through `SessionManager`.

## Database Conventions
- Key-value preferences are stored in Jetpack DataStore (`preferencesDataStore`).
- PINs are stored as hex-encoded salted hashes (`CryptoManager.hashPin`). Plaintext PINs must never touch storage.
- Intruder photo records are stored as JSON/DataStore entries pointing to sandboxed files in `context.filesDir/intruders`.

## API Conventions
- Android System APIs: Target API 35 (Android 15), compatible with Android 17 preview features.
- Hardware Biometrics: Use `androidx.biometric:biometric` targeting `BiometricManager.Authenticators.BIOMETRIC_STRONG`. Always check `canAuthenticate()` before `authenticate()`.

## Security Requirements
- **StrongBox / Titan M2**: Cryptographic keys are generated inside `AndroidKeyStore`.
- **Screenshot Protection**: Apply `FLAG_SECURE` to windows when `isRecentsMaskingEnabled` is active.
- **Zero Cloud Leakage**: No network sockets, analytics SDKs, or cloud endpoints.

## Dependency Guidelines
- Managed centrally in `gradle/libs.versions.toml`.
- Do not add heavy external libraries without architectural review. Prefer AndroidX and Kotlin standard library implementations.

## Testing Requirements
- Unit tests for `CryptoManager` (PBKDF2/SHA-256 validation) and `SessionManager` (grace period math, night lockdown logic).
- Smoke verification on target hardware (Google Pixel 9 via ADB) for 120Hz display smoothness and biometric trigger reliability.

## Verification Checklist
Before completing any task:
1. Run `.\gradlew.bat assembleRelease` (or `assembleDebug` for fast unit verification) and verify exit code 0.
2. Check that no main-thread disk or bitmap rasterization was introduced into `@Composable` scopes.
3. Deploy and test on the connected Pixel 9 with `adb install -r -d "app\build\outputs\apk\release\app-release.apk"` (ALWAYS install Production/Release build per user mandate).
4. **MANDATORY**: Update relevant documentation files (`docs/DESIGN.md`, `docs/DECISIONS.md`, `docs/MEMORY.md`, `docs/RULES.md`, `docs/TESTING.md`) immediately in the SAME turn whenever any UI, code, architectural, or configuration changes occur.
5. **Ephemeral File Cleanup**: Always remove temporary files and folders once work is completed (e.g. adb screencaps `*.png`, XML UI hierarchy dumps `ui.xml`, scratch logs, or test dumps). Never leave ephemeral files cluttering the repository root.

## Forbidden Changes
- **DO NOT** deploy debug builds (`app-debug.apk`) to physical devices; ALWAYS deploy production release builds (`app-release.apk`).
- **DO NOT** complete turns or tasks without immediately updating project documentation (`docs/DESIGN.md`, `docs/DECISIONS.md`, `docs/MEMORY.md`).
- **DO NOT** leave temporary debug files, screencaps, or UI dumps in the workspace after work is completed.
- **DO NOT** re-introduce `AccessibilityService` or `BIND_ACCESSIBILITY_SERVICE`—this triggers Google Pay, PhonePe, and UPI fraud blocks (use `UsageStatsManager` & `AppLockForegroundService` per ADR 056).
- **DO NOT** use `DEVICE_CREDENTIAL` in `BiometricPrompt.PromptInfo`—this compromises custom Master PIN security.
- **DO NOT** store intruder photos in public `MediaStore` or external shared storage.
- **DO NOT** call synchronous `toBitmap()` inside list item rendering passes.
- **DO NOT** hardcode fallback default PIN strings (e.g. "1234") — enforce user onboarding setup.
- **DO NOT** emit raw package names or sensitive telemetry in `Log.i/d` without `BuildConfig.DEBUG` guards.


## Additional Documentation
- [PRD.md](file:///a:/Development/app-lock/docs/PRD.md) - Product Requirements Document
- [DESIGN.md](file:///a:/Development/app-lock/docs/DESIGN.md) - Design System & UI Specifications
- [ARCHITECTURE.md](file:///a:/Development/app-lock/docs/ARCHITECTURE.md) - Technical Architecture & Data Flow
- [RULES.md](file:///a:/Development/app-lock/docs/RULES.md) - Project Engineering Rules
- [MEMORY.md](file:///a:/Development/app-lock/docs/MEMORY.md) - Session State & History
- [DECISIONS.md](file:///a:/Development/app-lock/docs/DECISIONS.md) - Architecture Decision Records
- [TESTING.md](file:///a:/Development/app-lock/docs/TESTING.md) - Test Strategy & Verification
