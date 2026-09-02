package pixl.rec.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.core.model.RecorderState
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary

/**
 * Cyberpunk Angular /---\ Chamfered Canopy Dock Shape
 */
class CrestedDockShape(
    private val cornerRadius: Dp = 16.dp,
    private val crestHeight: Dp = 20.dp,
    private val crestHalfWidth: Dp = 44.dp,
    private val slopeWidth: Dp = 22.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val h = with(density) { crestHeight.toPx() }
        val halfW = with(density) { crestHalfWidth.toPx() }
        val slope = with(density) { slopeWidth.toPx() }

        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val baselineY = h

        val path = Path().apply {
            // 1. Start below top-left corner
            moveTo(0f, baselineY + r)
            quadraticTo(0f, baselineY, r, baselineY)

            // 2. Left baseline to left chamfer slope start
            val leftSlopeStartX = (centerX - halfW - slope).coerceAtLeast(r)
            lineTo(leftSlopeStartX, baselineY)

            // 3. Diagonal 45° slope rising to canopy crest (/)
            lineTo(centerX - halfW, 0f)

            // 4. Flat top canopy bridge (---)
            lineTo(centerX + halfW, 0f)

            // 5. Diagonal 45° slope descending to baseline (\)
            val rightSlopeEndX = (centerX + halfW + slope).coerceAtMost(width - r)
            lineTo(rightSlopeEndX, baselineY)

            // 6. Right baseline to top-right corner
            lineTo(width - r, baselineY)
            quadraticTo(width, baselineY, width, baselineY + r)

            // 7. Right edge down to bottom
            lineTo(width, height)

            // 8. Bottom edge
            lineTo(0f, height)

            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    isRecording: Boolean,
    onRecordAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navShape = remember { CrestedDockShape() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        // Upper layer: Chamfered Canopy Dock + Centered Floating Shutter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            // Chamfered Canopy Dock Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(navShape)
                    .background(SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = BorderStark,
                        shape = navShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 25.dp, bottom = 4.dp, start = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
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

                    // Shutter gap reservation under the canopy
                    Spacer(modifier = Modifier.weight(1.3f))

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
            }

            // Shutter Button positioned with equal gap from top of chamfer and phone's navbar
            FloatingRecordShutter(
                isRecording = isRecording,
                onRecordAction = onRecordAction,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Solid Phone Navigation Bar Area (ObsidianCanvas)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(pixl.rec.ui.theme.ObsidianCanvas)
        )
    }
}

/**
 * Independent Floating Action Shutter button that overlays the BottomNavBar.
 */
@Composable
fun FloatingRecordShutter(
    isRecording: Boolean,
    onRecordAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Gentle, subtle breathing pulse for center shutter when live recording (idle = static 1f)
    val pulseScale = if (isRecording) {
        val pulseTransition = rememberInfiniteTransition(label = "ShutterPulse")
        val scale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseScale"
        )
        scale
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onRecordAction
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                id = if (isRecording) pixl.rec.R.drawable.ic_pixel_stop else pixl.rec.R.drawable.ic_pixel_record
            ),
            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
            tint = HyperCrimson,
            modifier = Modifier
                .size(if (isRecording) 56.dp else 52.dp)
                .scale(pulseScale)
        )
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
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = tab.iconRes),
            contentDescription = tab.label,
            tint = if (isSelected) TextPrimary else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = if (isSelected) TextPrimary else TextMuted,
            fontSize = 10.5.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(3.5.dp)
                    .background(HyperCrimson, CircleShape)
            )
        }
    }
}
