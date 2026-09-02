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
import pixl.rec.ui.theme.HyperCyan

@Composable
fun TermsScreen(
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
            iconResId = R.drawable.ic_pixel_terms,
            iconTint = HyperCyan,
            title = "TERMS OF SERVICE",
            subtitle = "OPEN-SOURCE LICENSE & USAGE GUIDELINES",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Preamble Overview Card
        PreambleCard(
            text = "By installing and utilizing PixL REC, you agree to the open-source software license terms and operational guidelines detailed below. PixL REC is free, open-source software designed for high-performance screen recording."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Section 1: Open-Source Architecture & License
        NumberedSectionHeader(
            title = "1. OPEN-SOURCE ARCHITECTURE & LICENSE",
            iconResId = R.drawable.ic_pixel_code,
            iconTint = HyperCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Open-Source Distribution:",
                description = "PixL REC is distributed under standard open-source licensing. The complete codebase is publicly hosted and auditable on GitHub."
            )
            ClauseItem(
                title = "Free Personal & Commercial Use:",
                description = "You are free to utilize PixL REC to record games, tutorials, application demonstrations, or personal content for both non-commercial and commercial distribution without royalties or attribution fees."
            )
            ClauseItem(
                title = "No Warranty Disclaimer ('As-Is'):",
                description = "The application is provided 'as-is', without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and non-infringement."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Section 2: Media Ownership & Content Rights
        NumberedSectionHeader(
            title = "2. MEDIA OWNERSHIP & CONTENT RIGHTS",
            iconResId = R.drawable.ic_pixel_video,
            iconTint = HyperCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "100% User Media Ownership:",
                description = "All video recordings, audio tracks, and screenshots produced with PixL REC are your sole and exclusive property. PixL asserts zero copyright, ownership, license, or distribution rights over your media."
            )
            ClauseItem(
                title = "Compliance with Local Laws:",
                description = "You are solely responsible for ensuring that you have appropriate consent, rights, and legal permissions to capture, save, and distribute any on-screen content or audio streams under your local jurisdiction."
            )
            ClauseItem(
                title = "No DRM Circumvention:",
                description = "PixL REC strictly respects Android operating system security flags (such as FLAG_SECURE) and will not bypass hardware-enforced DRM or protected media surfaces."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Section 3: Hardware Acceleration & Performance
        NumberedSectionHeader(
            title = "3. HARDWARE ACCELERATION & PERFORMANCE",
            iconResId = R.drawable.ic_pixel_speed,
            iconTint = HyperCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Zero-Copy Pipeline:",
                description = "PixL REC utilizes direct MediaProjection to MediaCodec GraphicBuffer hardware pipelining to minimize CPU overhead (targeting 3–5% CPU utilization)."
            )
            ClauseItem(
                title = "Device Hardware Capabilities:",
                description = "Achievable recording frame rates (up to 120+ FPS), maximum resolutions (up to 4K UHD), and HEVC (H.265) hardware encoder availability are dependent on your device's SoC and VPU capabilities."
            )
            ClauseItem(
                title = "Automatic Fallback Architecture:",
                description = "If a device hardware encoder fails or rejects configuration parameters, the recording engine will automatically attempt graceful fallback to standard AVC (H.264)."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Section 4: User Responsibility & Community
        NumberedSectionHeader(
            title = "4. USER RESPONSIBILITY & COMMUNITY",
            iconResId = R.drawable.ic_pixel_gavel,
            iconTint = HyperCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Responsible Recording:",
                description = "Do not use PixL REC to record sensitive personal credentials, private banking screens, or unauthorized third-party personal communications."
            )
            ClauseItem(
                title = "Community Contributions:",
                description = "Bug reports, feature suggestions, and code contributions to the PixL REC repository are governed by our public GitHub contributor guidelines."
            )
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
