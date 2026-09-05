# Changelog

All notable changes to **REC by PixL** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.4.2] - 2026-09-05

### 🚀 Added
* **High-Refresh 165 FPS Probing:**
  * Added 165 FPS display probing and encoder tier support for modern 144Hz & 165Hz gaming flagships (ASUS ROG, RedMagic, Motorola Edge).
* **Hardware AV1 Encoder Priority:**
  * Automatically detects and prioritizes hardware AV1 ASIC encoders (`video/av01`) on cutting-edge silicon (Snapdragon 8 Gen 3+, Tensor G4) over HEVC/AVC.
* **Proportional Scaling & Lite (540p) Tier:**
  * Added Lite (540p) resolution preset calculated with strict 16-pixel macroblock alignment, ensuring 4 clean tiers across all devices: Native (100%), Smooth (83%), Performance (67%), and Lite (50%).
* **Tactile Segmented Sliding Pill Selector (`SlidingPillSelector`):**
  * Custom Jetpack Compose component featuring single-line non-wrapping rows, physics-based spring animations, and haptic feedback.
* **Full-Screen Cyberpunk Countdown HUD & Tap-to-Cancel:**
  * Animated rotating tech HUD ring with scale-punch transitions, per-second haptic ticks, and tap-anywhere cancellation safety.
* **Hardware-Aligned Pixel-Square Digit Assets:**
  * Designed custom 120×120 pixel-square grid digits (`1`, `2`, `3`, `4`, `5`, `REC`) using REC's signature 8.2-pitch rounded-square design language (`assets/digits/` and `res/drawable/ic_digit_*.xml`).
* **Studio Audio Sliders & VU Multipliers:**
  * Independent dual-channel audio controls for Game Audio (0–100%) and Microphone Gain boost (0–200% / +6 dB) with soft-knee limiter and VU meter calibration.
* **Smart Bitrate Auto-Tune:**
  * Added recommendation dialog suggesting ideal bitrates on resolution switch with persistent user opt-out preference.
* **Safety Watchers (Battery & Storage Tripwires):**
  * Emergency auto-stop tripwires on low battery (<3%) or low storage (<200MB) to finalize recordings cleanly and write the `moov` atom before system shutdown.

### ⚡ Changed
* **Countdown Options Refinement:**
  * Streamlined countdown durations to `NONE (0s)`, `3s`, and `5s` with 100% custom pixel-square vector asset coverage.
* **Settings Architecture Overhaul:**
  * Replaced loose resolution tags with a full-width Material 3 `ExposedDropdownMenuBox` locked to the device's native panel aspect ratio.

---

## [0.4.1] - 2026-09-03

### 🐛 Fixed
* **Android 14+ (API 34–36) MediaProjection Startup Crash:**
  * Fixed asynchronous race condition in `ScreenRecorderEngine` where `VirtualDisplay` was instantiated prior to starting `VideoEncoder`, causing immediate session termination on modern Android versions.
* **MediaCodec Framerate Clamping & Profile Safety:**
  * Guarded requested framerates against `videoCaps.getSupportedFrameRatesFor()`, automatically clamping unsupported rates to encoder limits with dynamic fallback tiers.
  * Enforced SDR 8-bit `HEVCProfileMain` in `CodecProbe` to prevent profile rejection on devices without HDR10/Dolby Vision hardware support.
  * Removed conflicting real-time surface `KEY_OPERATING_RATE` hints.
* **Dynamic Hardware Capability Detection in Settings:**
  * Greys out capture framerates (90/120 FPS) not supported by the display panel or hardware ASIC encoder.
  * Added informative toast diagnostics explaining specific hardware limits upon tapping disabled framerate tags.
* **Standby Floating Pill Lifecycle Restoration:**
  * Fixed `RecordingService` unconditionally destroying `FloatingOverlayService` when stopping a recording session; standby pill is now cleanly preserved and restored if `alwaysOnFloatingPill` is enabled.
  * Reset ghost mode state (`isInvisibleGhost = false`) on session finish so standby bubbles never leak invisibility after recording ends.
  * Ensured `FloatingOverlayService.onStartCommand()` restores view visibility, clears temporary hidden flags, and removes lingering radial menus.
  * Guarded `MainActivity.onResume()` so foreground activity resumption never kills the overlay service during an active recording.
* **Floating Pill Touch Hitbox Expansion:**
  * Expanded collapsed pill window to 116dp × 88dp with a 70dp on-screen hit area (over 3x larger touch target) while preserving the clean 22dp visible bezel tip.
  * Attached gesture detection and click listeners to the root window container for effortless tap and drag initiation.
  * Expanded invisible ghost mode touch target to fill the full window for reliable gesture recall.
* **HUD Studio Background Silhouette Size Clamp:**
  * Removed hardcoded `44.dp` container clamp in `FloatingRadialMenuView`, allowing custom shape silhouettes (up to 56 DP) to expand dynamically on screen.
* **Bottom Nav Bar & Overlay Stop Button Visual Sizing:**
  * Normalized `ic_pixel_stop` vector viewport from 120dp to 64dp with centered 1.25x scaling, expanding visual stop footprint from 26.6 DP to 52.06 DP to perfectly match the idle record circle.

---

## [0.4.0] - 2026-09-02

### 🚀 Added
* **HUD Customization Studio (`HudStudioScreen`):**
  * Interactive studio subsystem featuring live real-time HUD preview and granular aesthetic controls.
  * Curated style presets: *Cyber Matrix, Bio Hazard, Neon Pulse, and Stealth Ops*.
  * Dynamic HUD geometry shapes: *Classic Pill, Chamfered Hex Pod, Cyber Diamond, and Round Minimal* via `HudShapeHelper` and `IsometricHexPodShape`.
  * Animated shaders: live organic breathing and pulsing neon borders via `HudAnimationHelper`.
  * Staggered spring animations and dynamic hex node items in `FloatingRadialMenuView`.
  * Configurable overlay drag physics and snapping modes (*Proximity Snap, Edge Dock, Free Float*).
  * Dual-state styling: separate customized aesthetics for standby mode versus active recording.
* **In-App Community & Support Hub:**
  * Dedicated `SupportScreen` detailing PixL's independent studio mission with Ko-fi and GitHub Sponsors integration.
  * Zero-permission, 100% offline commitment with zero trackers, zero cloud dependencies, and zero feature paywalls.
* **Zero-Permission Hardware Diagnostics & Feedback Dispatch:**
  * Dedicated `ReportBugScreen` and `RequestFeatureScreen` under the MORE hub.
  * `TelemetryReportHelper`: automated device hardware diagnostics auto-probing (model, SoC, Android version/API, display refresh rate & resolution, ASIC encoder capabilities).
  * Form validation with dynamic animated "light up" submit buttons.
  * Multi-channel dispatch options: pre-filled GitHub Issue URL (`Intent.ACTION_VIEW`), native Email client (`Intent.ACTION_SENDTO`), and clipboard export.
* **In-App Legal & Compliance Suite:**
  * Dedicated offline `PrivacyScreen`, `TermsScreen`, and `LicensesScreen` with custom dot-matrix icons.
* **Cyberpunk Dot-Matrix Pixel Iconography:**
  * Added 19 bespoke dot-matrix pixel vector assets and SVGs (`audio_waves`, `bolt`, `bug`, `code`, `coffee`, `display`, `folder`, `gavel`, `github` 15×15 Invertocat badge, `heart`, `hud_node`, `info`, `lightbulb`, `lock`, `more`, `shield`, `speed`, `terms`, `video`, `wrench`).

### ⚡ Changed & Optimized
* **MediaCodec Hardware Surface Pipeline:**
  * Configured encoder with real-time scheduler priority (`KEY_PRIORITY = 0`), explicit Studio BT.709 sRGB colorimetry, and full dynamic range video metadata.
  * Implemented hardware surface input suspend (`PARAMETER_KEY_SUSPEND`) and immediate keyframe synchronization (`PARAMETER_KEY_REQUEST_SYNC_FRAME`) across pause/resume cycles to eliminate macroblock corruption.
  * Dynamic encoder Profile and Level selection (AVC Level 5.1/5.2, HEVC Main Level 5.1) for high-bitrate 2K/4K recording.
  * 16-pixel macroblock boundary alignment with automatic hardware register validation and proportional aspect ratio clamping in `CodecProbe`.
* **More Screen Cleanliness:**
  * Streamlined `SupportHeroCard` layout, removing misleading external link icons in favor of direct in-app sub-screen navigation.

---

## [0.3.0] - 2026-08-31

### 🚀 Added
* **In-App Cyberpunk Video Player (Media3 ExoPlayer):**
  * Hardware-accelerated embedded video player with custom Cyberpunk HUD and zero-copy surface rendering.
  * Minimalist pure-white vector controls with dark contrast drop shadow for high-contrast viewing over light scenes.
  * Zero-freeze keyframe seeking with `SeekParameters.CLOSEST_SYNC` and optimized low-latency local buffering (`DefaultLoadControl`).
  * Full slow-motion / speed selector chips (`0.25X`, `0.5X`, `1.0X`, `1.5X`, `2.0X`), repeat loop, and external app launch action.
  * Automatic temporary hiding of the floating standby overlay during in-app playback for distraction-free viewing.
* **Tri-Channel Live Telemetry Oscilloscope:**
  * Real-time concurrent multi-colored oscilloscope plotting Frame Rate (Lime), Write Bitrate Mbps (Cyan), and Audio Waveform (Yellow) on the Dashboard.
* **Handcrafted PixL Vector Icon Suite:**
  * Complete set of 120×120 pixel-art vector drawables (`ic_pixel_share`, `ic_pixel_external`, `ic_pixel_play`, `ic_pixel_pause`, `ic_pixel_replay_5`, `ic_pixel_forward_5`, `ic_pixel_nav_dash`, `ic_pixel_power`, etc.).
  * Stepped 9-7-5-3-1 geometric Play icon and 9-block Pause dual bar.
* **Edge-to-Edge & Safe Insets Architecture:**
  * Comprehensive `WindowInsets.safeDrawing` handling across both portrait and landscape modes, safeguarding controls from display punch-holes, cutouts, and 3-button navigation bars.

### ⚡ Changed & Optimized
* **Telemetry Dashboard Redesign:**
  * Refined active hardware specs into a clean, read-only status badge on the Dashboard, hoisting all interactive configuration pickers to the CONFIG tab.
  * Enhanced typography hierarchy with strict single-line text constraints to eliminate horizontal wrapping.
* **Vault Card Clickability:**
  * Elevated click surface to the full media card for effortless one-tap playback.
* **High-Precision Timecode Standard:**
  * Unified high-precision timecodes formatted to `HH:MM:SS.X` with monospace digital typography.

---

## [0.2.0] - 2026-08-31

### 🚀 Added
* **Dynamic Orientation Locking Engine:**
  * Added Canvas Orientation configuration (`AUTO`, `LANDSCAPE`, `PORTRAIT`) with dynamic `VirtualDisplay` live surface rescaling.
* **Asynchronous Media Vault with LRU Memory Cache:**
  * Non-blocking background thumbnail decoding with 64-item `LruCache<Long, Bitmap>`, dropping vault initial load times from >1s down to <15ms.
* **Persistent Settings Storage:**
  * Implemented `ConfigPreferences` for automatic local persistence of recording configurations.
* **Standardized Binary Output Naming:**
  * Direct build output naming standard (`REC-v0.2.0.apk`, `REC-v0.2.0-debug.apk`, `REC-v0.2.0.aab`).

### ⚡ Changed & Optimized
* **Lock-Free 120 FPS Muxer Fast-Path:**
  * Replaced synchronized `ReentrantLock` frame writing with lock-free atomic sample dispatching once the muxer starts.
* **Zero-Allocation Startup Buffer Pooling:**
  * Added reusable `ArrayDeque<ByteArray>` buffer pool for pending keyframes, eliminating 5–15 MB of startup GC churn.
* **Hardware VPU Operating Rate & Priority Hints:**
  * Configured `KEY_OPERATING_RATE` and real-time kernel scheduling `KEY_PRIORITY = 0` on `VideoEncoder`.
* **16-Pixel Macroblock Boundary Alignment:**
  * Enforced mod-16 dimension rounding (`((dim + 15) / 16) * 16`), preventing VPU internal padding and edge artifacts on Qualcomm & MediaTek chips.
* **Deterministic EOS Synchronization:**
  * Replaced heuristic `delay(150)` with `CompletableDeferred` EOS await signals across video and audio encoders.
* **Unified Monotonic A/V Synchronization:**
  * Synchronized both audio PCM chunks and video surface timestamps against a unified `sessionBaseTimeNs` timeline.
* **Padé [3/3] Polynomial Soft-Limiter Acceleration:**
  * Accelerated 16-bit PCM limiter by 3–5x using Padé rational polynomial approximation (`x * (27 + x^2) / (27 + 9x^2)`).
* **Throttled RMS VU Decibel Calculations:**
  * Throttled live audio level measurement to 10 Hz matching UI telemetry refresh rate (80% CPU reduction).
* **Compose Recomposition & Animation Scoping:**
  * Scoped 10 Hz telemetry state collection strictly to `HeroRecordingCard`.
  * Scoped `MainScreen` and `BottomNavBar` to boolean recording states, eliminating full scaffold recompositions.
  * Synchronized overlay pill timer updates to 1-second boundaries (10x fewer overlay redraws).
  * Gated infinite pulse animations when idle or collapsed.
* **WindowManager Binder IPC Throttling:**
  * Throttled overlay window position updates to 60 Hz during edge-snap animations and cached screen `DockBounds`.
* **Battery & Sensor Efficiency:**
  * Reduced accelerometer sampling from 50 Hz to 5 Hz (`SENSOR_DELAY_NORMAL`) with squared magnitude vector math.
* **Zero-Reflection IPC:**
  * Migrated `RecordingConfig` from Java `Serializable` to Kotlin `@Parcelize` / `Parcelable`.

### 🔒 Build & Security Hardening
* **R8 ProGuard Full Minification & Shrinking:**
  * Enabled release R8 minification and resource shrinking, reducing standalone release APK size by **70% (67 MB $\rightarrow$ 20 MB)**.
* **Android 16 Compile SDK Alignment:**
  * Aligned `compileSdk = 36` across the project.

---

## [0.1.0] - 2026-08-30

### 🚀 Added
* **Zero-Copy Hardware Video Pipeline:**
  * Direct surface piping from `MediaProjection` $\rightarrow$ `VirtualDisplay` $\rightarrow$ `MediaCodec` Input Surface (`AHardwareBuffer` / `GraphicBuffer`).
  * Hardware-accelerated **HEVC (H.265)** and **AVC (H.264)** video encoders with real-time rate envelope calibration.
  * Support for high-framerate capture up to **120+ FPS** with strictly below ~3–5% CPU overhead.
* **Real-Time Dynamic Auto-Rotation Engine:**
  * Dynamic `DisplayManager.DisplayListener` detecting physical orientation changes mid-session.
  * Real-time `virtualDisplay.resize(w, h, dpi)` pipeline maintaining pristine aspect ratio without stopping or letterboxing.
* **Dual-Channel Nano-Precision Audio Engine:**
  * Native internal system audio loopback via Android 10+ `AudioPlaybackCaptureConfiguration`.
  * Concurrent microphone audio capture via `AudioRecord` with nanosecond `System.nanoTime()` timestamp alignment.
  * Studio-grade 16-bit PCM multi-channel software mixer with saturation soft-clipping protection.
* **Clean Canvas & Floating Game Pill:**
  * Draggable, magnetized pill overlay built entirely with Jetpack Compose.
  * **Invisible Ghost Mode:** Automatically hides the overlay after 2 seconds of inactivity.
  * **Gesture Recall:** Instant unhide via edge swipe, double tap, or notification tap.
  * Decoupled overlay lifecycle so screen capture functions seamlessly even if `SYSTEM_ALERT_WINDOW` permission is restricted.
* **Stop Triggers & Sensors:**
  * Hardware accelerometer **Shake-to-Stop** trigger with debounce filters.
  * Automatic stop on screen-off / device lock.
* **High-Contrast Dark UI & Media Vault:**
  * Clean dark aesthetic with high-contrast section cards and animated telemetry indicators.
  * In-app local media vault with video playback, Scoped Storage deletion, and system share sheet integration.
  * Hardware capability scanner probing real-time display refresh rate, encoder profile limits, and block-rate envelopes.
* **CI/CD & Packaging:**
  * Automated GitHub Actions pipeline building signed Google Play **Android App Bundles (`.aab`)** and standalone universal **`.apk`** binaries.
  * Single source of truth version management via `version.properties`.

---

[0.4.2]: https://github.com/pixlofficial/rec/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/pixlofficial/rec/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/pixlofficial/rec/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/pixlofficial/rec/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/pixlofficial/rec/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/pixlofficial/rec/releases/tag/v0.1.0
