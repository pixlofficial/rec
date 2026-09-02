package pixl.rec.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import pixl.rec.BuildConfig
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.VideoCodec

object TelemetryReportHelper {

    const val DEFAULT_SUPPORT_EMAIL = "support@pixl.dev"
    const val GITHUB_REPO_URL = "https://github.com/pixlofficial/rec"

    /**
     * Formats a clean, readable diagnostic string of the local device and video subsystem.
     */
    fun getFormattedDiagnostics(capabilities: DeviceCapabilities?): String {
        val display = capabilities?.display
        val hevcInfo = capabilities?.codecs?.get(VideoCodec.HEVC)
        val avcInfo = capabilities?.codecs?.get(VideoCodec.AVC)

        val hevcStatus = when {
            hevcInfo == null -> "Unsupported"
            hevcInfo.isHardwareAccelerated -> "Hardware ASIC (${hevcInfo.codecName})"
            else -> "Software Fallback (${hevcInfo.codecName})"
        }

        val avcStatus = when {
            avcInfo == null -> "Unsupported"
            avcInfo.isHardwareAccelerated -> "Hardware ASIC (${avcInfo.codecName})"
            else -> "Software Fallback (${avcInfo.codecName})"
        }

        val displayHz = display?.currentRefreshRate?.toInt() ?: 60
        val displayRes = if (display != null) "${display.physicalWidth}x${display.physicalHeight}" else "Unknown"

        return buildString {
            appendLine("### System Diagnostics")
            appendLine("- **App Version:** REC v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("- **Device:** ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("- **OS:** Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.ID})")
            appendLine("- **SoC Hardware:** ${Build.HARDWARE} (${Build.BOARD})")
            appendLine("- **Display:** ${displayRes} @ ${displayHz}Hz (Max FPS: ${capabilities?.maxHardwareFps ?: 60})")
            appendLine("- **HEVC Encoder:** $hevcStatus")
            appendLine("- **AVC Encoder:** $avcStatus")
            appendLine("- **Internal Audio:** ${if (capabilities?.hasInternalAudioCapture == true) "Supported (API 29+)" else "Unavailable"}")
        }
    }

    /**
     * Builds a full Markdown bug report including user description and telemetry.
     */
    fun buildBugReportMarkdown(
        title: String,
        whatHappened: String,
        stepsToReproduce: String,
        capabilities: DeviceCapabilities?
    ): String {
        return buildString {
            appendLine("## Bug Report: ${title.ifBlank { "Untitled Glitch" }}")
            appendLine()
            appendLine("### Description")
            appendLine(whatHappened.ifBlank { "No description provided." })
            appendLine()
            appendLine("### Steps to Reproduce")
            appendLine(stepsToReproduce.ifBlank { "1. Open REC\n2. Start recording" })
            appendLine()
            append(getFormattedDiagnostics(capabilities))
        }
    }

    /**
     * Builds a full Markdown feature request proposal including telemetry.
     */
    fun buildFeatureRequestMarkdown(
        title: String,
        problem: String,
        proposedSolution: String,
        capabilities: DeviceCapabilities?
    ): String {
        return buildString {
            appendLine("## Feature Request: ${title.ifBlank { "Untitled Proposal" }}")
            appendLine()
            appendLine("### Problem / Motivation")
            appendLine(problem.ifBlank { "Describe why this feature would be valuable." })
            appendLine()
            appendLine("### Proposed Solution / Workflow")
            appendLine(proposedSolution.ifBlank { "Describe how you envision this feature working." })
            appendLine()
            append(getFormattedDiagnostics(capabilities))
        }
    }

    /**
     * Constructs a pre-filled GitHub Issue URL.
     */
    fun createGitHubIssueUrl(
        title: String,
        body: String,
        labels: String = "bug"
    ): String {
        val base = "$GITHUB_REPO_URL/issues/new"
        val encTitle = Uri.encode(title.trim())
        val encBody = Uri.encode(body.trim())
        val encLabels = Uri.encode(labels)
        return "$base?title=$encTitle&body=$encBody&labels=$encLabels"
    }

    /**
     * Dispatches an Intent to open the GitHub issue creation page in the user's browser.
     */
    fun openGitHubIssue(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser. Link copied to clipboard.", Toast.LENGTH_SHORT).show()
            copyToClipboard(context, "GitHub Issue URL", url)
        }
    }

    /**
     * Dispatches an email via Intent.ACTION_SENDTO.
     */
    fun sendEmail(
        context: Context,
        subject: String,
        body: String,
        toEmail: String = DEFAULT_SUPPORT_EMAIL
    ) {
        try {
            val mailUri = Uri.parse("mailto:$toEmail?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
            val intent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No email app found. Report copied to clipboard.", Toast.LENGTH_LONG).show()
            copyToClipboard(context, "Bug Report", body)
        }
    }

    /**
     * Copies plain text to the Android clipboard.
     */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
