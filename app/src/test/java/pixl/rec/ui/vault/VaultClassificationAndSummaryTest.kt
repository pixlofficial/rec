package pixl.rec.ui.vault

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.ui.vault.model.VaultMediaClassifier
import pixl.rec.ui.vault.model.VaultMediaItem
import pixl.rec.ui.vault.model.VaultMediaType
import pixl.rec.ui.vault.model.VaultSummary

class VaultClassificationAndSummaryTest {

    @Test
    fun testClassifierPrefixPrecedence() {
        assertEquals(VaultMediaType.RECORDING, VaultMediaClassifier.classify("REC_20260912_120000.mp4", "video/mp4"))
        assertEquals(VaultMediaType.STREAM, VaultMediaClassifier.classify("STREAM_20260912_130000.mp4", "video/mp4"))
        assertEquals(VaultMediaType.REPLAY, VaultMediaClassifier.classify("CLIP_20260912_140000.mp4", "video/mp4"))
        assertEquals(VaultMediaType.SCREENSHOT, VaultMediaClassifier.classify("SHOT_20260912_150000.png", "image/png"))
    }

    @Test
    fun testClassifierFallbackBehavior() {
        // Unknown REC video filename falls back to RECORDING to prevent media loss
        assertEquals(VaultMediaType.RECORDING, VaultMediaClassifier.classify("Gameplay_Session_1.mp4", "video/mp4"))
        assertEquals(VaultMediaType.RECORDING, VaultMediaClassifier.classify("Game_Stream_Highlight.mp4", "video/mp4"))

        // Any image MIME type falls back to SCREENSHOT
        assertEquals(VaultMediaType.SCREENSHOT, VaultMediaClassifier.classify("Capture_clutch.jpg", "image/jpeg"))
        assertEquals(VaultMediaType.SCREENSHOT, VaultMediaClassifier.classify("Random_image.png", "image/png"))
    }

    @Test
    fun testVaultMediaItemProperties() {
        val dummyUri = mockk<Uri>()
        val videoItem = VaultMediaItem(
            id = 1L,
            uri = dummyUri,
            displayName = "REC_test.mp4",
            mediaType = VaultMediaType.RECORDING,
            durationMs = 65_000L,
            sizeBytes = 10_000_000L
        )

        assertTrue(videoItem.isVideo)
        assertFalse(videoItem.isImage)
        assertEquals("01:05", videoItem.formattedDuration)

        val imageItem = VaultMediaItem(
            id = 2L,
            uri = dummyUri,
            displayName = "SHOT_test.png",
            mediaType = VaultMediaType.SCREENSHOT,
            sizeBytes = 500_000L
        )

        assertFalse(imageItem.isVideo)
        assertTrue(imageItem.isImage)
        assertEquals("", imageItem.formattedDuration)
    }

    @Test
    fun testVaultSummaryAggregation() {
        val dummyUri = mockk<Uri>()
        val items = listOf(
            VaultMediaItem(1L, dummyUri, "REC_1.mp4", VaultMediaType.RECORDING, sizeBytes = 100L),
            VaultMediaItem(2L, dummyUri, "REC_2.mp4", VaultMediaType.RECORDING, sizeBytes = 200L),
            VaultMediaItem(3L, dummyUri, "STREAM_1.mp4", VaultMediaType.STREAM, sizeBytes = 500L),
            VaultMediaItem(4L, dummyUri, "CLIP_1.mp4", VaultMediaType.REPLAY, sizeBytes = 50L),
            VaultMediaItem(5L, dummyUri, "SHOT_1.png", VaultMediaType.SCREENSHOT, sizeBytes = 25L)
        )

        val summary = VaultSummary.fromItems(items)

        assertEquals(5, summary.totalCount)
        assertEquals(875L, summary.totalBytes)

        assertEquals(2, summary.recordingCount)
        assertEquals(300L, summary.recordingBytes)

        assertEquals(1, summary.streamCount)
        assertEquals(500L, summary.streamBytes)

        assertEquals(1, summary.replayCount)
        assertEquals(50L, summary.replayBytes)

        assertEquals(1, summary.screenshotCount)
        assertEquals(25L, summary.screenshotBytes)

        assertEquals(2, summary.getCount(VaultMediaType.RECORDING))
        assertEquals(300L, summary.getBytes(VaultMediaType.RECORDING))
    }
}
