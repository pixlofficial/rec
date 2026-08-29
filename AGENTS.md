# AGENTS.md — Development Guidelines for REC (by PixL)

Welcome to **REC**. This repository contains the source code for a zero-copy, hardware-accelerated, high-framerate (up to 120+ FPS) Android screen recording application and companion UI.

All AI agents, contributors, and automated tooling working in this codebase MUST follow the principles, architecture patterns, and constraints detailed below.

---

## 1. Project Philosophy & Core Pillars

1. **Zero-Copy Performance First:**
   * Never copy raw pixel data to CPU RAM or `ImageReader` in the hot recording path.
   * Always pipe `MediaProjection` $\rightarrow$ `VirtualDisplay` $\rightarrow$ `MediaCodec` Input Surface (`AHardwareBuffer` / `GraphicBuffer`).
   * Keep overall CPU overhead strictly below **~3–5%** during active 1080p 120 FPS recording.

2. **Nano-Precision Audio Synchronization:**
   * Internal audio (`AudioPlaybackCapture`) and microphone audio (`AudioRecord`) must be timestamped with `System.nanoTime()` and strictly aligned with video presentation timestamps (`PTS`).
   * Monotonic timestamp progression is required to prevent player playback stutter.

3. **Privacy & Zero Bloat:**
   * 100% offline, local recording. No mandatory user accounts, analytics SDKs, cloud sync requirements, or advertising trackers.

---

## 2. Platform Targets & Versioning

* **Minimum SDK (`minSdk`):** `29` (Android 10) — Mandatory for native `AudioPlaybackCaptureConfiguration`.
* **Target SDK (`targetSdk`):** `35` (Android 15) / `36` (Android 16 Ready).
* **Compile SDK (`compileSdk`):** `36` (Android 16).
* **Language & Tooling:** Kotlin 2.0+, Java 17/21, Android Gradle Plugin 8.5+, Jetpack Compose (Material 3).

### Forward-Compatibility Strategy for New Android Releases
* Always guard version-specific features with `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XXX)`.
* Adhere to Scoped Storage (`MediaStore.Video.Media`) without relying on deprecated `READ_EXTERNAL_STORAGE` or raw `/sdcard/` paths.
* Treat `MediaProjection` consent tokens as single-use (mandatory starting in Android 14 API 34+).

---

## 3. Repository Architecture & Directory Layout

```
PixL-Recorder/
├── PROJECT_BLUEPRINT.md               # Product Requirements & Technical Spec
├── AGENTS.md                          # Agent instructions & coding standards
├── .github/workflows/
│   └── build-apk.yml                  # CI/CD: Automated Gradle APK build & release
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Module settings
├── app/
│   ├── build.gradle.kts               # App dependencies (Compose, Coroutines, Media)
│   └── src/main/
│       ├── AndroidManifest.xml        # MediaProjection FGS, permissions, QS Tiles
│       ├── java/dev/pixl/recorder/
│       │   ├── PixLApp.kt             # Application class
│       │   ├── core/
│       │   │   ├── engine/            # Zero-copy MediaCodec & VirtualDisplay pipeline
│       │   │   │   ├── CodecProbe.kt          # Hardware capabilities scanner
│       │   │   │   ├── VideoEncoder.kt        # MediaCodec Surface encoder (HEVC/AVC)
│       │   │   │   ├── AudioEncoder.kt        # AAC MediaCodec encoder
│       │   │   │   └── ScreenRecorderEngine.kt# Master recording orchestrator
│       │   │   ├── audio/             # Internal audio loopback & mic mixing
│       │   │   │   ├── AudioCaptureManager.kt
│       │   │   │   └── PcmAudioMixer.kt
│       │   │   ├── storage/           # Scoped storage & file estimation
│       │   │   │   ├── MediaStoreWriter.kt
│       │   │   │   └── StorageCalculator.kt
│       │   │   └── model/             # StateFlow models & configurations
│       │   │       ├── RecordingConfig.kt
│       │   │       ├── RecorderState.kt
│       │   │       └── DeviceCapabilities.kt
│       │   ├── service/
│       │   │   ├── RecordingService.kt       # MediaProjection Foreground Service
│       │   │   └── FloatingOverlayService.kt # Draggable Compose Overlay Pill
│       │   └── ui/
│       │       ├── theme/             # Cyberpunk dark theme & glassmorphism
│       │       ├── dashboard/         # Main telemetry dashboard & controls
│       │       ├── overlay/           # Floating pill composable views
│       │       └── components/        # Audio visualizer, neon buttons, telemetry cards
│       └── res/                       # Drawables, layout, strings
```

---

## 4. Coding Conventions & Best Practices

1. **Kotlin Style:**
   * Use modern idiomatic Kotlin: `StateFlow`, Coroutines (`Dispatchers.Default`, `Dispatchers.IO`), and immutable data classes.
   * Avoid `GlobalScope`; tie coroutine lifecycles strictly to `Service` or `ViewModel` scopes.
2. **Jetpack Compose UI:**
   * Strictly adhere to Material 3 guidelines and unidirectional data flow (MVI / State Hoisting).
   * Do not hardcode dimensions or strings; utilize theme tokens (`MaterialTheme.colorScheme`) and resource references.
3. **MediaCodec & NDK Safety:**
   * Always verify `MediaCodecInfo.isHardwareAccelerated` before initializing encoders.
   * Catch and safely handle `MediaCodec.CodecException` with automatic fallback from HEVC $\rightarrow$ AVC.
   * Always release `MediaCodec`, `VirtualDisplay`, `AudioRecord`, and `MediaMuxer` in `finally` blocks or lifecycle teardowns.

---

## 5. Build, Test & CI Commands

* **Compile & Build Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
* **Run Unit Tests:**
  ```bash
  ./gradlew test
  ```
* **Linting & Code Quality:**
  ```bash
  ./gradlew lint
  ```
* **GitHub Actions Workflow:**
  * Pushing to `main` triggers `.github/workflows/build-apk.yml`, building the debug and release APKs and attaching them as downloadable artifacts.
