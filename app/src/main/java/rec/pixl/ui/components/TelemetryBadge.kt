package rec.pixl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rec.pixl.ui.theme.BitcountPropSingle
import rec.pixl.ui.theme.BorderHighlight
import rec.pixl.ui.theme.BorderStark
import rec.pixl.ui.theme.SurfaceElevated
import rec.pixl.ui.theme.TextInverse
import rec.pixl.ui.theme.ToxicLime

@Composable
fun TelemetryBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = ToxicLime,
    isHighlighted: Boolean = false
) {
    Box(
        modifier = modifier
            .background(if (isHighlighted) accentColor else SurfaceElevated, RoundedCornerShape(6.dp))
            .border(1.5.dp, if (isHighlighted) BorderHighlight else BorderStark, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                color = if (isHighlighted) TextInverse.copy(alpha = 0.75f) else rec.pixl.ui.theme.TextSecondary,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value.uppercase(),
                color = if (isHighlighted) TextInverse else accentColor,
                fontSize = 14.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
    }
}
