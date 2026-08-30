# Changelog

All notable changes to **REC by PixL** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
* **Cyberpunk Brutalist UI & Vault:**
  * Custom dark aesthetic with high-contrast brutalist cards and animated neon telemetry indicators.
  * In-app local media vault with video playback, Scoped Storage deletion, and system share sheet integration.
  * Hardware capability scanner probing real-time display refresh rate, encoder profile limits, and block-rate envelopes.
* **CI/CD & Packaging:**
  * Automated GitHub Actions pipeline building signed Google Play **Android App Bundles (`.aab`)** and standalone universal **`.apk`** binaries.
  * Single source of truth version management via `version.properties`.

---

[0.1.0]: https://github.com/pixlofficial/rec/releases/tag/v0.1.0
