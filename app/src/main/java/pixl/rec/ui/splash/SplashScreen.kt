package pixl.rec.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pixl.rec.R
import pixl.rec.ui.theme.ObsidianCanvas
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // 1. Splash Duration Timer (1.4 seconds)
    LaunchedEffect(Unit) {
        delay(1400L)
        onSplashFinished()
    }

    // 2. Subtle Cyber Breathing Animation on Logo
    val infiniteTransition = rememberInfiniteTransition(label = "SplashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas),
        contentAlignment = Alignment.Center
    ) {
        // Center: Red Pixel Recording Disc Logo
        Image(
            painter = painterResource(id = R.drawable.ic_logo_core),
            contentDescription = "REC Logo Core",
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
        )

        // Bottom: Brand Identity Vector (Matches native cold-start splash 1:1)
        Image(
            painter = painterResource(id = R.drawable.ic_splash_branding),
            contentDescription = "REC by PixL",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .size(width = 200.dp, height = 50.dp)
        )
    }
}
