# Project Rules

## General Rules
- Always preserve existing comments and documentation unrelated to code modifications.
- Keep all code changes strictly within the user workspace (`a:/Development/app-lock/`).
- Never perform main-thread blocking operations (disk IO, bitmap decoding, package queries).

## Code Organization
- Place code strictly in its designated architectural layer (`data/`, `domain/`, `service/`, `ui/`).
- Do not mix Compose UI code with background Service logic.
- Keep helper utilities and managers scoped and testable.

## Naming Conventions
- **Composables**: UpperCamelCase nouns or adjective-nouns (e.g. `MainScreen`, `PermissionCard`, `SecurityHealthGaugeCard`).
- **Data Classes & Models**: UpperCamelCase (e.g. `AppItem`, `LockPreferences`, `IntruderRecord`).
- **Functions & Methods**: lowerCamelCase verbs (e.g. `verifyPin()`, `enableHighRefreshRate()`, `toggleAppLock()`).
- **Constants**: SCREAMING_SNAKE_CASE in companion objects (e.g. `EXTRA_TARGET_PACKAGE`, `KEY_PIN_HASH`).

## Type Safety
- Annotate all data classes consumed by Compose `LazyColumn` items with `@Immutable`.
- Prefer strict Kotlin types and enums (`AppCategory`, `LockType`) over raw strings or integer flags.
- Avoid nullable platform types; provide safe defaults or fallbacks when querying Android system APIs.

## Error Handling
- Never crash the host application if an optional subsystem fails (e.g., front camera hardware failure or missing biometric sensor).
- Wrap all biometric, camera, and window manager invocations in guarded `try / catch` blocks with informative Android Logcat logging.
- Fall back gracefully to the custom Master PIN keypad whenever biometrics are unavailable.

## API Rules
- Use `androidx.biometric:biometric` targeting `BiometricManager.Authenticators.BIOMETRIC_STRONG`.
- Always verify `canAuthenticate()` before invoking `BiometricPrompt.authenticate()`.
- Use `setNegativeButtonText("Use PIN")` to avoid exposing the device screen passcode.

## Database Rules
- Store all user preferences in Jetpack DataStore; never use legacy `SharedPreferences`.
- Never store plaintext PINs or unencrypted hashes. Always generate a cryptographically random 16-byte salt, compute the stretched PBKDF2WithHmacSHA256 (200,000 iterations) hash, zero memory buffers with `fill(0)`, and envelope-encrypt both salt and hash via Titan M2 Keystore AES-256-GCM before writing to DataStore.
- Never hardcode default fallback PIN strings; enforce explicit configuration via onboarding.
- Persist global lockouts using wall-clock target timestamps and remaining duration, enforcing a 300-second sanity bound against clock-skew upon device reboot.

## Security Rules
- Apply `FLAG_SECURE` to windows whenever `isRecentsMaskingEnabled` is active.
- Save intruder photos strictly in internal sandboxed storage (`context.filesDir/intruders`) encrypted with Titan M2 Keystore keys. Never write intruder photos to `Environment.getExternalStorageDirectory()` or Android `MediaStore`.
- Do not expose any network permissions (`android.permission.INTERNET`) in `AndroidManifest.xml`.
- Do not declare or request `SYSTEM_ALERT_WINDOW` permission. App locking is managed exclusively via hardware-accelerated `LockActivity` with `resizeableActivity="false"` and multi-window evasion prevention.
- All credential changes (e.g. Master PIN updates in Settings) must be rate-limited through `SessionManager` and trigger intruder photo capture on repeated failures.
- Front camera capture must include a 4000ms watchdog timer to prevent Camera2 service lockups.
- **6-Digit Passcode & Weak PIN Defense**: Master PINs must be strictly 6 digits (`MASTER_PIN_LENGTH = 6`). Reject repeating digits, sequential numbers, and blocklisted trivial combinations via `CryptoManager.isWeakPin()`.
- **16-Character Offline Recovery Key**: Always generate a 16-character base32 recovery key (`XXXX-XXXX-XXXX-XXXX`) during PIN setup and store its PBKDF2 hash for verified offline credential recovery.
- **Fail-Closed Interception**: `AppLockAccessibilityService` must load `SecuritySnapshotManager` synchronously on creation and block non-system packages if caches are unready. Collectors must be enclosed in `SupervisorJob()` and protected with `.catch {}`.
- **Non-Destructive Notification Delivery**: Never cancel an original notification if `NotificationManagerCompat.areNotificationsEnabled()` is false or the masked channel is blocked. Fails closed toward delivery, never notification loss.
- **Contextual Permission Requests**: Request `POST_NOTIFICATIONS` during onboarding; request `CAMERA` strictly on-demand when Intruder Selfie is toggled on in Settings. Default `isIntruderSelfieEnabled` to `false`.
- **Locale-Independent Formatting**: Always use `Locale.ROOT` for hex encoding, decoding, and string case transformations to eliminate character conversion bugs.
- **Release Build Log Sanitization**: All log statements must be guarded with `if (BuildConfig.DEBUG)` to prevent sensitive telemetry or exception leakage in production.

## Dependency Rules
- Declare all dependencies in `gradle/libs.versions.toml`.
- Do not add third-party networking, analytics, ad networks, or heavy UI frameworks without explicit approval.

## UI Rules
- Build all UI using **Jetpack Compose with Material 3** tokens.
- Maintain touch targets of at least **48dp x 48dp** for all interactive components.
- Support 120Hz LTPO display timing (<8.33ms per frame) during list rendering and scrolling.

## State Management
- Expose state from ViewModels using `StateFlow<T>` via `.asStateFlow()`.
- Never mutate state directly from composables; emit intents/callbacks to the ViewModel.
- Coordinate in-memory session unlock state strictly through `SessionManager`.

## Testing Rules
- Test cryptographic hashing, salt generation, and verification logic with unit tests.
- Verify grace period expiration and night lockdown calculations with deterministic timestamp tests.
- Always perform smoke verification on target hardware (connected Pixel 9) via ADB.

## Documentation Rules
- **Mandatory Documentation Updates**: Always immediately update relevant documentation files (`docs/DESIGN.md`, `docs/DECISIONS.md`, `docs/MEMORY.md`, `docs/TESTING.md`, etc.) in the SAME step whenever any code, UI, architectural, or configuration changes are made.
- **Architectural Decision Records (ADRs)**: Every non-trivial architectural, design, or testing decision must be promptly appended to `docs/DECISIONS.md`.
- **Session Memory Tracking**: Always update `docs/MEMORY.md` with active status, recently completed tasks, and current session handoff notes.

## Git & Workspace Rules
- Do not commit generated build outputs (`build/`, `.gradle/`, `*.apk`, `local.properties`).
- Maintain `.antigravityignore` at root to prevent build artifacts, bytecode (`*.dex`, `*.class`), caches, and temporary debug captures from consuming AI context and degrading semantic search.
- **Immediate Ephemeral Cleanup**: Always delete temporary files and folders (adb screencaps `*.png`, UI hierarchy dumps `ui.xml`, scratch logs, or test dumps) immediately once their work is completed. Never leave ephemeral artifacts in the repository.
- Keep commit messages concise, descriptive, and prefixed with the affected component (e.g., `feat(ui):`, `fix(perf):`).

## Performance Rules
- **Icon Pre-Rasterization**: App icons must be pre-converted into 120x120 Bitmaps on `Dispatchers.IO` in background coroutines.
- **O(1) In-Memory Lookups**: Parse JSON strings outside Lazy lists using `remember`; do not parse JSON inside row composables.
- **Lazy Recycling**: Provide `key` and `contentType` parameters in all `LazyColumn` implementations.
- **Memoization**: Memoize complex counts or filtered sub-lists with `remember(allApps)` to avoid repeated list iterations during scroll.

## Deployment & Build Rules
- **Always Install Production (Release) Build**: Always compile and install the release build (`assembleRelease` -> `app-release.apk`) to target devices. Never deploy debug builds unless explicitly instructed by the user.

## Forbidden Practices
- **DO NOT** deploy debug builds (`app-debug.apk`) to physical devices; always build and deploy production release builds (`app-release.apk`).
- **DO NOT** leave temporary debug files, screencaps, or UI dumps in the workspace after work is completed.
- **DO NOT** use `DEVICE_CREDENTIAL` in `BiometricPrompt.PromptInfo`.
- **DO NOT** execute `app.icon.toBitmap()` synchronously inside `@Composable` functions.
- **DO NOT** replace `AccessibilityService` with periodic `UsageStatsManager` polling loops.
- **DO NOT** request or add `android.permission.INTERNET` to the project manifest.
- **DO NOT** hardcode non-theme colors for layout backgrounds or typography.
- **DO NOT** hardcode default or fallback PIN strings (e.g. "1234") — enforce user onboarding setup.
- **DO NOT** emit raw package names or sensitive telemetry in `Log.i/d` without `BuildConfig.DEBUG` guards.
- **DO NOT** suggest, propose, or build calculator launcher icon disguises, fake calculator cloaks, or hidden passcode calculations. This constraint is permanent across all versions.

