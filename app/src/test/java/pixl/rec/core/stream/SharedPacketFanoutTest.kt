package pixl.rec.core.stream

import android.media.MediaCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.engine.MultiStreamOutputTarget
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class SharedPacketFanoutTest {

    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
    }

    @Test
    fun testSharedRtmpPacket_lifecycleAndReclamation() {
        var reclaimed = false
        val dummyPacket = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 1000L,
            payload = byteArrayOf(0x17, 0x01, 0x00, 0x00, 0x00)
        )

        val shared = SharedRtmpPacket(
            packet = dummyPacket,
            initialReferences = 3,
            onReclaimed = { reclaimed = true }
        )

        assertEquals("Initial references must be 3", 3, shared.activeReferences)
        assertFalse("Packet must not be released initially", shared.isReleased)
        assertFalse("Reclaimed callback must not have fired", reclaimed)

        // 1. First destination completes transmission
        val refAfter1 = shared.release()
        assertEquals(2, refAfter1)
        assertEquals(2, shared.activeReferences)
        assertFalse(reclaimed)

        // 2. Retain (e.g. queue re-try or archival)
        shared.retain()
        assertEquals(3, shared.activeReferences)

        // 3. Release again down to 1
        shared.release()
        shared.release()
        assertEquals(1, shared.activeReferences)
        assertFalse(reclaimed)

        // 4. Final destination completes -> triggers reclamation
        val finalRemaining = shared.release()
        assertEquals(0, finalRemaining)
        assertTrue("Packet must be marked as released", shared.isReleased)
        assertTrue("Reclaimed callback must fire when refCount reaches 0", reclaimed)
    }

    @Test
    fun testMultiStreamOutputTarget_singlePacketizationFanout() = runBlocking {
        val destA = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_a",
            enabled = true
        )
        val destB = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_b",
            enabled = true
        )
        val destC = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_c",
            enabled = true
        )

        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            streamKey = "test_key",
            enableAbr = false
        )
        val scope = CoroutineScope(Dispatchers.Default)

        val multiTarget = MultiStreamOutputTarget(
            destinations = listOf(destA, destB, destC),
            streamConfig = config,
            scope = scope
        )

        assertEquals("Should configure 3 child targets", 3, multiTarget.childTargets.size)
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
        }

        multiTarget.start()

        // Dispatch a single AVC Keyframe
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00, 0x12, 0x34)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 100_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(keyBuf, keyInfo)

        // All 3 child targets must have received and packetized the exact same byte count
        val bytesA = multiTarget.childTargets[0].totalBytesPacketized.get()
        val bytesB = multiTarget.childTargets[1].totalBytesPacketized.get()
        val bytesC = multiTarget.childTargets[2].totalBytesPacketized.get()

        assertTrue("Target A must have packetized bytes", bytesA > 0)
        assertEquals("Target B must have identical packetized bytes as Target A", bytesA, bytesB)
        assertEquals("Target C must have identical packetized bytes as Target A", bytesA, bytesC)

        // Dispatch an Audio sample
        val audioBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val audioBuf = ByteBuffer.wrap(audioBytes)
        val audioInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = audioBytes.size
            presentationTimeUs = 100_000L
            flags = 0
        }

        multiTarget.onAudioSample(audioBuf, audioInfo)

        for (target in multiTarget.childTargets) {
            val audioChan = target.connection.getAudioChannel()!!
            assertTrue("Audio packet must be enqueued in each destination's audioChannel", audioChan.tryReceive().isSuccess)
        }

        multiTarget.release()
        scope.cancel()
    }

    @Test
    fun testMultiStreamOutputTarget_selectiveSyncGatingAcrossDestinations() = runBlocking {
        val destA = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_a",
            enabled = true
        )
        val destB = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_b",
            enabled = true
        )

        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            streamKey = "test_key",
            enableAbr = false
        )
        val scope = CoroutineScope(Dispatchers.Default)

        val multiTarget = MultiStreamOutputTarget(
            destinations = listOf(destA, destB),
            streamConfig = config,
            scope = scope
        )

        val targetA = multiTarget.childTargets[0]
        val targetB = multiTarget.childTargets[1]

        targetA.connection.initChannelForTesting(capacity = 64)
        targetB.connection.initChannelForTesting(capacity = 64)

        multiTarget.start()

        // Simulate Target A having experienced an inter-frame eviction (waiting for sync frame)
        targetA.waitingForSyncFrame.set(true)
        // Target B is completely normal (NOT waiting for sync frame)
        targetB.waitingForSyncFrame.set(false)

        // 1. Dispatch a Delta (inter) frame
        val deltaBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41.toByte(), 0x00)
        val deltaBuf = ByteBuffer.wrap(deltaBytes)
        val deltaInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = deltaBytes.size
            presentationTimeUs = 100_000L
            flags = 0 // Inter-frame!
        }

        multiTarget.onVideoSample(deltaBuf, deltaInfo)

        // Target A must have dropped the delta frame (0 bytes packetized)
        assertEquals("Target A must drop delta frames while waiting for sync frame", 0L, targetA.totalBytesPacketized.get())
        assertTrue("Target A must remain in waiting state", targetA.waitingForSyncFrame.get())

        // Target B must have packetized the delta frame without interruption!
        assertTrue("Target B must packetize delta frame normally", targetB.totalBytesPacketized.get() > 0)

        // 2. Dispatch a Keyframe
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 133_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(keyBuf, keyInfo)

        // Target A must have accepted the keyframe and cleared waiting state
        assertTrue("Target A must packetize the keyframe", targetA.totalBytesPacketized.get() > 0)
        assertFalse("Target A waitingForSyncFrame must be cleared", targetA.waitingForSyncFrame.get())

        multiTarget.release()
        scope.cancel()
    }

    @Test
    fun testMultiStreamOutputTarget_allUnhealthySkipsPacketization() = runBlocking {
        val destA = StreamDestination(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_a",
            enabled = true
        )

        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            streamKey = "test_key",
            enableAbr = false
        )
        val scope = CoroutineScope(Dispatchers.Default)

        val multiTarget = MultiStreamOutputTarget(
            destinations = listOf(destA),
            streamConfig = config,
            scope = scope
        )

        val targetA = multiTarget.childTargets[0]
        targetA.connection.initChannelForTesting(capacity = 64)

        multiTarget.start()

        // Set target A to Unhealthy
        targetA.connection.setUnhealthy("Terminal failure")

        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 100_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(keyBuf, keyInfo)

        // Target A must receive 0 bytes
        assertEquals("Unhealthy destination must receive 0 bytes", 0L, targetA.totalBytesPacketized.get())

        multiTarget.release()
        scope.cancel()
    }
}
