package pixl.rec.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

private const val KOFI_URL = "https://ko-fi.com/pixlofficial"
private const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/pixlofficial"

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Header
        LegalPageHeader(
            iconResId = R.drawable.ic_pixel_heart,
            iconTint = HyperCrimson,
            title = "SUPPORT PIXL",
            subtitle = "INDEPENDENT SOFTWARE STUDIO",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Studio Mission Preamble
        PreambleCard(
            text = "Independent studio crafting high-performance, zero-bloat, privacy-first software. REC is 100% free, offline, and ad-free. Your support directly funds independent development across all PixL projects."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Section: SPONSORSHIP CHANNELS
        Text(
            text = "SPONSORSHIP CHANNELS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BitcountPropSingle,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Ko-fi Card
        SponsorCard(
            iconResId = R.drawable.ic_pixel_coffee,
            iconTint = CyberYellow,
            title = "SUPPORT ON KO-FI",
            subtitle = "One-time tips or monthly membership via Card, Apple Pay, Google Pay, or PayPal.",
            badgeText = "KO-FI",
            badgeColor = CyberYellow,
            actionLabel = "BUY PIXL A COFFEE",
            url = KOFI_URL,
            onClick = { openUrl(context, KOFI_URL) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // GitHub Sponsors Card
        SponsorCard(
            iconResId = R.drawable.ic_pixel_github,
            iconTint = HyperCrimson,
            title = "GITHUB SPONSORS",
            subtitle = "Sponsor PixL's open-source projects and developer ecosystem directly through GitHub.",
            badgeText = "GITHUB",
            badgeColor = HyperCrimson,
            actionLabel = "SPONSOR ON GITHUB",
            url = GITHUB_SPONSORS_URL,
            onClick = { openUrl(context, GITHUB_SPONSORS_URL) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Section: PRIVACY & NO PAYWALLS
        NumberedSectionHeader(
            title = "1. ZERO PAYWALLS & COMPLETE INTEGRITY",
            iconResId = R.drawable.ic_pixel_shield,
            iconTint = ToxicLime
        )
        Spacer(modifier = Modifier.height(8.dp))

        ClauseCard {
            ClauseItem(
                title = "Always 100% Free",
                description = "Sponsoring is completely optional. REC will never lock features behind paywalls, introduce subscriptions, or restrict recording resolution or framerate."
            )
            Spacer(modifier = Modifier.height(10.dp))
            ClauseItem(
                title = "100% Offline & Private",
                description = "REC does not contain analytics SDKs, payment SDKs, or advertising trackers. Sponsoring links operate purely via external browser intents."
            )
            Spacer(modifier = Modifier.height(10.dp))
            ClauseItem(
                title = "Direct Impact",
                description = "Every contribution directly funds device testbeds (120Hz & 144Hz devices), SoC optimizations, and upcoming PixL software."
            )
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun SponsorCard(
    @DrawableRes iconResId: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    actionLabel: String,
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderStark, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0D12))
                        .border(1.dp, iconTint.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BitcountPropSingle,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BitcountPropSingle
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF09090D))
                    .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = actionLabel,
                        color = iconTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BitcountPropSingle,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_external),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}
