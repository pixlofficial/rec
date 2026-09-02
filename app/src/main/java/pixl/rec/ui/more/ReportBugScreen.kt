package pixl.rec.ui.more

import android.os.Build
import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.BuildConfig
import pixl.rec.R
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.VideoCodec
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

@Composable
fun ReportBugScreen(
    capabilities: DeviceCapabilities?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var title by rememberSaveable { mutableStateOf("") }
    var whatHappened by rememberSaveable { mutableStateOf("") }
    var stepsToReproduce by rememberSaveable { mutableStateOf("") }
    var isDiagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    val isFormValid = title.isNotBlank() && whatHappened.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Header
        LegalPageHeader(
            iconResId = R.drawable.ic_pixel_bug,
            iconTint = HyperCrimson,
            title = "REPORT A BUG",
            subtitle = "DIAGNOSTICS & ISSUE DISPATCH",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Telemetry Auto-Attached Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderStark, RoundedCornerShape(14.dp))
                .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ToxicLime)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM TELEMETRY AUTO-ATTACHED",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BitcountPropSingle,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Icon(
                        imageVector = if (isDiagnosticsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} • REC v${BuildConfig.VERSION_NAME}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )

                if (isDiagnosticsExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF09090D))
                            .border(1.dp, Color(0xFF1E1E28), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = TelemetryReportHelper.getFormattedDiagnostics(capabilities),
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = BitcountPropSingle,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. User Form Fields
        Text(
            text = "ISSUE DETAILS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BitcountPropSingle,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Title Field
        CyberTextField(
            value = title,
            onValueChange = { title = it },
            label = "TITLE *",
            placeholder = "e.g. 120 FPS recording stutters on device...",
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // What Happened Field
        CyberTextField(
            value = whatHappened,
            onValueChange = { whatHappened = it },
            label = "WHAT HAPPENED? *",
            placeholder = "Describe what you saw versus what you expected...",
            minLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Steps to Reproduce Field
        CyberTextField(
            value = stepsToReproduce,
            onValueChange = { stepsToReproduce = it },
            label = "STEPS TO REPRODUCE (OPTIONAL)",
            placeholder = "1. Set target FPS to 120\n2. Open game and start recording\n3. Observed stutter...",
            minLines = 3
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Dispatch Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DISPATCH REPORT",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BitcountPropSingle,
                letterSpacing = 1.sp
            )
            if (!isFormValid) {
                Text(
                    text = "REQUIRED FIELDS (*)",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = BitcountPropSingle,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Primary: GitHub Issue
        CyberActionButton(
            iconResId = R.drawable.ic_pixel_github,
            title = "SUBMIT VIA GITHUB ISSUES",
            subtitle = "Pre-fills full Markdown report into REC issue tracker",
            accentColor = HyperCyan,
            badge = "RECOMMENDED",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildBugReportMarkdown(title, whatHappened, stepsToReproduce, capabilities)
                val url = TelemetryReportHelper.createGitHubIssueUrl(
                    title = if (title.isNotBlank()) "[BUG] $title" else "[BUG] Issue Report",
                    body = markdown,
                    labels = "bug"
                )
                TelemetryReportHelper.openGitHubIssue(context, url)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary: Email
        CyberActionButton(
            iconResId = R.drawable.ic_pixel_more,
            title = "SUBMIT VIA EMAIL",
            subtitle = "Opens default mail client to ${TelemetryReportHelper.DEFAULT_SUPPORT_EMAIL}",
            accentColor = ToxicLime,
            badge = "EMAIL",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildBugReportMarkdown(title, whatHappened, stepsToReproduce, capabilities)
                TelemetryReportHelper.sendEmail(
                    context = context,
                    subject = if (title.isNotBlank()) "[REC Bug] $title" else "[REC Bug Report]",
                    body = markdown
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tertiary: Copy
        CyberActionButton(
            iconResId = R.drawable.ic_pixel_code,
            title = "COPY REPORT TO CLIPBOARD",
            subtitle = "Copies formatted Markdown text to paste anywhere",
            accentColor = TextSecondary,
            badge = "COPY",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildBugReportMarkdown(title, whatHappened, stepsToReproduce, capabilities)
                TelemetryReportHelper.copyToClipboard(context, "Bug Report", markdown)
            }
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = BitcountPropSingle,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = BitcountPropSingle
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceElevated,
                unfocusedContainerColor = SurfaceElevated,
                focusedBorderColor = HyperCyan,
                unfocusedBorderColor = BorderStark,
                cursorColor = HyperCyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CyberActionButton(
    @DrawableRes iconResId: Int,
    title: String,
    subtitle: String,
    accentColor: Color,
    badge: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBorderColor = if (enabled) accentColor.copy(alpha = 0.5f) else BorderStark
    val animatedBorderColor by animateColorAsState(targetValue = targetBorderColor, label = "cyberButtonBorder")

    val targetIconBg = if (enabled) Color(0xFF0D0D12) else Color(0xFF09090D)
    val animatedIconBg by animateColorAsState(targetValue = targetIconBg, label = "cyberButtonIconBg")

    val targetIconTint = if (enabled) accentColor else TextMuted
    val animatedIconTint by animateColorAsState(targetValue = targetIconTint, label = "cyberButtonIconTint")

    val targetTitleColor = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f)
    val animatedTitleColor by animateColorAsState(targetValue = targetTitleColor, label = "cyberButtonTitle")

    val targetBadgeBg = if (enabled) accentColor.copy(alpha = 0.15f) else Color(0xFF14141C)
    val animatedBadgeBg by animateColorAsState(targetValue = targetBadgeBg, label = "cyberButtonBadgeBg")

    val targetBadgeBorder = if (enabled) accentColor.copy(alpha = 0.5f) else BorderStark
    val animatedBadgeBorder by animateColorAsState(targetValue = targetBadgeBorder, label = "cyberButtonBadgeBorder")

    val targetBadgeText = if (enabled) accentColor else TextMuted
    val animatedBadgeText by animateColorAsState(targetValue = targetBadgeText, label = "cyberButtonBadgeText")

    val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.45f, label = "cyberButtonAlpha")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .border(1.dp, animatedBorderColor, RoundedCornerShape(14.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(14.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(animatedIconBg)
                .border(1.dp, animatedBadgeBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = animatedIconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = animatedTitleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BitcountPropSingle,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(animatedBadgeBg)
                        .border(1.dp, animatedBadgeBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        color = animatedBadgeText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BitcountPropSingle
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = if (enabled) TextSecondary else TextMuted,
                fontSize = 10.sp,
                fontFamily = BitcountPropSingle
            )
        }
    }
}

