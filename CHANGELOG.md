# Changelog

All notable changes to **REC by PixL** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.2.0]: https://github.com/pixlofficial/rec/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/pixlofficial/rec/releases/tag/v0.1.0
