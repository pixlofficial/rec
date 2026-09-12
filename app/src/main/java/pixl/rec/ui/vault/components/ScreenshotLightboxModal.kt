package pixl.rec.ui.vault.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pixl.rec.ui.components.ActionButton
import pixl.rec.ui.components.ActionButtonVariant
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.ElectricPurple
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.vault.model.VaultMediaItem

/**
 * Full-screen modal lightbox for inspecting, sharing, and deleting screenshots.
 */
@Composable
fun ScreenshotLightboxModal(
    item: VaultMediaItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    // Load display-bounded image asynchronously to prevent OOM
    val fullBitmapState = produceState<Bitmap?>(initialValue = item.thumbnail, key1 = item.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val displayMetrics = context.resources.displayMetrics
                val maxReqWidth = displayMetrics.widthPixels
                val maxReqHeight = displayMetrics.heightPixels

                // 1. Measure image bounds first without allocating pixel memory
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                // 2. Compute power-of-two inSampleSize to bound memory to screen size
                var inSampleSize = 1
                if (boundsOptions.outHeight > maxReqHeight || boundsOptions.outWidth > maxReqWidth) {
                    val halfHeight = boundsOptions.outHeight / 2
                    val halfWidth = boundsOptions.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxReqHeight && (halfWidth / inSampleSize) >= maxReqWidth) {
                        inSampleSize *= 2
                    }
                }

                // 3. Decode sampled bitmap safely with OOM guard
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: item.thumbnail
            } catch (oom: OutOfMemoryError) {
                android.util.Log.w("ScreenshotLightbox", "OOM decoding screenshot, falling back to thumbnail", oom)
                item.thumbnail
            } catch (e: Exception) {
                item.thumbnail
            }
        }
    }

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
                .fillMaxSize()
                .background(ObsidianCanvas.copy(alpha = 0.96f))
        ) {
            // 1. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(ElectricPurple.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, ElectricPurple.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "📸 SCREENSHOT",
                                color = ElectricPurple,
                                fontSize = 9.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.displayName,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${item.formattedDate} • ${item.formattedSize}" +
                                if (item.formattedResolution.isNotEmpty()) " • ${item.formattedResolution}" else "",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceElevated, CircleShape)
                        .border(1.dp, BorderStark, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Centered High-Res Image View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = fullBitmapState.value
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderStark, RoundedCornerShape(10.dp))
                    )
                } else {
                    CircularProgressIndicator(
                        color = ElectricPurple,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // 3. Bottom Action Bar (Share & Delete)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "SHARE",
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    variant = ActionButtonVariant.PRIMARY,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = "DELETE",
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    variant = ActionButtonVariant.DANGER,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
