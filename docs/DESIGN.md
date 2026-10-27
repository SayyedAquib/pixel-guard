# Design System

## Design Philosophy
Pixel Guard is designed around **Google Material You (Material 3)** with a deep security and privacy aesthetic. The interface feels native to Google Pixel devices: tactile, ambient, and modern, using dynamic wallpaper extraction, fluid spring physics, glassmorphic elements, and rich haptic micro-interactions.

## Visual Direction
- **Dual-Theme Support**: Native Material You implementation supporting both dynamic and static palettes across Light and Dark system modes.
- **Ambient Darkness & Clean Light**: Deep charcoal tones (`#101418`, `#1B2025`) for dark mode; clean porcelain and tonal blues (`#FFFFFF`, `#F7F9FD`, `#EBF0F8`) for light mode.
- **Privacy Lock Isolation**: Lock overlays (`LockOverlayScreen`, `LockActivity`, `OverlayWindowManager`) are permanently locked to true OLED black (`#000000`) for privacy and battery conservation on Pixel 9 displays.
- **Tactile Depth**: Subtle borders (`1dp outlineVariant`), high corner radiuses (`16dp`–`24dp`), and glassmorphism without heavy drop shadows.

## Colors & Theming Architecture
Defined in [`Color.kt`](file:///a:/Development/app-lock/app/src/main/java/com/pixelguard/applock/ui/theme/Color.kt) and [`Theme.kt`](file:///a:/Development/app-lock/app/src/main/java/com/pixelguard/applock/ui/theme/Theme.kt):

### Material 3 Dark & Light Palette
| Token | Dark Hex | Light Hex | Role |
| :--- | :--- | :--- | :--- |
| `PixelPrimary` | `#A8C7FA` | `#1B6EF3` | Primary brand accent & active states |
| `PixelOnPrimary` | `#062E6F` | `#FFFFFF` | High-contrast text on primary |
| `PixelPrimaryContainer` | `#1B448C` | `#D3E3FD` | Selected card & button backgrounds |
| `PixelOnPrimaryContainer` | `#D3E3FD` | `#041E49` | Text on primary container |
| `PixelBackground` | `#101418` | `#FFFFFF` | Scaffold & screen root background |
| `PixelSurface` | `#1B2025` | `#F7F9FD` | Container surfaces & elevated cards |
| `PixelSurfaceVariant` | `#282F37` | `#EBF0F8` | Keypad discs, input boxes, list items |
| `PixelOnSurface` | `#E1E2E8` | `#1A1C1E` | Primary body text & card titles |
| `PixelOnSurfaceVariant` | `#C3C7CF` | `#43474E` | Subtitles, metadata, timestamps |
| `PixelOutline` | `#8B919A` | `#73777F` | Subtle dividers & unfilled state borders |
| `PixelOutlineVariant` | `#3F4750` | `#C3C7CF` | Inset card borders & item strokes |
| `PixelOledBlack` | `#000000` | `#000000` | True OLED black background for lock overlay |

### Adaptive Semantic Color System (`SemanticColors`)
Alert, warning, and success states dynamically swap tone according to active mode to ensure strict WCAG 2.1 compliance:
| Semantic Token | Dark Hex | Light Hex | Light Background Contrast | Dark Background Contrast | WCAG Rating |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `amber` (Text / Icons) | `#F9AB00` | `#8F4C00` | 6.3:1 (vs `#FFFFFF`) | 8.3:1 (vs `#101418`) | **AA / AAA** |
| `onAmber` | `#261900` | `#FFFFFF` | - | - | - |
| `amberContainer` | `#4D3300` | `#FFDDB8` | 1.3:1 container | 2.1:1 container | Container disc |
| `onAmberContainer` | `#FFDDB8` | `#2E1600` | 13.1:1 (vs container) | 12.8:1 (vs container) | **AAA** |
| `success` | `#7BD88F` | `#1B6D2F` | 5.8:1 (vs `#FFFFFF`) | 9.7:1 (vs `#101418`) | **AA / AAA** |
| `error` | `#FFB4AB` | `#BA1A1A` | 6.0:1 (vs `#FFFFFF`) | 8.9:1 (vs `#101418`) | **AA / AAA** |

### Dynamic Brand Accent Luminance Formula
In `LockOverlayScreen`, target app branding (e.g. YouTube red, Spotify green, Settings blue) determines primary button background. Button content color is dynamically evaluated:
$$\text{Luminance } L = 0.2126 \cdot R + 0.7152 \cdot G + 0.0722 \cdot B$$
- If $L > 0.45$: `contentColor = Color(0xFF000000)` (black)
- If $L \le 0.45$: `contentColor = Color(0xFFFFFFFF)` (white)
This mathematically guarantees $\ge 4.5:1$ contrast against any installed app's brand theme.

## Typography
Defined in [`Type.kt`](file:///a:/Development/app-lock/app/src/main/java/com/pixelguard/applock/ui/theme/Type.kt) adhering to Material 3 scale with **Tabular Figures (`fontFeatureSettings = "tnum"`)** enabled for stable numeric glyph spacing:
- **Headline Large (`32sp / 40sp`, Bold)**: Screen titles (e.g., "Pixel Guard").
- **Headline Medium (`26sp / 32sp`, SemiBold)**: Target app lock titles.
- **Headline Small (`22sp / 28sp`, SemiBold)**: Sub-section headers.
- **Title Large (`20sp / 26sp`, Medium)**: TopAppBar and section hero titles.
- **Title Medium (`15sp / 22sp`, SemiBold)**: Settings row titles and list headers.
- **Title Small (`14sp / 20sp`, SemiBold)**: Card titles and app list item names.
- **Body Large (`16sp / 24sp`, Normal)**: Description copy and dialog paragraphs.
- **Body Medium (`14sp / 20sp`, Normal)**: Standard explanatory copy.
- **Body Small (`12sp / 16sp`, Normal)**: Secondary timestamps, categories, and helper notes (`tnum`).
- **Label Medium (`12sp / 16sp`, Medium)**: Filter chips and inline controls.
- **Label Small (`11sp / 14sp`, Bold)**: Badges (e.g., "Required", "Optional", "Active", `tnum`).

## Spacing
Standard 4dp/8dp grid system:
- **`4dp` / `8dp`**: Micro gaps (badges, icon-to-label spacing, filter chip gaps).
- **`10dp`**: Tightened inner item spacing in LazyColumns for optimal information density.
- **`12dp` / `14dp`**: Inner card row padding and item spacing in LazyColumns.
- **`16dp` / `20dp`**: Screen horizontal edge gutters and dialog container padding.
- **`24dp` / `32dp`**: Section separators and hero element vertical margins.

## Border Radius
- **Micro (`6dp` - `8dp`)**: Badges, status chips, and tiny inline buttons.
- **Small (`10dp` - `12dp`)**: Filter chips, action buttons, AppListItem app icon clipping.
- **Medium (`16dp` - `18dp`)**: Cards, list items, permission cards, Settings cards.
- **Large (`24dp` - `28dp`)**: Search bar, modal bottom sheets.
- **Full Circle (`CircleShape`)**: Biometric pulse rings, keypad tonal discs, icon containers.

## Shadows
- Elevation shadows are deliberately minimized to preserve Android 17 edge-to-edge dark aesthetics.
- Visual hierarchy is established via **container color luminance** and subtle border strokes (`1dp outlineVariant`).

## Layout
- **Edge-to-Edge**: `enableEdgeToEdge()` on all activities with proper `Scaffold` inner padding.
- **LazyColumn**: Main apps dashboard, intruder logs, and settings lists.
- **LazyRow**: Horizontal filter chips with spaced arrangement.
- **Above-the-Fold Optimization**: Reclaimed ~120dp vertical space on Pixel 9 displays via horizontal security capsule.

## Responsive Breakpoints
- **Compact Phones (Pixel 9 / 9 Pro, width < 600dp)**: Standard single-column flow with floating bottom navigation/sheets.
- **Expanded / Foldable (Pixel 9 Pro Fold, width >= 600dp)**: Centered max-width constraints (`Modifier.widthIn(max = 640.dp)`) for keypad and overlay dialogs to prevent stretched inputs.

## Component Patterns

### Buttons
- **Primary Action (`Button`)**: Full-width or prominent, `RoundedCornerShape(14.dp)`, `height = 52.dp`.
- **Tonal Button (`FilledTonalButton`)**: Secondary batch actions (e.g., "Lock All", "Lock Critical"), `shape = 10.dp`.
- **Outlined Button (`OutlinedButton`)**: Tertiary dismissal / clear actions (e.g., "Unlock All", "Later").
- **Keypad Button (`Box` / `IconButton`)**: `72.dp x 72.dp` circular borderless tonal discs (`surfaceVariant.copy(alpha = 0.42f)`) with 30sp tabular digits and haptic tick callbacks.

### Forms
- **Search Bar (`OutlinedTextField`)**: `singleLine = true`, `RoundedCornerShape(24.dp)`, leading search icon and trailing clear `IconButton`.
- **Numeric Keypad**: 4 rows (`1-3`, `4-6`, `7-9`, `bio/0/del`) with instant 4-digit verification.
- **Passcode Setup Inputs**: Dual `OutlinedTextField` inputs (`Enter 4-Digit Passcode` & `Confirm 4-Digit Passcode`) with `PasswordVisualTransformation()`, chained `ImeAction.Next` ➔ `ImeAction.Done` keyboard navigation, wrapped in a scrollable `Modifier.imePadding()` container with automatic animated scroll-into-view behavior to prevent soft keyboard occlusion.

### Cards
- **`AppListItem`**: Card with `containerColor = surfaceVariant.copy(alpha = 0.35f)`, subtle `1dp outlineVariant` stroke on locked state, 44dp icon container, title, category, unified drill-down security capsule (`[ 🔑 PIN • $graceBadge › ]`), and trailing upright shackle spring bounce toggle (`-3dp` displacement, `0.92f` scale pulse).
- **`SecurityHealthGaugeCard`**: Streamlined **82dp horizontal security capsule** featuring a 54dp arc gauge (0–100%), tabular percentage display, status readout, inline `[Lock Critical]`, and `[Unlock All]` pill backed by a confirmation safeguard dialog (`Unlock All Apps?`).
- **`PermissionCard`**: 18dp Material 3 card structured into a two-tier layout:
  - *Header Row*: 40dp circular tonal icon disc (`primaryContainer` with `onPrimaryContainer` tint for crisp contrast), title with guaranteed single-line stability (hides redundant `[Required]` badge once granted), and top-right `(v) Active` badge in soft pastel pill (`primary.copy(alpha = 0.12f)` container + `primary` text).
  - *Body*: 52dp-indented description aligned cleanly beneath the title column (clamped to `maxLines = 1` when granted to save ~64dp of viewport height).
  - *Actions*: Bottom-right action row (`Later` text button + `Enable` filled button; or `OutlinedButton` when skipped). Eliminates horizontal crowding and guarantees 100% badge visibility.
- **`Setup Permissions Primary Action Button`**: 52dp prominent action button dynamically reflecting the zero-trust single-permission architecture (`"Grant Accessibility Permission"` ➔ `"Continue to App Lock"`). Fully actionable across all states, directly navigating the user to the Accessibility settings screen if ungranted, or advancing setup once enabled. Overlay permission (`SYSTEM_ALERT_WINDOW`) has been completely eradicated from the system.
- **`SettingsCard`**: 18dp container with `surfaceContainer` tint, `1dp outlineVariant` border stroke, 32dp header icon disc, and 36dp tonal row icon discs.
- **`Actionable Hero Banner` (Settings)**: Interactive security status card ("Your Apps are Protected") that detects unconfigured protections (e.g. Lock Phone Settings, Hide in App Switcher) and auto-scrolls down to the offending controls upon tap.
- **`Hide in App Switcher & Screen Mirroring`**: Toggles `FLAG_SECURE` dynamically on `MainActivity`, `LockActivity`, and system overlays. When disabled (default), enables full laptop screen mirroring via `scrcpy` and local screenshots. When enabled, strictly enforces hardware-level SurfaceFlinger blanking so apps remain hidden when switching apps.
- **`SecurityPrivacyFaqCard`**: Collapsible accordion-style FAQ card in Settings with "Common Questions" header (Help icon, "FAQ" badge). Contains 4 expandable items addressing why Pixel Guard needs app detection, how screen privacy is preserved, why the setup permission is required, and zero internet usage guarantee. Uses spring-animated `AnimatedVisibility` expand/shrink and `graphicsLayer` chevron rotation. Accordion behavior: one item expanded at a time, all start collapsed.

### Navigation & Chrome
- TopAppBar includes back arrow button, brand title, live status count (`$lockedCount apps locked`), and shield action icon.
- Consolidated **Search & Sort Strip**: Single horizontal row combining an edge-to-edge search bar with a compact sort assist chip (`Sort: A–Z`). Supports **Dual Search Matching** across both user-facing `appName` and technical `packageName` (e.g. searching `"vending"` surfaces Google Play Store).
- **Filter Chips Bar (`filter_chips_row`)**: Horizontally scrolling `LazyRow` featuring pinned `Locked Only ($count)` and `Customized ($count)` FilterChips, 5 built-in categories (`Banking & Finance`, `Social & Messaging`, `Sensitive & Recommended`, `System Settings`, `Other Apps`), dynamic user tag chips (`#$tag ($count)`), and `+ Tag` AssistChip.
- **Contextual Tag Action Strip**: Rendered when filtering by a custom tag, showing live counter (`X of Y protected in #$tag`), 1-tap `[Lock All]`, 1-tap `[Unlock All]`, and a delete tag icon button with confirmation dialog.

### Modals & Bottom Sheets
- **`AppSecurityProfileSheetContent`**: Modal bottom sheet featuring:
  - Header with app icon, title, package name, and live Lock/Unlock toggle.
  - When to Lock This App segmented `FilterChip` selector (`Default`, `Immediately (15s buffer for switching apps)`, `When screen turns off`, `After 1 minute`, `After 5 minutes`, `After 15 minutes`).
  - **Category & Tags Section**: Interactive category dropdown capsule allowing reclassification to any of the 5 categories (with a "Reset to Default" action), plus interactive custom tag chips with `(x)` remove buttons and a `+ Add Tag` AssistChip opening `AddTagToAppDialog`.
  - Advanced Protection toggles: "Always Ask for Passcode" and "Hide Notification Previews".
- Modal bottom sheets always use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

### Tables
- Data is structured into structured lists or card grids rather than dense HTML tables.

### Loading States
- Asynchronous app queries and icon rasterization occur on `Dispatchers.IO` before list rendering, with clean shimmer or circular progress indicators for first-time indexing.

### Empty States
- Illustrated placeholder icons with supportive typography when search yields zero matches or intruder logs are empty.

### Error States & Sensory Choreography
- **3-Stage PIN Failure Sequence**:
  1. *Stage 1 (Error Morph)*: PIN dots smoothly morph from brand color to `MaterialTheme.colorScheme.error` over 60ms.
  2. *Stage 2 (Elastic Shake)*: Keypad header and dots execute a 5-point dampened spring shake (-22f to +22f) over 180ms with heavy haptic feedback.
  3. *Stage 3 (Dissolve & Reset)*: Dots fade out over 120ms, pin input resets, and animatables cleanly snap back to base state.
- **Lockout Penalty Indicator**: Sleek `LinearProgressIndicator` drain bar showing elapsed lockout penalty time directly under lockout countdown typography.
- **Permission Warning**: Tinted red/amber container banner (`errorContainer`) with clear setup guidance.

### Animations & Expressive Motion
- **Frosted Glass Backdrop Blur**: Hardware-accelerated `Modifier.blur(24.dp)` paired with radial ambient gradients over OLED black (`#000000`), generating an ultra-smooth frosted depth layer on Android 13+ platform `RenderEffect`.
- **Dynamic App Brand Aura**: Soft pulsing radial glow behind target app icon on lock overlay (`scale = 1f` to `1.15f`, `alpha = 0.22f` to `0.45f` with `tween(1600, FastOutSlowInEasing)`), using dominant app color tones.
- **Liquid Ripple PIN Dots**: Individual spring physics (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) triggering a scale bloom (`1.28f` to `1.0f`) on fill, and coordinated 3-stage failure shake and dissolve.
- **Elastic Keypad Shape-Morphing**: Buttons feature live spring compression (`0.88f` scale on press via `MutableInteractionSource`) with soft shape transformation from rounded square to circle.
- **Unlock Success Burst**: Morphing checkmark and glowing halo burst state (`isUnlocked = true`) providing instant positive reinforcement before window dismissal.
- **Swipe-to-Action Gestures**: Non-destructive Material 3 `SwipeToDismissBox` on app list rows:
  - *Swipe Right*: Toggles lock/unlock with live color morphing and animated lock icons. Row auto-rebounds to settled state.
  - *Swipe Left*: Opens per-app grace period sheet.
- **Spring Shackle Bounce**: Upright shackle rotation (`-14f` to `0f`) and scale pulse on toggle.
- **24-Hour Circular Schedule Dial**: Interactive circular day/night time visualizer (`Canvas` with `drawArc` and animated sweep angle) in the Night Lockdown sheet, rendering active lockdown hours, duration badge, and start/end time indicators.
- **Pinch-to-Zoom Evidence Viewer**: Hardware-accelerated multi-touch zoom (`detectTransformGestures` supporting 1x to 3.5x magnification) and two-finger panning in the intruder log photo preview dialog.
- **Physics-Based Springs**: `spring(stiffness = Spring.StiffnessMediumLow)` for lock toggles, shackle bounces, and gauge animations.
- **Biometric Aura**: Infinite pulsing scale (`1f` to `1.18f`) and alpha (`0.2f` to `0.55f`) with `tween(1200, FastOutSlowInEasing)`.
- **Refresh Rate**: Guaranteed locked 120Hz smooth scrolling without frame skips.

### Haptics (3-Tiered Linear Actuations)
- **`EFFECT_TICK`**: Keypad numeric button taps, category filter chip clicks.
- **`EFFECT_CLICK`**: Lock toggle switches, action buttons.
- **`EFFECT_DOUBLE_CLICK`**: Unlock success confirmation.
- **`EFFECT_HEAVY_CLICK`**: PIN auth failure, lockout penalty.

### Accessibility
- Touch targets strictly maintain a minimum of **48dp x 48dp** (keypad buttons are 72dp).
- Content descriptions provided on all iconography and image components.
- High-contrast color pairings conforming to WCAG AA dark mode guidelines.

### App Launcher Icon (Google Design Language)
- **Design Philosophy**: Perfectly aligns with Google's native app icon aesthetic on Google Pixel devices (similar to Google Authenticator, Google Drive, Google Files, and Google Play Protect). Replaces dark cyber-antivirus badge styling with a clean, friendly, native system utility presence.
- **Adaptive Canvas**: 108dp x 108dp viewport with 72dp safe zone (`safe circle radius = 33dp` centered at `54, 54`), immune to cropping across all launcher masks (Circle, Squircle, Rounded Square, Pebble).
- **Background Layer (`ic_launcher_background.xml`)**: Solid `#FFFFFF` pure white adaptive canvas.
- **Foreground Layer (`ic_launcher_foreground.xml`)**:
  - *Shield Geometry*: Smooth rounded geometric security crest divided into 4 flowing Google brand quadrants:
    - *Top-Left*: Google Blue (`#4285F4`)
    - *Top-Right*: Google Red (`#EA4335`)
    - *Bottom-Right*: Google Yellow (`#FBBC05`)
    - *Bottom-Left*: Google Green (`#34A853`)
  - *Center Padlock Cutout*: Pure white negative-space cutout unified body (21.5dp x 15.5dp, `rx = 3.5dp`) and arched shackle (stroke width 4dp, radius 8.5dp) carved cleanly through the center of the shield, letting the white canvas breathe through.
  - *Shackle Arch Windows*: Shield colors (Blue left, Red right) visible through the inner loop opening.
  - *Keyhole*: Crisp Google Material Blue (`#1A73E8`) aperture centered in the lock body.
- **Monochrome Themed Icon (`ic_launcher_monochrome.xml`)**: Single solid `#FFFFFF` vector path utilizing `android:fillType="evenOdd"` to cleanly carve out the padlock silhouette and fill the keyhole, providing flawless dynamic Monet wallpaper theming on Android 13-17 Pixel Launcher.

### Extra Protection Recommended Card & 1-Tap Arming
- **Card Hierarchy**: Rendered prominently in `SettingsScreen` whenever critical anti-tamper defenses are disarmed (e.g. `isSettingsProtected == false` or `isRecentsMaskingEnabled == false`).
- **Styling**: `PixelAmber` container (`#F9AB00` with 0.12f alpha fill and 0.35f alpha outline border), Titan M2 shield badge, and high-visibility warning iconography.
- **Fast Remediation**: Contains 1-tap `[Turn On Recommended Protections]` elevated button with `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)`. Tapping instantly arms both Phone Settings Protection and App Switcher Hiding, closing the privacy gap without tedious manual toggle hunting.

### Liquid Ripple PIN Dots (6-Digit Indicator)
- **6-Dot Geometry**: 6 individual spring-animated indicator dots (`MASTER_PIN_LENGTH = 6`) with spring physics (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) triggering a scale bloom (`1.28f` to `1.0f`) on fill, and coordinated 3-stage failure shake and dissolve.
- **Keypad Dynamic Reshuffle**: Keypad positions re-scramble per verification attempt when scramble mode is enabled, preventing shoulder-surfing or smudge detection.

### Master Recovery Key Presentation Dialog (Onboarding)
- **Modal Architecture**: Rendered upon successful Step 2 passcode creation.
- **Monospace Container**: 16-character alphanumeric key rendered in unambiguous base32 (`XXXX-XXXX-XXXX-XXXX`), set in `FontFamily.Monospace` with `1.5.sp` letter spacing and high-contrast primary accent.
- **One-Tap Clipboard Copy**: Outlined button with dynamic "Copied to Clipboard!" haptic state.
- **Explicit User Commitment**: Dismissal requires tapping "I Have Saved My Recovery Key", ensuring the user acknowledges safeguarding their offline recovery credentials.

### 3-Step Secure Passcode Change Sheet
- **Modal Architecture**: Integrated inside `ChangePinSheetContent` as a dedicated bottom sheet.
- **Three-Stage Verification Flow**:
  1. *Step 1: Current Passcode*: Challenges the user for their existing 6-digit passcode via PBKDF2 hash verification. Prevents unauthorized passcode modification if the phone is momentarily unlocked.
  2. *Step 2: New 6-Digit Passcode*: User enters the new passcode with real-time numeric masking and weak-PIN validation.
  3. *Step 3: Confirm New Passcode*: Confirms matching 6-digit code with error feedback on mismatch.
- **Visual Hygiene & Security**: All inputs use `PasswordVisualTransformation('\u2022')`, `KeyboardType.NumberPassword`, and auto-advance focus.

## Do Not
- **Do NOT** use hardcoded non-theme colors for backgrounds or text.
- **Do NOT** use square (0dp radius) buttons or harsh rectangular cards.
- **Do NOT** perform heavy layout or bitmap calculations inside `@Composable` drawing passes.
- **Do NOT** display light mode surfaces when ambient privacy lock is engaged.

