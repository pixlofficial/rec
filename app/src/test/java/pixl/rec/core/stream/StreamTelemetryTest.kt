package pixl.rec.core.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.engine.RtmpStreamOutputTarget
import pixl.rec.core.model.DropReason
import pixl.rec.core.model.PacketMediaType
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.model.WriteLatencyMetrics
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamTelemetryTest {

    private var serverSocket: ServerSocket? = null
    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
        runCatching { serverSocket?.close() }
    }

    private fun serverHandshake(input: BufferedInputStream, output: BufferedOutputStream) {
        val c0c1 = ByteArray(1537)
        var totalRead = 0
        while (totalRead < 1537) {
            val r = input.read(c0c1, totalRead, 1537 - totalRead)
            if (r < 0) throw IllegalStateException("EOF during C0/C1 read")
            totalRead += r
        }

        val s0s1 = ByteArray(1537)
        s0s1[0] = 3
        output.write(s0s1)

        val s2 = ByteArray(1536)
        System.arraycopy(c0c1, 1, s2, 0, 1536)
        output.write(s2)
        output.flush()

        val c2 = ByteArray(1536)
        totalRead = 0
        while (totalRead < 1536) {
            val r = input.read(c2, totalRead, 1536 - totalRead)
            if (r < 0) throw IllegalStateException("EOF during C2 read")
            totalRead += r
        }
    }

    @Test
    fun testPacketMediaType_classification() {
        // 1. AVC Keyframe: Tag byte 0x17 (Keyframe = 1, CodecId = 7 for AVC), AVCPacketType = 1 (NALU)
        val avcKey = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 0,
            streamId = 1,
            payload = byteArrayOf(0x17.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00, 0x65.toByte())
        )
        assertEquals(PacketMediaType.VIDEO_KEYFRAME, avcKey.mediaType)

        // 2. AVC Inter-frame: Tag byte 0x27 (Inter = 2, CodecId = 7 for AVC), AVCPacketType = 1 (NALU)
        val avcInter = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 33,
            streamId = 1,
            payload = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00, 0x41.toByte())
        )
        assertEquals(PacketMediaType.VIDEO_INTER, avcInter.mediaType)

        // 3. AVC Sequence Header: Tag byte 0x17, AVCPacketType = 0 (Sequence Header)
        val avcSeq = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 0,
            streamId = 1,
            payload = byteArrayOf(0x17.toByte(), 0x00.toByte(), 0x00, 0x00, 0x00, 0x01)
        )
        assertEquals(PacketMediaType.VIDEO_SEQUENCE_HEADER, avcSeq.mediaType)

        // 4. Enhanced RTMP HEVC Keyframe: isExHeader = 1 (0x80), frameType = 1 (Keyframe, 0x10), packetType = 1 (CodedFrames, 0x01) -> 0x91
        val hevcKey = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 0L,
            streamId = 1,
            payload = byteArrayOf(
                (0x80 or (1 shl 4) or 0x01).toByte(),
                'h'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte(),
                0x01, 0x00, 0x00, 0x00
            )
        )
        assertEquals(PacketMediaType.VIDEO_KEYFRAME, hevcKey.mediaType)

        // 5. Enhanced RTMP HEVC Inter-frame: isExHeader = 1 (0x80), frameType = 2 (Inter, 0x20), packetType = 1 (CodedFrames, 0x01) -> 0xA1
        val hevcInter = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 33L,
            streamId = 1,
            payload = byteArrayOf(
                (0x80 or (2 shl 4) or 0x01).toByte(),
                'h'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte(),
                0x01, 0x00, 0x00, 0x00
            )
        )
        assertEquals(PacketMediaType.VIDEO_INTER, hevcInter.mediaType)

        // 6. Enhanced RTMP HEVC Sequence Header: isExHeader = 1 (0x80), frameType = 1 (Keyframe, 0x10), packetType = 0 (SequenceStart, 0x00) -> 0x90
        val hevcSeq = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 0L,
            streamId = 1,
            payload = byteArrayOf(
                (0x80 or (1 shl 4) or 0x00).toByte(),
                'h'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte(),
                0x01, 0x00, 0x00, 0x00
            )
        )
        assertEquals(PacketMediaType.VIDEO_SEQUENCE_HEADER, hevcSeq.mediaType)

        // 6. Audio AAC Frame: Tag byte 0xAF (AAC soundFormat = 10), AACPacketType = 1 (Raw AAC frame)
        val aacFrame = RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            timestamp = 20,
            streamId = 1,
            payload = byteArrayOf(0xAF.toByte(), 0x01.toByte(), 0x21, 0x10)
        )
        assertEquals(PacketMediaType.AUDIO, aacFrame.mediaType)

        // 7. Audio AAC Sequence Header: Tag byte 0xAF, AACPacketType = 0 (AAC sequence header)
        val aacSeq = RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            timestamp = 0,
            streamId = 1,
            payload = byteArrayOf(0xAF.toByte(), 0x00.toByte(), 0x12, 0x10)
        )
        assertEquals(PacketMediaType.AUDIO_SEQUENCE_HEADER, aacSeq.mediaType)

        // 8. Metadata and Command
        val metadata = RtmpPacket(
            messageType = RtmpPacket.TYPE_DATA_AMF0,
            timestamp = 0L,
            streamId = 1,
            payload = byteArrayOf(0x02, 0x00, 0x0A, 'o'.code.toByte())
        )
        assertEquals(PacketMediaType.METADATA_COMMAND, metadata.mediaType)

        val command = RtmpPacket(
            messageType = RtmpPacket.TYPE_COMMAND_AMF0,
            timestamp = 0,
            streamId = 0,
            payload = byteArrayOf(0x02, 0x00, 0x07, 'c'.code.toByte())
        )
        assertEquals(PacketMediaType.METADATA_COMMAND, command.mediaType)
    }

    @Test
    fun testWriteLatencyMetrics_percentiles() {
        val metrics = WriteLatencyMetrics(capacity = 128)

        // Empty metrics should return (0, 0, 0)
        val (p50Empty, p95Empty, p99Empty) = metrics.getPercentiles()
        assertEquals(0L, p50Empty)
        assertEquals(0L, p95Empty)
        assertEquals(0L, p99Empty)

        // Insert 100 entries: 1ms through 100ms
        for (i in 1L..100L) {
            metrics.record(i)
        }

        val (p50, p95, p99) = metrics.getPercentiles()
        assertEquals(51L, p50)
        assertEquals(96L, p95)
        assertEquals(100L, p99)

        // Insert 100 more entries to verify circular wrap-around past 128 capacity
        for (i in 101L..200L) {
            metrics.record(i)
        }

        val (p50Wrapped, p95Wrapped, p99Wrapped) = metrics.getPercentiles()
        assertTrue("p50 wrapped should be >= 100", p50Wrapped >= 100L)
        assertTrue("p95 wrapped should be >= 180", p95Wrapped >= 180L)
        assertTrue("p99 wrapped should be >= 195", p99Wrapped >= 195L)
    }

    @Test
    fun testRtmpPacket_enqueueTimeTracking() {
        val before = System.nanoTime()
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 100,
            streamId = 1,
            payload = byteArrayOf(0x17.toByte(), 0x01.toByte(), 0x00)
        )
        val after = System.nanoTime()

        assertTrue(packet.enqueueTimeNs in before..after)
    }

    @Test
    fun testRtmpConnection_onUndeliveredElement_typedDrops() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val connection = RtmpConnection(scope)
        // With Phase 2 separated channels, test onUndeliveredElement on both audio & video queues
        connection.initChannelForTesting(audioCapacity = 50, videoCapacity = 50)

        val interPayload = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00)
        val audioPayload = byteArrayOf(0xAF.toByte(), 0x01.toByte(), 0x00)

        // Enqueue 80 audio packets into capacity-50 audio channel -> exactly 30 displaced
        for (i in 0 until 80) {
            val audioPacket = RtmpPacket(
                messageType = RtmpPacket.TYPE_AUDIO,
                timestamp = (i * 20).toLong(),
                streamId = 1,
                payload = audioPayload
            )
            connection.enqueuePacket(audioPacket)
        }

        // Enqueue 70 video inter packets into capacity-50 video channel -> exactly 20 displaced
        for (i in 0 until 70) {
            val videoPacket = RtmpPacket(
                messageType = RtmpPacket.TYPE_VIDEO,
                timestamp = (i * 33).toLong(),
                streamId = 1,
                payload = interPayload
            )
            connection.enqueuePacket(videoPacket)
        }

        val telem = connection.getTelemetry("test-dest")
        assertEquals(50L, connection.totalFramesDropped.get())
        assertEquals(30L, telem.droppedAudio)
        assertEquals(20L, telem.droppedVideoInter)
        assertEquals(50L, telem.totalDroppedPackets)

        scope.cancel()
    }

    @Test
    fun testSyntheticChokedNetworkTrace() = runBlocking {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        val serverAccepted = CountDownLatch(1)
        val readyLatch = CountDownLatch(1)
        val packetsReadCount = CountDownLatch(5)

        testScope?.launch {
            val client = server.accept()
            val bufIn = BufferedInputStream(client.getInputStream())
            val bufOut = BufferedOutputStream(client.getOutputStream())

            // Handshake
            serverHandshake(bufIn, bufOut)

            // Read packets and simulate choked socket with artificial delay
            val serverChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
            var running = true
            while (running) {
                try {
                    val packet = serverChunkStream.readPacket(bufIn)
                    when (packet.messageType) {
                        RtmpPacket.TYPE_SET_CHUNK_SIZE -> {
                            val newSize = java.nio.ByteBuffer.wrap(packet.payload).int
                            serverChunkStream.inChunkSize = newSize
                        }
                        RtmpPacket.TYPE_COMMAND_AMF0 -> {
                            val cmd = Amf0.parseCommand(packet.payload)
                            val commandName = cmd?.commandName
                            if (commandName == "connect") {
                                val result = Amf0.Writer()
                                    .writeString("_result")
                                    .writeNumber(1.0)
                                    .writeObject(mapOf("fmsVer" to "FMS/3,5,7,7009"))
                                    .writeObject(mapOf("level" to "status", "code" to "NetConnection.Connect.Success"))
                                    .toByteArray()
                                serverChunkStream.writePacket(
                                    RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0L, 0, result, RtmpPacket.CSID_COMMAND),
                                    bufOut
                                )
                                bufOut.flush()
                            } else if (commandName == "createStream") {
                                val result = Amf0.Writer()
                                    .writeString("_result")
                                    .writeNumber(4.0)
                                    .writeNull()
                                    .writeNumber(1.0)
                                    .toByteArray()
                                serverChunkStream.writePacket(
                                    RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0L, 0, result, RtmpPacket.CSID_COMMAND),
                                    bufOut
                                )
                                bufOut.flush()
                            }
                        }
                        RtmpPacket.TYPE_VIDEO -> {
                            packetsReadCount.countDown()
                            // Simulate network latency / choked socket buffer
                            Thread.sleep(50)
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
            client.close()
        }

        val scope = CoroutineScope(Dispatchers.IO)
        val connection = RtmpConnection(scope)

        scope.launch {
            connection.connectAndPublish(
                endpointUrl = "rtmp://127.0.0.1:$port/live",
                streamKey = "test_key",
                onReady = { readyLatch.countDown() }
            )
        }

        assertTrue("Connection onReady should be reached", readyLatch.await(5, TimeUnit.SECONDS))

        // Pump 5 packets with simulated network latency
        val videoPayload = byteArrayOf(0x17.toByte(), 0x01.toByte(), 0x00, 0x00, 0x00, 0x65.toByte())
        for (i in 0 until 5) {
            connection.enqueuePacket(
                RtmpPacket(
                    messageType = RtmpPacket.TYPE_VIDEO,
                    timestamp = (i * 33).toLong(),
                    streamId = 1,
                    payload = videoPayload,
                    csid = RtmpPacket.CSID_VIDEO
                )
            )
        }

        assertTrue("Server should read choked packets", packetsReadCount.await(5, TimeUnit.SECONDS))

        // Query telemetry
        val telem = connection.getTelemetry("test-destination")
        assertTrue("Bytes transmitted should be recorded", telem.bytesTransmitted > 0)
        assertTrue("Write latency p50 should reflect socket writes", telem.writeLatencyP50Ms >= 0)
        assertEquals(0L, telem.totalDroppedPackets)

        scope.cancel()
    }

    @Test
    fun testPresentationTimestampDrift() {
        val config = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "test_key"
        )
        val scope = CoroutineScope(Dispatchers.Default)
        val target = RtmpStreamOutputTarget(
            streamConfig = config,
            scope = scope
        )

        // Inject video PTS = 2,000,000 us (2.000s), audio PTS = 1,850,000 us (1.850s)
        // Difference = 150,000 us = 150 ms drift
        target.lastVideoPtsUs.set(2_000_000L)
        target.lastAudioPtsUs.set(1_850_000L)
        target.totalBytesEncoded.set(500_000L)
        target.totalBytesPacketized.set(505_000L)
        target.totalVideoFrames.set(60L)
        target.totalPacketizationTimeNs.set(60_000_000L) // 1ms avg packetization

        val sessionTelemetry = target.getTelemetry()
        assertEquals(150L, sessionTelemetry.audioVideoPtsDriftMs)
        assertEquals(500_000L, sessionTelemetry.totalBytesEncoded)
        assertEquals(505_000L, sessionTelemetry.totalBytesPacketized)
        assertEquals(1000L, sessionTelemetry.avgPacketizationTimeUs)
        assertEquals(60.0f, sessionTelemetry.encoderOutputFps, 0.01f)
        assertNotNull(sessionTelemetry.destinations["YOUTUBE"])

        scope.cancel()
    }
}
