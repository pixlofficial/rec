package rec.pixl.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rec.pixl.ui.components.SectionCard
import rec.pixl.ui.components.TelemetryBadge
import rec.pixl.ui.dashboard.DashboardViewModel
import rec.pixl.ui.theme.BitcountPropSingle
import rec.pixl.ui.theme.BorderStark
import rec.pixl.ui.theme.HyperCrimson
import rec.pixl.ui.theme.ObsidianCanvas
import rec.pixl.ui.theme.SurfaceElevated
import rec.pixl.ui.theme.SurfaceRaised
import rec.pixl.ui.theme.TextMuted
import rec.pixl.ui.theme.TextPrimary
import rec.pixl.ui.theme.TextSecondary
import rec.pixl.ui.theme.ToxicLime

@Composable
fun MoreScreen(
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val capabilities = uiState.capabilities
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = ObsidianCanvas
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Header
            Text(
                text = "SYSTEM // ABOUT",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "PIXL REC • ZERO-COPY ARCHITECTURE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 100% Offline Privacy Guarantee Badge Card
            SectionCard(
                title = "PRIVACY & SECURITY GUARANTEE",
                titleTag = "100% OFFLINE",
                tagColor = ToxicLime
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ToxicLime.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, ToxicLime, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ToxicLime,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero Cloud Sync • Zero Tracking",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "100% offline and private. No mandatory accounts, telemetry SDKs, or cloud uploads. Your recordings never leave your device storage.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = BitcountPropSingle,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Support & Community Action Rows
            SectionCard(title = "COMMUNITY & FEEDBACK", titleTag = "OPEN SOURCE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportActionRow(
                        icon = Icons.Default.BugReport,
                        title = "Report a Bug",
                        subtitle = "Found an issue? Open a GitHub ticket",
                        onClick = {
                            openUrl(context, "https://github.com/PixL/REC/issues/new?template=bug_report.md")
                        }
                    )

                    SupportActionRow(
                        icon = Icons.Default.Lightbulb,
                        title = "Request a Feature",
                        subtitle = "Suggest new tools, codecs, or UI tweaks",
                        onClick = {
                            openUrl(context, "https://github.com/PixL/REC/issues/new?template=feature_request.md")
                        }
                    )

                    SupportActionRow(
                        icon = Icons.Default.Code,
                        title = "GitHub Repository",
                        subtitle = "Source code, release notes & guidelines",
                        onClick = {
                            openUrl(context, "https://github.com/PixL/REC")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Hardware SoC Diagnostics
            val display = capabilities?.display
            val isHevc = capabilities?.isHevcHardwareSupported == true
            val isAvc = capabilities?.codecs?.get(rec.pixl.core.model.VideoCodec.AVC)?.isHardwareAccelerated == true

            SectionCard(title = "HARDWARE ENGINE DIAGNOSTICS", titleTag = "VPU STATUS") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticRow(label = "DEVICE MODEL", value = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
                    DiagnosticRow(label = "ANDROID VERSION", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    DiagnosticRow(label = "SOC HARDWARE", value = Build.HARDWARE.uppercase())
                    DiagnosticRow(label = "DISPLAY REFRESH", value = "${display?.currentRefreshRate?.toInt() ?: 60} HZ AMOLED")
                    DiagnosticRow(label = "HEVC HW ASIC", value = if (isHevc) "ACTIVE (ZERO-COPY)" else "SOFTWARE")
                    DiagnosticRow(label = "AVC HW ASIC", value = if (isAvc) "ACTIVE (ZERO-COPY)" else "SOFTWARE")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. About PixL Card
            SectionCard(title = "ABOUT PIXL REC", titleTag = "v${rec.pixl.BuildConfig.VERSION_NAME}") {
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SupportActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
            }
        }
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open Link",
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
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
