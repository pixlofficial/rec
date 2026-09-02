package pixl.rec.ui.more

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

@Composable
fun RequestFeatureScreen(
    capabilities: DeviceCapabilities?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var title by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var proposedSolution by rememberSaveable { mutableStateOf("") }

    val isFormValid = title.isNotBlank() && problem.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Header
        LegalPageHeader(
            iconResId = R.drawable.ic_pixel_lightbulb,
            iconTint = CyberYellow,
            title = "REQUEST A FEATURE",
            subtitle = "COMMUNITY ROADMAP & PROPOSALS",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. PixL Philosophy Banner
        PreambleCard(
            text = "Every feature in REC must maintain zero-copy hardware acceleration, under 5% CPU overhead, and 100% offline privacy. Have an idea for a tool, workflow, or codec feature? We'd love to hear it!"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. User Form Fields
        Text(
            text = "PROPOSAL DETAILS",
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
            label = "FEATURE TITLE *",
            placeholder = "e.g. Custom video bitrate slider up to 100 Mbps...",
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Problem / Motivation
        CyberTextField(
            value = problem,
            onValueChange = { problem = it },
            label = "PROBLEM / USE CASE *",
            placeholder = "What challenge or workflow does this solve? Why would it be helpful?",
            minLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Proposed Solution / Workflow
        CyberTextField(
            value = proposedSolution,
            onValueChange = { proposedSolution = it },
            label = "PROPOSED SOLUTION (OPTIONAL)",
            placeholder = "How do you envision this working in REC's UI or recording engine?",
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Device Context Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderStark, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(HyperCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HARDWARE CONTEXT: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} • ANDROID ${Build.VERSION.RELEASE}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = BitcountPropSingle,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Dispatch Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DISPATCH PROPOSAL",
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
            title = "SUBMIT ON GITHUB ROADMAP",
            subtitle = "Posts feature proposal to community discussion board",
            accentColor = CyberYellow,
            badge = "RECOMMENDED",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildFeatureRequestMarkdown(title, problem, proposedSolution, capabilities)
                val url = TelemetryReportHelper.createGitHubIssueUrl(
                    title = if (title.isNotBlank()) "[FEATURE] $title" else "[FEATURE] Feature Proposal",
                    body = markdown,
                    labels = "enhancement"
                )
                TelemetryReportHelper.openGitHubIssue(context, url)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary: Email
        CyberActionButton(
            iconResId = R.drawable.ic_pixel_more,
            title = "SEND PROPOSAL VIA EMAIL",
            subtitle = "Email directly to ${TelemetryReportHelper.DEFAULT_SUPPORT_EMAIL}",
            accentColor = HyperCyan,
            badge = "EMAIL",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildFeatureRequestMarkdown(title, problem, proposedSolution, capabilities)
                TelemetryReportHelper.sendEmail(
                    context = context,
                    subject = if (title.isNotBlank()) "[REC Feature] $title" else "[REC Feature Proposal]",
                    body = markdown
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tertiary: Copy
        CyberActionButton(
            iconResId = R.drawable.ic_pixel_code,
            title = "COPY PROPOSAL TO CLIPBOARD",
            subtitle = "Copies formatted proposal text to paste anywhere",
            accentColor = TextSecondary,
            badge = "COPY",
            enabled = isFormValid,
            onClick = {
                val markdown = TelemetryReportHelper.buildFeatureRequestMarkdown(title, problem, proposedSolution, capabilities)
                TelemetryReportHelper.copyToClipboard(context, "Feature Proposal", markdown)
            }
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}
