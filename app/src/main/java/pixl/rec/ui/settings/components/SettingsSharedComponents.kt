package pixl.rec.ui.settings.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pixl.rec.R
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.SurfaceRaised
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

@Composable
fun SettingsTag(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDisabledClick: (() -> Unit)? = null
) {
    val bg = if (isSelected) TextPrimary else SurfaceElevated
    val border = if (isSelected) HyperCrimson else if (enabled) BorderStark else BorderStark.copy(alpha = 0.4f)
    val textColor = if (isSelected) TextInverse else if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable {
                if (enabled) onClick() else onDisabledClick?.invoke()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 13.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SettingsSwitch(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    pillTraceProgress: Float = 0f,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.5.dp, if (checked) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (checked) BorderHighlight else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (checked) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.wrapContentSize()
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextInverse,
                    checkedTrackColor = TextPrimary,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianCanvas
                )
            )

            if (pillTraceProgress > 0f) {
                Canvas(
                    modifier = Modifier.size(52.dp, 32.dp)
                ) {
                    val strokeWidth = 2.5.dp.toPx()
                    val cornerRadius = 16.dp.toPx()

                    val inset = strokeWidth / 2f
                    val rectWidth = size.width - strokeWidth
                    val rectHeight = size.height - strokeWidth

                    val fullPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = inset,
                                top = inset,
                                right = inset + rectWidth,
                                bottom = inset + rectHeight,
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        )
                    }

                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(fullPath, true)
                    val totalLength = pathMeasure.length

                    if (pillTraceProgress <= 1.0f) {
                        val headDist = pillTraceProgress * totalLength
                        val tailLength = totalLength * 0.45f
                        val startDist = (headDist - tailLength).coerceAtLeast(0f)

                        val segmentPath = Path()
                        pathMeasure.getSegment(startDist, headDist, segmentPath, true)

                        // Outer soft crimson neon glow
                        drawPath(
                            path = segmentPath,
                            color = HyperCrimson.copy(alpha = 0.5f),
                            style = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round)
                        )
                        // Inner intense bright red tracer core
                        drawPath(
                            path = segmentPath,
                            color = Color(0xFFFF5252),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    } else {
                        // Dissolving glow across entire border
                        val fadeAlpha = (1f - (pillTraceProgress - 1.0f) / 0.3f).coerceIn(0f, 1f)
                        drawPath(
                            path = fullPath,
                            color = HyperCrimson.copy(alpha = 0.45f * fadeAlpha),
                            style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = fullPath,
                            color = Color(0xFFFF5252).copy(alpha = 0.9f * fadeAlpha),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoTuneBitrateDialog(
    newTierTag: String,
    newWidth: Int,
    newHeight: Int,
    currentBitrateMbps: Int,
    recommendedBitrateMbps: Int,
    onDismiss: () -> Unit,
    onKeep: (doNotAskAgain: Boolean) -> Unit,
    onApply: (doNotAskAgain: Boolean) -> Unit
) {
    var doNotAskAgain by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(12.dp))
                .border(2.dp, BorderStark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_lightning),
                        contentDescription = null,
                        tint = HyperCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTO-TUNE BITRATE?",
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = HyperCrimson,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Switched to $newTierTag ($newWidth × $newHeight).\nRecommended bitrate is $recommendedBitrateMbps Mbps for balanced quality and file size.",
                    fontFamily = BitcountPropSingle,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Checkbox: Don't ask again
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { doNotAskAgain = !doNotAskAgain }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(if (doNotAskAgain) HyperCrimson else SurfaceElevated, RoundedCornerShape(4.dp))
                            .border(1.5.dp, if (doNotAskAgain) HyperCrimson else BorderStark, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (doNotAskAgain) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_check),
                                contentDescription = "Checked",
                                tint = ObsidianCanvas,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Don't ask again",
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .background(SurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                            .clickable { onKeep(doNotAskAgain) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "KEEP ${currentBitrateMbps}M",
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(TextPrimary, RoundedCornerShape(6.dp))
                            .border(1.5.dp, HyperCrimson, RoundedCornerShape(6.dp))
                            .clickable { onApply(doNotAskAgain) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "APPLY ${recommendedBitrateMbps}M",
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextInverse
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GamingOptimizationDialog(
    onDismiss: (doNotAskAgain: Boolean) -> Unit,
    onConfigure: (doNotAskAgain: Boolean) -> Unit
) {
    var doNotAskAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(doNotAskAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss(doNotAskAgain) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(SurfaceRaised, RoundedCornerShape(12.dp))
                    .border(1.5.dp, ToxicLime, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_gamepad),
                            contentDescription = null,
                            tint = ToxicLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMART GAME OPTIMIZATION",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ToxicLime,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Would you like REC to automatically detect when games launch, lock landscape canvas, enforce a 60 FPS cap on 120Hz panels, and minimize background overhead?",
                        fontFamily = BitcountPropSingle,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Checkbox: Do not show again
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { doNotAskAgain = !doNotAskAgain }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(if (doNotAskAgain) ToxicLime else SurfaceElevated, RoundedCornerShape(4.dp))
                                .border(1.5.dp, if (doNotAskAgain) ToxicLime else BorderStark, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (doNotAskAgain) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_check),
                                    contentDescription = "Checked",
                                    tint = ObsidianCanvas,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Do not show again",
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .background(SurfaceElevated, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                            .clickable { onDismiss(doNotAskAgain) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "LATER",
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .background(TextPrimary, RoundedCornerShape(6.dp))
                                .border(1.5.dp, ToxicLime, RoundedCornerShape(6.dp))
                                .clickable { onConfigure(doNotAskAgain) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "YES, CONFIGURE",
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextInverse
                            )
                        }
                    }
                }
            }
        }
    }
}
