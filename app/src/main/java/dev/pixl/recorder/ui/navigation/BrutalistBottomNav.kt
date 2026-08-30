package dev.pixl.recorder.ui.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.core.model.RecorderState
import dev.pixl.recorder.ui.theme.BitcountPropSingle
import dev.pixl.recorder.ui.theme.BorderHighlight
import dev.pixl.recorder.ui.theme.BorderStark
import dev.pixl.recorder.ui.theme.HyperCrimson
import dev.pixl.recorder.ui.theme.ObsidianCanvas
import dev.pixl.recorder.ui.theme.SurfaceElevated
import dev.pixl.recorder.ui.theme.TextInverse
import dev.pixl.recorder.ui.theme.TextMuted
import dev.pixl.recorder.ui.theme.TextPrimary
import dev.pixl.recorder.ui.theme.TextSecondary

@Composable
fun BrutalistBottomNav(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    recorderState: RecorderState,
    onRecordAction: () -> Unit
) {
    val isRecording = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused

    // Infinite breathing pulse for center shutter when live recording
    val pulseTransition = rememberInfiniteTransition(label = "ShutterPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianCanvas)
    ) {
        // Main Nav Dock Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .background(SurfaceElevated)
                .border(width = 1.dp, color = BorderStark, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Left Tabs: DASHBOARD & VAULT
            NavTabItem(
                tab = NavigationTab.DASHBOARD,
                isSelected = currentTab == NavigationTab.DASHBOARD,
                onClick = { onTabSelected(NavigationTab.DASHBOARD) },
                modifier = Modifier.weight(1f)
            )

            NavTabItem(
                tab = NavigationTab.VAULT,
                isSelected = currentTab == NavigationTab.VAULT,
                onClick = { onTabSelected(NavigationTab.VAULT) },
                modifier = Modifier.weight(1f)
            )

            // Center Spacer for elevated floating Record Shutter
            Spacer(modifier = Modifier.weight(1.2f))

            // Right Tabs: SETTINGS & MORE
            NavTabItem(
                tab = NavigationTab.SETTINGS,
                isSelected = currentTab == NavigationTab.SETTINGS,
                onClick = { onTabSelected(NavigationTab.SETTINGS) },
                modifier = Modifier.weight(1f)
            )

            NavTabItem(
                tab = NavigationTab.MORE,
                isSelected = currentTab == NavigationTab.MORE,
                onClick = { onTabSelected(NavigationTab.MORE) },
                modifier = Modifier.weight(1f)
            )
        }

        // Center Floating Shutter Trigger Button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(if (isRecording) pulseScale else 1f)
                    .background(
                        color = if (isRecording) HyperCrimson else TextPrimary,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.5.dp,
                        color = if (isRecording) BorderHighlight else HyperCrimson,
                        shape = CircleShape
                    )
                    .clickable(onClick = onRecordAction),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Recording",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = dev.pixl.recorder.R.drawable.ic_pixel_record),
                        contentDescription = "Start Recording",
                        tint = HyperCrimson,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavigationTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = tab.iconRes),
            contentDescription = tab.label,
            tint = if (isSelected) TextPrimary else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = tab.label,
            color = if (isSelected) TextPrimary else TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(HyperCrimson, CircleShape)
            )
        }
    }
}
