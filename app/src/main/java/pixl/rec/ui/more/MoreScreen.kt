package pixl.rec.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

private enum class LegalSheetType {
    PRIVACY,
    TERMS,
    LICENSES
}

@Composable
fun MoreScreen(
    viewModel: DashboardViewModel,
    onReportBugClick: (() -> Unit)? = null,
    onRequestFeatureClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val capabilities = uiState.capabilities
    val scrollState = rememberScrollState()

    var activeSubPage by rememberSaveable { mutableStateOf(MoreSubPage.HUB) }

    BackHandler(enabled = activeSubPage != MoreSubPage.HUB) {
        activeSubPage = MoreSubPage.HUB
    }

    AnimatedContent(
        targetState = activeSubPage,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(160))
        },
        label = "MoreSubPageTransition"
    ) { page ->
        when (page) {
            MoreSubPage.PRIVACY -> PrivacyScreen(onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.TERMS -> TermsScreen(onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.LICENSES -> LicensesScreen(onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.REPORT_BUG -> ReportBugScreen(capabilities = capabilities, onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.REQUEST_FEATURE -> RequestFeatureScreen(capabilities = capabilities, onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.SUPPORT -> SupportScreen(onBack = { activeSubPage = MoreSubPage.HUB })
            MoreSubPage.HUB -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // 1. Header Banner
                    Text(
                        text = "MORE // ABOUT",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "SUPPORT, APP INFO & LEGAL POLICIES",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hero Support Card
                    SupportHeroCard(
                        onClick = { activeSubPage = MoreSubPage.SUPPORT }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Section: FEEDBACK & IDEAS
                    SectionHeader(title = "FEEDBACK & IDEAS")
                    Spacer(modifier = Modifier.height(10.dp))

                    // Report a Bug Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_bug,
                        iconTint = HyperCrimson,
                        title = "REPORT A BUG",
                        subtitle = "Auto-diagnostics & issue dispatch",
                        badgeText = "SUPPORT",
                        badgeColor = HyperCrimson,
                        onClick = {
                            if (onReportBugClick != null) {
                                onReportBugClick()
                            } else {
                                activeSubPage = MoreSubPage.REPORT_BUG
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Request a Feature Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_lightbulb,
                        iconTint = CyberYellow,
                        title = "REQUEST A FEATURE",
                        subtitle = "Suggest new tools, codecs, or UI tweaks",
                        badgeText = "IDEAS",
                        badgeColor = CyberYellow,
                        onClick = {
                            if (onRequestFeatureClick != null) {
                                onRequestFeatureClick()
                            } else {
                                activeSubPage = MoreSubPage.REQUEST_FEATURE
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // GitHub Repository Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_code,
                        iconTint = ToxicLime,
                        title = "GITHUB REPOSITORY",
                        subtitle = "Source code, release notes & guidelines",
                        badgeText = "OPEN SOURCE",
                        badgeColor = ToxicLime,
                        onClick = {
                            openUrl(context, "https://github.com/pixlofficial/rec")
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Section: APP INFO & SYSTEM
                    SectionHeader(title = "APP INFO & SYSTEM")
                    Spacer(modifier = Modifier.height(10.dp))

                    // About PixL REC Card
                    SectionCard(title = "ABOUT PIXL REC", titleTag = "v${pixl.rec.BuildConfig.VERSION_NAME}") {
                        Text(
                            text = "High-performance, zero-copy, hardware-accelerated screen recorder engineered in Kotlin and Jetpack Compose.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = BitcountPropSingle,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Crafted by PixL • Precision Edition",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hardware Diagnostics Card
                    val display = capabilities?.display
                    val isHevc = capabilities?.isHevcHardwareSupported == true
                    val isAvc = capabilities?.codecs?.get(pixl.rec.core.model.VideoCodec.AVC)?.isHardwareAccelerated == true

                    SectionCard(title = "HARDWARE ENGINE DIAGNOSTICS", titleTag = "VPU STATUS", tagColor = HyperCyan) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DiagnosticRow(label = "DEVICE MODEL", value = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
                            DiagnosticRow(label = "ANDROID VERSION", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            DiagnosticRow(label = "SOC HARDWARE", value = Build.HARDWARE.uppercase())
                            DiagnosticRow(label = "DISPLAY REFRESH", value = "${display?.currentRefreshRate?.toInt() ?: 60} HZ AMOLED")
                            DiagnosticRow(label = "HEVC HW ASIC", value = if (isHevc) "ACTIVE (ZERO-COPY)" else "SOFTWARE")
                            DiagnosticRow(label = "AVC HW ASIC", value = if (isAvc) "ACTIVE (ZERO-COPY)" else "SOFTWARE")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Section: LEGAL & PRIVACY
                    SectionHeader(title = "LEGAL & PRIVACY")
                    Spacer(modifier = Modifier.height(10.dp))

                    // Privacy Policy Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_shield,
                        iconTint = ToxicLime,
                        title = "PRIVACY POLICY",
                        subtitle = "100% offline, zero trackers, zero data collection",
                        badgeText = "PRIVACY",
                        badgeColor = ToxicLime,
                        onClick = {
                            activeSubPage = MoreSubPage.PRIVACY
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Terms of Service Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_terms,
                        iconTint = HyperCyan,
                        title = "TERMS OF SERVICE",
                        subtitle = "Open-source terms, media rights & usage permissions",
                        badgeText = "TERMS",
                        badgeColor = HyperCyan,
                        onClick = {
                            activeSubPage = MoreSubPage.TERMS
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Third-Party Licenses Card
                    MoreActionCard(
                        iconResId = R.drawable.ic_pixel_info,
                        iconTint = TextSecondary,
                        title = "THIRD-PARTY LICENSES",
                        subtitle = "Jetpack Compose, AndroidX, Kotlin Coroutines",
                        badgeText = "OSS",
                        badgeColor = TextMuted,
                        onClick = {
                            activeSubPage = MoreSubPage.LICENSES
                        }
                    )

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

enum class MoreSubPage {
    HUB,
    PRIVACY,
    TERMS,
    LICENSES,
    REPORT_BUG,
    REQUEST_FEATURE,
    SUPPORT
}

@Composable
private fun SupportHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E0C17),
                        Color(0xFF12121B),
                        Color(0xFF0C161F)
                    )
                )
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        HyperCrimson.copy(alpha = 0.6f),
                        HyperCyan.copy(alpha = 0.5f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF280B17))
                    .border(1.dp, HyperCrimson.copy(alpha = 0.6f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_heart),
                    contentDescription = null,
                    tint = HyperCrimson,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SUPPORT PIXL",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BitcountPropSingle,
                        letterSpacing = 0.6.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HyperCrimson.copy(alpha = 0.2f))
                            .border(1.dp, HyperCrimson.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SPONSOR",
                            color = HyperCrimson,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BitcountPropSingle
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Fund zero-copy, ad-free independent development",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun MoreActionCard(
    @DrawableRes iconResId: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderStark, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon squircle badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0D12))
                .border(1.dp, iconTint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BitcountPropSingle
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontFamily = BitcountPropSingle
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right pill badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(badgeColor.copy(alpha = 0.12f))
                .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BitcountPropSingle,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("MoreScreen", "Failed to open url: $url", e)
    }
}
