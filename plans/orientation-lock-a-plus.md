# Recording Orientation Lock — Direction A+

## Context & Decision History

### Original Problem
The app starts recording at whatever resolution matches the current device orientation.
If the user starts in portrait (1080×2400) and then opens a landscape game, the recording
stays at 1080×2400 — the game is letterboxed/scaled into a portrait frame.

### Approaches Evaluated

#### Direction C: Segmented Codec Restart + Stitcher (REJECTED)
- Hot-swap `MediaCodec` at new resolution on rotation, write to per-orientation temp MP4
  segments, stitch into a single MP4 on stop via `MediaExtractor` + `MediaMuxer`.
- **Was implemented**, then reverted after review identified critical issues:
  - `MediaMuxer` only supports ONE `MediaFormat` per track — the final MP4's `tkhd`/`stsd`
    headers declare a single resolution. Players that read container headers (YouTube,
    Discord, WhatsApp transcoders) would stretch/crush the changed-resolution frames.
  - Audio PTS continuity across segment boundaries needs a global monotonic clock, not
    per-segment offset estimation.
  - Each new segment must begin with an IDR keyframe (`PARAMETER_KEY_REQUEST_SYNC_FRAME`)
    or the segment is not independently decodable.
  - Surface swap racing with `VirtualDisplay` and GPU `GraphicBuffer` release can crash on
    some SoCs.
  - The `SegmentStitcher` needs real-world validation across ExoPlayer, VLC, MX Player,
    QuickTime, and social media transcoders before shipping.
- **Verdict:** Cool engineering, fragile UX. The file might look broken when shared.
  Shelved for V2 R&D with ChatGPT's hardened architecture (global clock, quiesce lifecycle,
  explicit segment state machine, in-band SPS/PPS).

#### Direction A+: Fixed Canvas with Orientation Lock Setting (CHOSEN)
- Let the user choose their preferred recording canvas orientation in settings.
- The codec resolution is fixed for the entire session — no restart, no stitching.
- `VirtualDisplay` automatically handles scaling/centering content that doesn't match
  the locked canvas orientation (clean pillarboxing/letterboxing).
- **Pros:** Zero frame drops, zero risk, 100% player/transcoder compatibility, trivial to
  implement.
- **Cons:** Non-matching orientation gets black bars (accepted trade-off).

---

## Implementation Plan

### New Setting: `RecordingOrientation`

```kotlin
enum class RecordingOrientation(val displayName: String) {
    AUTO("Auto (match device)"),       // Current behavior: use orientation at record start
    LANDSCAPE("Landscape"),            // Always max×min — gamers' pick
    PORTRAIT("Portrait");              // Always min×max — vertical content creators
}
```

Default: `AUTO`

### Changes Required

#### 1. [NEW] `RecordingOrientation` enum
- Add to `RecordingConfig.kt` alongside the existing enums.
- Add field: `val recordingOrientation: RecordingOrientation = RecordingOrientation.AUTO`

#### 2. [MODIFY] `ScreenRecorderEngine.kt`
- In `start()`, where we compute `initialWidth` / `initialHeight` based on current rotation:
  - If `config.recordingOrientation == LANDSCAPE` → force `max(w,h) × min(w,h)`
  - If `config.recordingOrientation == PORTRAIT` → force `min(w,h) × max(w,h)`
  - If `config.recordingOrientation == AUTO` → current behavior (match device rotation)
- The `DisplayListener` rotation handler stays as-is: it resizes VirtualDisplay so the
  content is re-centered, but the codec stays fixed.

#### 3. [REVERT] Stitching Infrastructure
- Delete `SegmentMuxer.kt`
- Delete `SegmentStitcher.kt`
- Revert `VideoEncoder.kt` — remove `reconfigure()`, `drainRemainingBuffers()`,
  `createCodecAndSurface()` helper. Restore original `prepare()`.
- Revert `ScreenRecorderEngine.kt` — remove segment tracking, debounce job,
  `handleOrientationChange()`, segmented stop/stitch logic. Restore single-muxer path.
- Revert `MediaStoreWriter.kt` — remove `openDeferred()`, `getFileDescriptor()`.
  Restore original `open()` that creates the muxer directly.
- Remove `orientationAdaptation` field from `RecordingConfig.kt`.

#### 4. [MODIFY] Dashboard UI
- Add an orientation selector to the settings (dropdown or segmented control) that maps to
  `RecordingOrientation`. Expose it alongside the existing resolution/FPS/codec settings.

### File Summary

| File | Action |
|---|---|
| `RecordingConfig.kt` | Add `RecordingOrientation` enum + field, remove `orientationAdaptation` |
| `ScreenRecorderEngine.kt` | Revert to single-muxer, add orientation lock logic in `start()` |
| `VideoEncoder.kt` | Revert to original (remove `reconfigure()`) |
| `MediaStoreWriter.kt` | Revert to original (remove deferred FD) |
| `SegmentMuxer.kt` | DELETE |
| `SegmentStitcher.kt` | DELETE |
| Dashboard UI | Add orientation setting picker |

### Verification
```bash
./gradlew assembleDebug test
```
Manual: Start recording in each orientation mode, rotate mid-recording, verify the canvas
stays locked and content is correctly centered/pillarboxed.
