# Contributing to REC by PixL

Thank you for your interest in contributing to **REC**! REC is an open-source, zero-copy, hardware-accelerated screen recorder engineered for Android with a clean dark aesthetic and strict zero-bloat privacy.

---

## 🏛️ Core Principles & Non-Negotiables

When submitting code to REC, please keep our core engineering pillars in mind:

1. **Zero-Copy Performance First:**
   * **Never copy raw pixel data to CPU memory or `ImageReader` in the recording path.**
   * Always pipe `MediaProjection` $\rightarrow$ `VirtualDisplay` $\rightarrow$ `MediaCodec` Input Surface (`AHardwareBuffer` / `GraphicBuffer`).
   * Keep overall CPU overhead strictly below **~3–5%** during active 1080p 120 FPS recording.
2. **Nano-Precision Audio Synchronization:**
   * Internal audio (`AudioPlaybackCapture`) and microphone audio (`AudioRecord`) must be timestamped with `System.nanoTime()` and strictly aligned with video presentation timestamps (`PTS`).
3. **100% Offline & Privacy First:**
   * Zero analytics trackers, zero advertisement SDKs, zero cloud telemetry, and zero third-party dependencies that violate user privacy.
4. **Clean Dark Theme & Typography Hierarchy:**
   * High-contrast styling with vivid accents (`#E50914`, `#00FF66`, `#00E5FF`).
   * Display typography (`BitcountPropSingle` / monospace) is paired for headers and telemetry, while clean proportional sans-serif is used for subtitles and body text to prevent layout wrapping.

---

## 🛠️ Development Setup

### Prerequisites:
* **JDK 17 or 21**
* **Android Studio (Ladybug / Meerkat or newer)**
* **Android SDK Build Tools 35.0.0+**
* **Minimum Android Test Device:** Android 10 (API 29) or higher

### Cloning & Building:
```bash
# Clone repository
git clone https://github.com/pixlofficial/rec.git
cd rec

# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew test

# Run Lint
./gradlew lint
```

---

## 🌿 Git & Pull Request Workflow

1. **Fork the Repository:** Create a personal fork on GitHub.
2. **Create a Feature Branch:**
   ```bash
   git checkout -b feat/my-awesome-feature
   # or
   git checkout -b fix/issue-description
   ```
3. **Write Conventional Commits:**
   * `feat:` A new user-facing feature or enhancement.
   * `fix:` A bug fix.
   * `perf:` A performance optimization.
   * `refactor:` Code restructuring without behavioral changes.
   * `docs:` Documentation updates.
   * `ci:` GitHub Actions / build pipeline changes.
4. **Verify Tests & Builds Locally:**
   ```bash
   ./gradlew assembleDebug assembleRelease test
   ```
5. **Open a Pull Request:**
   * Provide a clear description of the problem solved and any relevant test device details (Android version, device model).
   * Include before/after screenshots or screen recordings for UI changes.

---

## 🐛 Reporting Bugs

When opening an issue, please include:
* **Device Model:** (e.g., Samsung Galaxy S23, Google Pixel 8, Moto G57)
* **Android Version:** (e.g., Android 11, Android 14, Android 15)
* **Steps to Reproduce:** Clear, numbered steps to replicate the bug.
* **Logcat (if applicable):** Output from `adb logcat -s ScreenRecorderEngine:V VideoEncoder:V AudioCaptureManager:V`

---

## 📜 License

By contributing to **REC by PixL**, you agree that your contributions will be licensed under the [GNU General Public License v3.0 (GPL-3.0)](./LICENSE).
