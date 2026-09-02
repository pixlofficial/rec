package pixl.rec.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.StrokeStyle
import pixl.rec.ui.components.HudNodeSurface
import pixl.rec.ui.components.rememberHudIconAnimation
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import kotlin.math.roundToInt

enum class StudioTab(val title: String) {
    STANDBY("STANDBY"),
    RECORDING("RECORDING")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudStudioScreen(
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableStateOf(StudioTab.STANDBY) }

    val currentConfig = if (activeTab == StudioTab.STANDBY) {
        uiState.config.standbyHudConfig
    } else {
        uiState.config.recordingHudConfig
    }

    fun updateCurrentConfig(updated: HudStyleConfig) {
        if (activeTab == StudioTab.STANDBY) {
            viewModel.updateStandbyHudConfig(updated)
        } else {
            viewModel.updateRecordingHudConfig(updated)
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "HUD THEME STUDIO",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "STANDBY & RECORDING LAB",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle
                        )
                    }
                }

                // Reset Action for Active Tab
                Box(
                    modifier = Modifier
                        .background(SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                        .clickable { updateCurrentConfig(HudStyleConfig()) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = CyberYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET",
                            color = CyberYellow,
                            fontSize = 10.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Dual Tabs: STANDBY vs RECORDING
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StudioTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    val tabColor = if (tab == StudioTab.STANDBY) HyperCyan else HyperCrimson

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) tabColor.copy(alpha = 0.18f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) tabColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isSelected) tabColor else TextMuted, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tab.title,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Hero Live Preview Showcase Card (Visual showcase)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val iconAnim = rememberHudIconAnimation(
                        animation = currentConfig.animation,
                        baseOpacity = currentConfig.iconOpacity
                    )
                    HudNodeSurface(
                        config = currentConfig
                    ) {
                        val iconSize = currentConfig.iconSizeDp.dp
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_record),
                            contentDescription = null,
                            tint = HyperCrimson,
                            modifier = Modifier
                                .size(iconSize)
                                .scale(iconAnim.scale)
                                .alpha(iconAnim.alpha)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (activeTab == StudioTab.STANDBY) "LIVE STANDBY PREVIEW" else "LIVE RECORDING (00:00:00)",
                        color = if (activeTab == StudioTab.STANDBY) HyperCyan else CyberYellow,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Edge Snapping Behavior Card (Configurable independently for Standby and Recording)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_gesture),
                            contentDescription = null,
                            tint = CyberYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EDGE SNAPPING BEHAVIOR",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = currentConfig.snapBehavior.displayName.uppercase(),
                        color = CyberYellow,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Controls magnetic physics when dragging or dropping in ${activeTab.title.lowercase()} mode.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudSnapBehavior.entries.forEach { option ->
                        val isSelected = currentConfig.snapBehavior == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BorderHighlight else BorderStark,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { updateCurrentConfig(currentConfig.copy(snapBehavior = option)) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (option) {
                                    HudSnapBehavior.PROXIMITY_SNAP -> "PROXIMITY"
                                    HudSnapBehavior.ALWAYS_SNAP_EDGE -> "ALWAYS"
                                    HudSnapBehavior.FREE_FLOAT -> "FREE"
                                },
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Card: ICON (Configures Icon Size and Independent Icon Opacity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "ICON",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Configure record shutter icon size & opacity",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subheading & Slider: ICON SIZE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ICON SIZE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentConfig.iconSizeDp} DP",
                        color = CyberYellow,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.iconSizeDp.toFloat().coerceIn(15f, 44f),
                    onValueChange = { updateCurrentConfig(currentConfig.copy(iconSizeDp = it.roundToInt())) },
                    valueRange = 15f..44f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subheading & Slider: ICON OPACITY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ICON OPACITY",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(currentConfig.iconOpacity * 100).roundToInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.iconOpacity,
                    onValueChange = { updateCurrentConfig(currentConfig.copy(iconOpacity = it)) },
                    valueRange = 0.20f..1.0f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subheading & Selector: ANIMATION STYLE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ANIMATION STYLE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentConfig.animation.displayName.uppercase(),
                        color = CyberYellow,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HudAnimation.entries.forEach { anim ->
                        val isSelected = currentConfig.animation == anim
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BorderHighlight else BorderStark,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { updateCurrentConfig(currentConfig.copy(animation = anim)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = anim.displayName.uppercase(),
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Card: BACKGROUND & SILHOUETTE (Collapsible with Shape, Container Size & Background Opacity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, if (currentConfig.hasBackground) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BACKGROUND & SILHOUETTE",
                            color = if (currentConfig.hasBackground) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentConfig.hasBackground) "Using custom shape background silhouette" else "Background disabled (showing pure REC icon)",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle
                        )
                    }

                    Switch(
                        checked = currentConfig.hasBackground,
                        onCheckedChange = { updateCurrentConfig(currentConfig.copy(hasBackground = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextInverse,
                            checkedTrackColor = TextPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = ObsidianCanvas
                        )
                    )
                }

                // Collapsible Shape, Container Size & Background Opacity
                AnimatedVisibility(
                    visible = currentConfig.hasBackground,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SHAPE GEOMETRY",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HudShape.entries.forEach { shape ->
                                val isSelected = currentConfig.shape == shape
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) BorderHighlight else BorderStark,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { updateCurrentConfig(currentConfig.copy(shape = shape)) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shape.displayName.uppercase(),
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = BitcountPropSingle,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Background Container Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONTAINER SIZE",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${currentConfig.nodeSizeDp} DP",
                                color = CyberYellow,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = currentConfig.nodeSizeDp.toFloat().coerceIn(36f, 56f),
                            onValueChange = { updateCurrentConfig(currentConfig.copy(nodeSizeDp = it.roundToInt())) },
                            valueRange = 36f..56f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = BorderStark
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Background Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BACKGROUND OPACITY",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(currentConfig.backgroundOpacity * 100).roundToInt()}%",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = currentConfig.backgroundOpacity,
                            onValueChange = { updateCurrentConfig(currentConfig.copy(backgroundOpacity = it)) },
                            valueRange = 0.10f..1.0f,
                            steps = 17,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = BorderStark
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Card: BORDER STROKE (Collapsible with Stroke Weight, Pattern & Stroke Opacity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, if (currentConfig.hasStroke) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BORDER STROKE",
                            color = if (currentConfig.hasStroke) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentConfig.hasStroke) "Outline border & cyber path effects active" else "Stroke disabled",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle
                        )
                    }

                    Switch(
                        checked = currentConfig.hasStroke,
                        onCheckedChange = { updateCurrentConfig(currentConfig.copy(hasStroke = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextInverse,
                            checkedTrackColor = TextPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = ObsidianCanvas
                        )
                    )
                }

                // Collapsible Stroke Weight, Pattern & Stroke Opacity
                AnimatedVisibility(
                    visible = currentConfig.hasStroke,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Stroke Weight Slider (0.5 DP to 3.5 DP)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STROKE WEIGHT",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format("%.1f DP", currentConfig.strokeWidthDp),
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = currentConfig.strokeWidthDp,
                            onValueChange = { updateCurrentConfig(currentConfig.copy(strokeWidthDp = ((it * 10f).roundToInt() / 10f))) },
                            valueRange = 0.5f..3.5f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = BorderStark
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stroke Style Selector (Solid, Circular Dots, Dashed)
                        Text(
                            text = "STROKE PATTERN",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StrokeStyle.entries.forEach { style ->
                                val isSelected = currentConfig.strokeStyle == style
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) BorderHighlight else BorderStark,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { updateCurrentConfig(currentConfig.copy(strokeStyle = style)) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = style.displayName.uppercase(),
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = BitcountPropSingle,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stroke Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STROKE OPACITY",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(currentConfig.strokeOpacity * 100).roundToInt()}%",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = currentConfig.strokeOpacity,
                            onValueChange = { updateCurrentConfig(currentConfig.copy(strokeOpacity = it)) },
                            valueRange = 0.10f..1.0f,
                            steps = 17,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = BorderStark
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
