package pixl.rec.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import java.util.Locale

@Composable
fun SteppedVuMeter(
    label: String,
    dbLevel: Float, // -60f to 0f
    modifier: Modifier = Modifier,
    segmentCount: Int = 18,
    statusOverride: String? = null
) {
    val animatedDb by animateFloatAsState(
        targetValue = dbLevel.coerceIn(-60f, 0f),
        animationSpec = tween(durationMillis = 80),
        label = "VuDbAnimation"
    )

    // Normalize -60dB -> 0dB into 0.0 -> 1.0
    val normalized = ((animatedDb + 60f) / 60f).coerceIn(0f, 1f)
    val activeSegments = (normalized * segmentCount).toInt()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label.uppercase(),
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = pixl.rec.ui.theme.BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            val displayText = when {
                statusOverride != null && dbLevel <= -59f -> statusOverride
                dbLevel <= -59f -> "SILENT"
                else -> String.format(Locale.US, "%.1f dB", dbLevel)
            }
            val displayColor = when {
                statusOverride != null && dbLevel <= -59f -> TextMuted
                dbLevel > -3f -> HyperCrimson
                dbLevel > -12f -> CyberYellow
                else -> ToxicLime
            }
            Text(
                text = displayText,
                color = displayColor,
                fontSize = 13.sp,
                fontFamily = pixl.rec.ui.theme.BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Stepped LED Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(SurfaceElevated, RoundedCornerShape(3.dp))
                .border(1.dp, BorderStark, RoundedCornerShape(3.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until segmentCount) {
                val isActive = i < activeSegments
                val segmentFraction = i.toFloat() / segmentCount.toFloat()

                val segmentColor = when {
                    segmentFraction >= 0.85f -> HyperCrimson
                    segmentFraction >= 0.65f -> CyberYellow
                    else -> ToxicLime
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            if (isActive) segmentColor else Color(0xFF1E1E28),
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
