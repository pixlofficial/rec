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
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

@Composable
fun LicensesScreen(
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
            iconResId = R.drawable.ic_pixel_info,
            iconTint = TextSecondary,
            title = "THIRD-PARTY LICENSES",
            subtitle = "OPEN-SOURCE ECOSYSTEM ACKNOWLEDGMENTS",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Preamble Overview Card
        PreambleCard(
            text = "PixL REC is built with pride on the modern Android and Kotlin open-source ecosystem. We gratefully acknowledge the following core open-source libraries, frameworks, and developer tools that make this zero-copy recorder possible."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Section 1: Jetpack Compose & Material 3
        NumberedSectionHeader(
            title = "1. JETPACK COMPOSE & MATERIAL 3",
            iconResId = R.drawable.ic_pixel_layers,
            iconTint = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Copyright & Authors:",
                description = "Copyright © The Android Open Source Project (Google LLC)."
            )
            ClauseItem(
                title = "License:",
                description = "Licensed under the Apache License, Version 2.0 (the 'License'). You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0"
            )
            ClauseItem(
                title = "Description:",
                description = "Provides declarative reactive UI rendering, theme token management, graphics layer pipelines, and hardware-accelerated animations across all dashboard views and floating overlay pills."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Section 2: Kotlin & Coroutines
        NumberedSectionHeader(
            title = "2. KOTLIN & KOTLINX COROUTINES",
            iconResId = R.drawable.ic_pixel_bolt,
            iconTint = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Copyright & Authors:",
                description = "Copyright © 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors."
            )
            ClauseItem(
                title = "License:",
                description = "Licensed under the Apache License, Version 2.0."
            )
            ClauseItem(
                title = "Description:",
                description = "Powers the non-blocking, zero-allocation asynchronous concurrency framework, StateFlow reactive pipelines, and thread isolation across recording services and codec workers."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Section 3: AndroidX Media3 & MediaCodec Extensions
        NumberedSectionHeader(
            title = "3. ANDROIDX MEDIA3 & CODEC SUITE",
            iconResId = R.drawable.ic_pixel_video,
            iconTint = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Copyright & Authors:",
                description = "Copyright © The Android Open Source Project (Google LLC)."
            )
            ClauseItem(
                title = "License:",
                description = "Licensed under the Apache License, Version 2.0."
            )
            ClauseItem(
                title = "Description:",
                description = "Low-latency media container muxing, AudioPlaybackCapture internal loopback configurations, and MediaCodec hardware encoder capability probe interfaces."
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Section 4: Android Architecture & Lifecycle
        NumberedSectionHeader(
            title = "4. ANDROID LIFECYCLE & CORE UTILITIES",
            iconResId = R.drawable.ic_pixel_wrench,
            iconTint = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClauseCard {
            ClauseItem(
                title = "Copyright & Authors:",
                description = "Copyright © The Android Open Source Project (Google LLC)."
            )
            ClauseItem(
                title = "License:",
                description = "Licensed under the Apache License, Version 2.0."
            )
            ClauseItem(
                title = "Description:",
                description = "Provides AndroidX Core KTX, ViewModel lifecycle binding, and Activity result launchers for permission handling."
            )
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
