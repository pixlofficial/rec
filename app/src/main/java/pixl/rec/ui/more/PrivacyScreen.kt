package pixl.rec.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pixl.rec.R
import pixl.rec.ui.theme.ToxicLime

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Header with Back Button
        LegalPageHeader(
            iconResId = R.drawable.ic_pixel_shield,
            iconTint = ToxicLime,
            title = "PRIVACY POLICY",
            subtitle = "100% OFFLINE • ZERO TRACKERS • SCOPED STORAGE",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Preamble Overview Card
        PreambleCard(
            text = "PixL REC is engineered with strict local-first and zero-knowledge privacy. The application operates 100% offline and requires zero network permissions. No recordings, telemetry, analytics, or hardware profiles are ever collected, transmitted, or shared."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Section 1: Zero Network Permissions
        NumberedSectionHeader(
            title = "1. ZERO NETWORK PERMISSIONS",
            iconResId = R.drawable.ic_pixel_lock,
            iconTint = ToxicLime
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "No Internet Permission:",
                description = "The application manifest strictly omits android.permission.INTERNET. The Android operating system physically forbids the app from opening network sockets or connecting to remote servers."
            )
            ClauseItem(
                title = "100% Offline Processing:",
                description = "All hardware video encoding (MediaCodec HEVC/AVC), audio loopback muxing (MediaMuxer), and disk operations execute entirely on your device's local SoC hardware."
            )
            ClauseItem(
                title = "Zero Analytics & SDKs:",
                description = "PixL REC contains zero advertising SDKs, zero crash analytics telemetry (no Firebase, Sentry, or Google Analytics), and zero user identifiers."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Section 2: Media Storage & File Isolation
        NumberedSectionHeader(
            title = "2. MEDIA STORAGE & FILE ISOLATION",
            iconResId = R.drawable.ic_pixel_folder,
            iconTint = ToxicLime
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Scoped Storage Exclusivity:",
                description = "Recordings are saved exclusively to the standard Movies/REC directory using Android's modern Scoped Storage MediaStore API."
            )
            ClauseItem(
                title = "Zero Arbitrary File Access:",
                description = "The app possesses no permission to read your personal photos, private documents, downloads, or external storage files."
            )
            ClauseItem(
                title = "100% User Media Ownership:",
                description = "Every video file saved by PixL REC resides strictly on your local device. You retain exclusive ownership and control over your media files at all times."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Section 3: Media Projection Consent
        NumberedSectionHeader(
            title = "3. SCREEN PROJECTION CONSENT",
            iconResId = R.drawable.ic_pixel_display,
            iconTint = ToxicLime
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Explicit System Authorization:",
                description = "Screen recording starts only after you approve Android's explicit MediaProjection system consent modal. The app cannot capture your screen silently or autonomously."
            )
            ClauseItem(
                title = "Single-Use Session Tokens:",
                description = "Per Android 14+ (API 34+) security mandates, MediaProjection tokens are strictly single-use and are immediately discarded upon session termination."
            )
            ClauseItem(
                title = "Immediate Pipeline Destruction:",
                description = "The VirtualDisplay and encoder input surfaces are immediately unlinked and destroyed the instant recording is stopped."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Section 4: Audio Stream Privacy
        NumberedSectionHeader(
            title = "4. AUDIO STREAM PRIVACY",
            iconResId = R.drawable.ic_pixel_audio_waves,
            iconTint = ToxicLime
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Internal Audio Loopback:",
                description = "Internal sound capture uses Android 10+ AudioPlaybackCapture. Only media streams from applications that explicitly permit capture are mixed into your recording."
            )
            ClauseItem(
                title = "Microphone User Control:",
                description = "Microphone audio is captured strictly when the microphone toggle is explicitly turned ON by you."
            )
            ClauseItem(
                title = "Live Visualizer Telemetry:",
                description = "The real-time Stepped VU Meter provides visible feedback whenever audio capture is active, ensuring you always know when microphone input is running."
            )
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
