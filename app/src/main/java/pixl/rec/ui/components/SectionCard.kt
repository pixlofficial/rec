package pixl.rec.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.ShadowSolid
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextPrimary

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleTag: String? = null,
    tagColor: Color = Color.White,
    tagTextColor: Color = TextInverse,
    borderColor: Color = BorderStark,
    containerColor: Color = SurfaceCard,
    shape: Shape = RoundedCornerShape(12.dp),
    shadowOffset: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Drop shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(ShadowSolid, shape)
                .border(2.dp, BorderStark, shape)
        )

        // Front Card Surface
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, shape)
                .border(2.dp, borderColor, shape)
                .padding(16.dp)
        ) {
            if (title != null || titleTag != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (title != null) {
                        Text(
                            text = title.uppercase(),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = pixl.rec.ui.theme.BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (titleTag != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(tagColor, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = titleTag.uppercase(),
                                color = tagTextColor,
                                fontSize = 12.sp,
                                fontFamily = pixl.rec.ui.theme.BitcountPropSingle,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            content()
        }
    }
}
