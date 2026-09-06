package pixl.rec.ui.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import pixl.rec.R
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.RecordingConfig
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

/**
 * Operating mode of the setup modal.
 */
enum class SetupModalMode {
    WELCOME,        // Shown on first app launch (hardware summary, privacy pledge, permissions checklist)
    FIRST_RECORD    // Shown if permissions are still missing when the user taps Record
}

data class SetupPermissionsState(
    val isOverlayGranted: Boolean,
    val isAudioGranted: Boolean,
    val isNotificationGranted: Boolean
) {
    val areAllGranted: Boolean get() = isOverlayGranted && isAudioGranted && isNotificationGranted
    val isRecordingReady: Boolean get() = isAudioGranted
}

fun checkCurrentPermissions(context: Context): SetupPermissionsState {
    val isOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    val isAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val isNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    return SetupPermissionsState(
        isOverlayGranted = isOverlay,
        isAudioGranted = isAudio,
        isNotificationGranted = isNotification
    )
}

@Composable
fun SetupModal(
    mode: SetupModalMode,
    config: RecordingConfig? = null,
    capabilities: DeviceCapabilities?,
    onRequestOverlayPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permState by remember { mutableStateOf(checkCurrentPermissions(context)) }

    // Re-check permissions whenever returning to app from external settings dialogs
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permState = checkCurrentPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Determine which permissions are required based on active config
    val isAudioRequired = if (mode == SetupModalMode.FIRST_RECORD) {
        config?.audioSource?.hasAudio ?: true
    } else {
        true
    }

    val isOverlayRequired = if (mode == SetupModalMode.FIRST_RECORD) {
        config?.showFloatingPill ?: true
    } else {
        true
    }

    val isNotificationRequired = if (mode == SetupModalMode.FIRST_RECORD) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ((config?.standbyNotification == true) || (config?.showFloatingPill == false))
    } else {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    // Retain initial missing checklist items for FIRST_RECORD mode so they don't abruptly vanish upon grant
    val initiallyMissingAudio = remember { isAudioRequired && !permState.isAudioGranted }
    val initiallyMissingOverlay = remember { isOverlayRequired && !permState.isOverlayGranted }
    val initiallyMissingNotification = remember { isNotificationRequired && !permState.isNotificationGranted }

    val canProceed = (!isAudioRequired || permState.isAudioGranted) &&
                     (!isOverlayRequired || permState.isOverlayGranted) &&
                     (!isNotificationRequired || permState.isNotificationGranted)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .then(
                    if (mode == SetupModalMode.WELCOME) {
                        Modifier.fillMaxHeight(0.88f)
                    } else {
                        Modifier.wrapContentHeight()
                    }
                )
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianCanvas)
                .border(1.dp, BorderStark, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (mode == SetupModalMode.WELCOME) {
                            Modifier.fillMaxHeight()
                        } else {
                            Modifier.wrapContentHeight()
                        }
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // 1. Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HyperCrimson.copy(alpha = 0.15f))
                                .border(1.dp, HyperCrimson, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_record),
                                contentDescription = null,
                                tint = HyperCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (mode == SetupModalMode.WELCOME) "WELCOME TO REC" else "PERMISSIONS NEEDED",
                                fontFamily = BitcountPropSingle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (mode == SetupModalMode.WELCOME) "Screen recording setup" else "Required to start recording",
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Close icon (top-right)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = HyperCrimson),
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_close),
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Privacy Pledge Card (in Welcome Mode)
                if (mode == SetupModalMode.WELCOME) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .border(1.dp, HyperCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_shield),
                                contentDescription = null,
                                tint = HyperCyan,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "100% OFFLINE & PRIVATE",
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HyperCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Your recordings and microphone audio stay strictly on this device. No accounts, no cloud sync, and zero trackers.",
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 3. Hardware Auto-Detection (in Welcome Mode)
                if (mode == SetupModalMode.WELCOME && capabilities != null) {
                    val disp = capabilities.display
                    val resText = "${disp.physicalWidth} × ${disp.physicalHeight}"
                    val fpsText = "${disp.currentRefreshRate.toInt()} Hz Display"
                    val codecText = if (capabilities.isHevcHardwareSupported) "Hardware HEVC" else "Hardware AVC"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderStark, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DEVICE DISPLAY & HARDWARE",
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ToxicLime.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "OPTIMIZED",
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ToxicLime
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "$resText • $fpsText • $codecText",
                                fontFamily = BitcountPropSingle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Default settings are automatically tailored to match your screen resolution and refresh rate.",
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Description for FIRST_RECORD mode
                if (mode == SetupModalMode.FIRST_RECORD) {
                    Text(
                        text = "To record your screen and capture sound without interruptions, please grant the permissions below:",
                        fontFamily = BitcountPropSingle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 4. Permissions Checklist Section
                Text(
                    text = "PERMISSIONS CHECKLIST",
                    fontFamily = BitcountPropSingle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Item 1: Floating Controls (Overlay)
                if (mode == SetupModalMode.WELCOME || initiallyMissingOverlay) {
                    PermissionCard(
                        iconRes = R.drawable.ic_pixel_eye,
                        title = "Floating Controls",
                        description = "Displays the floating bubble and recording HUD so you can pause, resume, or stop while inside apps and games.",
                        isGranted = permState.isOverlayGranted,
                        onGrant = onRequestOverlayPermission
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Item 2: Audio Recording
                if (mode == SetupModalMode.WELCOME || initiallyMissingAudio) {
                    PermissionCard(
                        iconRes = R.drawable.ic_pixel_audio,
                        title = "Audio Recording",
                        description = "Required to capture your microphone commentary alongside internal device audio.",
                        isGranted = permState.isAudioGranted,
                        onGrant = onRequestAudioPermission
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Item 3: Notification Bar Controls
                if (mode == SetupModalMode.WELCOME || initiallyMissingNotification) {
                    PermissionCard(
                        iconRes = R.drawable.ic_pixel_system,
                        title = "Notification Controls",
                        description = "Shows recording controls and status in your notification shade for instant one-tap access.",
                        isGranted = permState.isNotificationGranted,
                        onGrant = onRequestNotificationPermission
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Action Buttons (Footer)
                if (mode == SetupModalMode.WELCOME) {
                    // Primary: Get Started
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HyperCrimson)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = Color.White),
                                onClick = onGetStarted
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (permState.areAllGranted) "GET STARTED →" else "CONTINUE WITH REC →",
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary: Skip for now
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSkip
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Skip for now",
                            fontFamily = BitcountPropSingle,
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // FIRST_RECORD Mode: Continue to Record or Cancel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canProceed) HyperCrimson else SurfaceCard)
                            .border(
                                1.dp,
                                if (canProceed) HyperCrimson else BorderStark,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                enabled = canProceed,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = if (canProceed) ripple(bounded = true, color = Color.White) else null,
                                onClick = onGetStarted
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CONTINUE TO RECORD →",
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canProceed) TextPrimary else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = BitcountPropSingle,
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(
                1.dp,
                if (isGranted) ToxicLime.copy(alpha = 0.35f) else BorderStark,
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = if (isGranted) ToxicLime else HyperCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontFamily = BitcountPropSingle,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Grant / Granted button
                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ToxicLime.copy(alpha = 0.15f))
                            .border(1.dp, ToxicLime.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✓ GRANTED",
                            fontFamily = BitcountPropSingle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ToxicLime
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HyperCrimson.copy(alpha = 0.15f))
                            .border(1.dp, HyperCrimson, RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = HyperCrimson),
                                onClick = onGrant
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "GRANT",
                            fontFamily = BitcountPropSingle,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = HyperCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
