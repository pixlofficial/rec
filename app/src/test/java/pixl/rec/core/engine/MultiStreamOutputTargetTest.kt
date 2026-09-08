package pixl.rec.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import java.nio.ByteBuffer

class MultiStreamOutputTargetTest {

    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
    }

    @Test
    fun testMultiStreamConfig_activeDestinationsAndCodecCompatibility() {
        // Setup destinations: YouTube (supports HEVC) + Twitch (requires AVC)
        val destinations = listOf(
            StreamDestination(
                platform = StreamPlatform.YOUTUBE,
                streamKey = "yt_key_123",
                enabled = true
            ),
            StreamDestination(
                platform = StreamPlatform.TWITCH,
                streamKey = "twitch_key_456",
                enabled = true
            ),
            StreamDestination(
                platform = StreamPlatform.KICK,
                streamKey = "",
                enabled = false // Not enabled
            )
        )

        val config = StreamConfig(
            destinations = destinations,
            useEnhancedHevc = true,
            videoBitrate = 8_000_000
        )

        // 2 active configured destinations
        assertEquals(2, config.activeDestinations.size)
        assertTrue(config.isConfigured)

        // Since Twitch is active, effectiveSupportsHevc MUST be false to ensure AVC broadcast
        assertFalse(config.effectiveSupportsHevc)

        // Total required bitrate: 2 * (8Mbps + 256kbps)
        val expectedBps = 2 * (8_000_000L + 256_000L)
        assertEquals(expectedBps, config.totalRequiredBitrateBps)
    }

    @Test
    fun testMultiStreamConfig_allHevcDestinations() {
        val destinations = listOf(
            StreamDestination(
                platform = StreamPlatform.YOUTUBE,
                streamKey = "yt_key_123",
                enabled = true
            ),
            StreamDestination(
                platform = StreamPlatform.CUSTOM,
                customEndpointUrl = "rtmp://my.custom.hevc.server/live",
                streamKey = "custom_key",
                enabled = true
            )
        )

        val config = StreamConfig(
            destinations = destinations,
            useEnhancedHevc = true
        )

        assertEquals(2, config.activeDestinations.size)
        // Both YouTube and Custom support HEVC, so effectiveSupportsHevc is true
        assertTrue(config.effectiveSupportsHevc)
    }

    @Test
    fun testMultiStreamOutputTarget_initializationAndChildTargets() {
        val destinations = listOf(
            StreamDestination(
                platform = StreamPlatform.YOUTUBE,
                streamKey = "yt_live_key",
                enabled = true
            ),
            StreamDestination(
                platform = StreamPlatform.TWITCH,
                streamKey = "twitch_live_key",
                enabled = true
            )
        )

        val config = StreamConfig(
            destinations = destinations,
            enableAbr = true,
            videoBitrate = 6_000_000
        )

        val multiTarget = MultiStreamOutputTarget(
            destinations = config.activeDestinations,
            streamConfig = config,
            scope = testScope!!
        )

        assertEquals(2, multiTarget.childTargets.size)
        assertNotNull(multiTarget.compositeUplinkHealth)
        assertEquals(UplinkHealth.CLEAN, multiTarget.compositeUplinkHealth.value)

        // Verify child targets have their respective configs
        assertEquals(StreamPlatform.YOUTUBE, multiTarget.childTargets[0].streamConfig.platform)
        assertEquals("yt_live_key", multiTarget.childTargets[0].streamConfig.streamKey)

        assertEquals(StreamPlatform.TWITCH, multiTarget.childTargets[1].streamConfig.platform)
        assertEquals("twitch_live_key", multiTarget.childTargets[1].streamConfig.streamKey)

        multiTarget.release()
    }

    @Test
    fun testBufferDuplication_zeroCopyIndependence() {
        val originalBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x1E)
        val originalBuffer = ByteBuffer.allocate(originalBytes.size).apply {
            put(originalBytes)
            flip()
        }

        // Duplicate the buffer for 2 child sinks
        val dup1 = originalBuffer.duplicate()
        val dup2 = originalBuffer.duplicate()

        // Modify dup1 position
        dup1.position(4)
        val dup1Bytes = ByteArray(dup1.remaining())
        dup1.get(dup1Bytes)

        // Verify original and dup2 positions were unaffected
        assertEquals(0, originalBuffer.position())
        assertEquals(0, dup2.position())
        assertEquals(originalBytes.size, dup2.remaining())

        // Verify dup1 read correct payload
        assertEquals(4, dup1Bytes.size)
        assertEquals(0x67.toByte(), dup1Bytes[0])
    }
}
