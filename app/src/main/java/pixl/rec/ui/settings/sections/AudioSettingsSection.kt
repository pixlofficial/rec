package pixl.rec.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.RecorderState
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.components.SteppedVuMeter
import pixl.rec.ui.dashboard.DashboardUiState
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AudioSettingsSection(
    uiState: DashboardUiState,
    isRecordingActive: Boolean,
    recorderState: RecorderState,
    viewModel: DashboardViewModel
) {
    val config = uiState.config
    val gameDb = if (recorderState is RecorderState.Recording) recorderState.gameAudioDb else -60f
    val micDb = if (recorderState is RecorderState.Recording) recorderState.micAudioDb else -60f

    val isInternalAudioActive = config.audioSource == AudioSource.INTERNAL_AND_MIC || config.audioSource == AudioSource.INTERNAL_ONLY
    val isMicAudioActive = config.audioSource == AudioSource.INTERNAL_AND_MIC || config.audioSource == AudioSource.MIC_ONLY

    // 1. Audio Routing & Studio Controls
    SectionCard(title = "AUDIO STUDIO", titleTag = "48 KHZ STEREO AAC") {
        SlidingPillSelector(
            items = listOf(
                AudioSource.INTERNAL_AND_MIC,
                AudioSource.INTERNAL_ONLY,
                AudioSource.MIC_ONLY,
                AudioSource.MUTE
            ),
            selectedItem = config.audioSource,
            itemLabel = {
                when (it) {
                    AudioSource.INTERNAL_AND_MIC -> "Both"
                    AudioSource.INTERNAL_ONLY -> "Internal"
                    AudioSource.MIC_ONLY -> "Mic"
                    AudioSource.MUTE -> "Mute"
                }
            },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateAudioSource(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Internal Volume Slider (0 - 100%)
        val internalPercent = (config.internalAudioGain * 100).roundToInt()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INTERNAL VOLUME",
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isInternalAudioActive) TextPrimary else TextMuted
            )
            Text(
                text = "$internalPercent%",
                fontFamily = BitcountPropSingle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isInternalAudioActive) HyperCrimson else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = config.internalAudioGain,
            onValueChange = { viewModel.updateInternalAudioGain(it) },
            valueRange = 0f..1f,
            enabled = !isRecordingActive && isInternalAudioActive,
            colors = SliderDefaults.colors(
                thumbColor = HyperCrimson,
                activeTrackColor = HyperCrimson,
                inactiveTrackColor = BorderStark,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = BorderStark.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mic Gain Slider (0 - 200%, with dB boost calculation)
        val micPercent = (config.micGain * 100).roundToInt()
        val boostDb = if (config.micGain > 1.0f) {
            " (+${String.format(Locale.US, "%.1f", 20 * kotlin.math.log10(config.micGain))} dB Boost)"
        } else if (config.micGain < 1.0f && config.micGain > 0f) {
            " (${String.format(Locale.US, "%.1f", 20 * kotlin.math.log10(config.micGain))} dB)"
        } else ""

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIC GAIN",
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMicAudioActive) TextPrimary else TextMuted
            )
            Text(
                text = "$micPercent%$boostDb",
                fontFamily = BitcountPropSingle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMicAudioActive) HyperCyan else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = config.micGain,
            onValueChange = { viewModel.updateMicGain(it) },
            valueRange = 0f..2f,
            enabled = !isRecordingActive && isMicAudioActive,
            colors = SliderDefaults.colors(
                thumbColor = HyperCyan,
                activeTrackColor = HyperCyan,
                inactiveTrackColor = BorderStark,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = BorderStark.copy(alpha = 0.3f)
            )
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Real-time VU Levels
    SectionCard(title = "LIVE AUDIO VU VISUALIZER", titleTag = "48 KHZ STEREO") {
        SteppedVuMeter(
            label = "Internal Audio Loopback",
            dbLevel = gameDb,
            statusOverride = if (!isRecordingActive) "STANDBY" else if (!isInternalAudioActive) "MUTED" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        SteppedVuMeter(
            label = "Microphone Audio (Stereo)",
            dbLevel = micDb,
            statusOverride = if (!isMicAudioActive) "MUTED" else null
        )
    }
}
