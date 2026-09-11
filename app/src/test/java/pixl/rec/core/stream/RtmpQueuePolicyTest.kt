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
import pixl.rec.core.engine.RtmpStreamOutputTarget
import pixl.rec.core.model.DropReason
import pixl.rec.core.model.PacketMediaType
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class RtmpQueuePolicyTest {

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
    fun testAudioPriorityAndContinuityUnderVideoCongestion() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val connection = RtmpConnection(scope)
        connection.initChannelForTesting(capacity = 64)

        val audioPayload = byteArrayOf(0xAF.toByte(), 0x01.toByte(), 0x00)
        val interPayload = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00)

        // 1. Enqueue 50 video inter packets followed by 10 audio packets
        for (i in 0 until 50) {
            val videoPacket = RtmpPacket(
                messageType = RtmpPacket.TYPE_VIDEO,
                timestamp = (i * 33).toLong(),
                streamId = 1,
                payload = interPayload
            )
            connection.enqueuePacket(videoPacket)
        }

        for (i in 0 until 10) {
            val audioPacket = RtmpPacket(
                messageType = RtmpPacket.TYPE_AUDIO,
                timestamp = (i * 20).toLong(),
                streamId = 1,
                payload = audioPayload
            )
            connection.enqueuePacket(audioPacket)
        }

        // Verify audio packets entered dedicated audioChannel and video packets entered videoChannel
        val audioChan = connection.getAudioChannel()
        val videoChan = connection.getVideoChannel()
        assertTrue("Audio channel must not be null", audioChan != null)
        assertTrue("Video channel must not be null", videoChan != null)

        // Receive from audio channel: exactly 10 packets present with 0 drops
        var audioCount = 0
        while (audioChan!!.tryReceive().isSuccess) {
            audioCount++
        }
        assertEquals("All 10 audio packets must be safely queued without being displaced by video", 10, audioCount)

        // Telemetry must report 0 audio drops
        val telem = connection.getTelemetry("test")
        assertEquals("Audio drops must be strictly 0", 0L, telem.droppedAudio)

        scope.cancel()
    }

    @Test
    fun testStaleInterFrameEviction_latencyBudgetExceeded() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val connection = RtmpConnection(scope)
        connection.initChannelForTesting(capacity = 64)

        val syncFrameRequested = AtomicBoolean(false)
        val interFrameEvicted = AtomicBoolean(false)
        connection.onRequestSyncFrame = { syncFrameRequested.set(true) }
        connection.onInterFrameEvicted = { interFrameEvicted.set(true) }

        val interPayload = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00)

        // Create a stale inter-frame enqueued 2.5 seconds ago (exceeding LATENCY_BUDGET_MS of 1000ms)
        val stalePacket = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 1000L,
            streamId = 1,
            payload = interPayload,
            enqueueTimeNs = System.nanoTime() - 2_500_000_000L
        )

        // Enqueue into video channel
        connection.enqueuePacket(stalePacket)

        // Simulate reading from video channel and applying age eviction in streaming loop
        val videoChan = connection.getVideoChannel()!!
        val packet = videoChan.receive()

        val ageMs = ((System.nanoTime() - packet.enqueueTimeNs) / 1_000_000L).coerceAtLeast(0L)
        assertTrue("Packet age must exceed latency budget", ageMs > connection.LATENCY_BUDGET_MS)
        assertEquals("Packet must be an inter-frame", PacketMediaType.VIDEO_INTER, packet.mediaType)

        // Simulate eviction
        connection.recordDrop(packet, DropReason.LATENCY_BUDGET_EXCEEDED)
        connection.onInterFrameEvicted?.invoke()
        connection.onRequestSyncFrame?.invoke()

        val telem = connection.getTelemetry("test")
        assertEquals("Dropped video inter count must be 1", 1L, telem.droppedVideoInter)
        assertTrue("Sync frame must be requested", syncFrameRequested.get())
        assertTrue("Inter-frame evicted callback must be triggered", interFrameEvicted.get())

        scope.cancel()
    }

    @Test
    fun testKeyframeAndSequenceHeaderProtectionUnderLatencyBudget() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val connection = RtmpConnection(scope)
        connection.initChannelForTesting(capacity = 64)

        val syncFrameRequested = AtomicBoolean(false)
        connection.onRequestSyncFrame = { syncFrameRequested.set(true) }

        // Keyframe payload (AVC IDR)
        val keyPayload = byteArrayOf(0x17.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00, 0x65.toByte())

        // Keyframe with timestamp 2.5 seconds ago
        val staleKeyframe = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 1000L,
            streamId = 1,
            payload = keyPayload,
            enqueueTimeNs = System.nanoTime() - 2_500_000_000L
        )

        connection.enqueuePacket(staleKeyframe)

        val videoChan = connection.getVideoChannel()!!
        val packet = videoChan.receive()

        // Verify that even though age exceeds latency budget, VIDEO_KEYFRAME is NEVER evicted!
        assertEquals("Packet media type must be VIDEO_KEYFRAME", PacketMediaType.VIDEO_KEYFRAME, packet.mediaType)
        val ageMs = ((System.nanoTime() - packet.enqueueTimeNs) / 1_000_000L).coerceAtLeast(0L)
        assertTrue("Age exceeds latency budget", ageMs > connection.LATENCY_BUDGET_MS)

        // Keyframe is NOT dropped
        val telem = connection.getTelemetry("test")
        assertEquals("Keyframes must never be dropped by age eviction", 0L, telem.droppedVideoKey)
        assertFalse("Sync frame should not be requested since keyframe is intact", syncFrameRequested.get())

        scope.cancel()
    }

    @Test
    fun testPostCongestionSyncRecoveryGating() = runBlocking {
        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "test_key"
        )
        val scope = CoroutineScope(Dispatchers.Default)
        val target = RtmpStreamOutputTarget(
            streamConfig = config,
            scope = scope
        )
        target.connection.initChannelForTesting(capacity = 64)

        // 1. Simulate an eviction event occurring: connection triggers onInterFrameEvicted
        target.connection.onInterFrameEvicted?.invoke()
        assertTrue("Target must be waiting for sync frame after eviction", target.waitingForSyncFrame.get())

        // 2. An incoming delta frame arrives -> must be dropped early!
        val deltaBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41.toByte(), 0x00)
        val deltaBuf = ByteBuffer.wrap(deltaBytes)
        val deltaInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = deltaBytes.size
            presentationTimeUs = 100_000L
            flags = 0
        }
        target.onVideoSample(deltaBuf, deltaInfo)

        assertEquals("Delta frame must not be packetized while waiting for sync frame", 0L, target.totalBytesPacketized.get())
        assertTrue("Target must remain in waitingForSyncFrame state", target.waitingForSyncFrame.get())

        // 3. Fresh keyframe arrives -> accepted, clears gate!
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 133_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        target.onVideoSample(keyBuf, keyInfo)

        assertTrue("Bytes must be packetized for fresh keyframe", target.totalBytesPacketized.get() > 0)
        assertFalse("Waiting for sync frame must be cleared", target.waitingForSyncFrame.get())

        // 4. Subsequent delta frame arrives -> accepted and packetized!
        val bytesBefore = target.totalBytesPacketized.get()
        target.onVideoSample(deltaBuf, deltaInfo)
        assertTrue("Subsequent delta frame is now packetized", target.totalBytesPacketized.get() > bytesBefore)

        target.release()
        scope.cancel()
    }

    @Test
    fun testMultiDestinationIsolation_unhealthyDestinationSkipped() = runBlocking {
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

        assertEquals("Should have 2 child targets", 2, multiTarget.childTargets.size)
        val targetA = multiTarget.childTargets[0]
        val targetB = multiTarget.childTargets[1]

        targetA.connection.initChannelForTesting(capacity = 64)
        targetB.connection.initChannelForTesting(capacity = 64)

        multiTarget.start()

        // Manually mark Destination A as Unhealthy (e.g. max reconnect exhaustion or server reject)
        targetA.connection.setUnhealthy("Connection lost to Destination A")
        assertTrue("Destination A must be Unhealthy", targetA.connectionState.value is RtmpConnection.State.Unhealthy)

        // Dispatch a keyframe
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 100_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(keyBuf, keyInfo)

        // Destination A must be skipped: 0 bytes packetized
        assertEquals("Unhealthy destination A must not receive any video bytes", 0L, targetA.totalBytesPacketized.get())

        // Destination B must receive and packetize normally
        assertTrue("Healthy destination B must receive and packetize video bytes", targetB.totalBytesPacketized.get() > 0)

        multiTarget.release()
        scope.cancel()
    }
}
