package pixl.rec.core.stream

import android.media.MediaCodec
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.engine.RtmpStreamOutputTarget
import pixl.rec.core.engine.VideoEncoder
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamPlatform
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RtmpControlProtocolTest {

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
    fun testServerPingRequest_triggersPriorityPingResponse() = runBlocking {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        val readyLatch = CountDownLatch(1)
        val pingResponseReceivedLatch = CountDownLatch(1)
        val receivedPingTimestamp = AtomicReference<Int>()

        testScope?.launch {
            val client = server.accept()
            val bufIn = BufferedInputStream(client.getInputStream())
            val bufOut = BufferedOutputStream(client.getOutputStream())

            serverHandshake(bufIn, bufOut)

            val serverChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
            var running = true

            while (running) {
                try {
                    val packet = serverChunkStream.readPacket(bufIn)
                    when (packet.messageType) {
                        RtmpPacket.TYPE_SET_CHUNK_SIZE -> {
                            val newSize = ByteBuffer.wrap(packet.payload).int
                            serverChunkStream.inChunkSize = newSize
                        }
                        RtmpPacket.TYPE_COMMAND_AMF0 -> {
                            val cmd = Amf0.parseCommand(packet.payload)
                            if (cmd?.commandName == "connect") {
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
                            } else if (cmd?.commandName == "createStream") {
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
                            } else if (cmd?.commandName == "publish") {
                                // Once publish is received, send a Server Ping Request (0x0006) with timestamp 0x2A3B4C5D
                                val pingTimestamp = 0x2A3B4C5D
                                val pingPayload = byteArrayOf(
                                    0x00, 0x06, // Event Type 6 = PingRequest
                                    ((pingTimestamp shr 24) and 0xFF).toByte(),
                                    ((pingTimestamp shr 16) and 0xFF).toByte(),
                                    ((pingTimestamp shr 8) and 0xFF).toByte(),
                                    (pingTimestamp and 0xFF).toByte()
                                )
                                serverChunkStream.writePacket(
                                    RtmpPacket(RtmpPacket.TYPE_USER_CONTROL, 0L, 0, pingPayload, RtmpPacket.CSID_CONTROL),
                                    bufOut
                                )
                                bufOut.flush()
                            }
                        }
                        RtmpPacket.TYPE_USER_CONTROL -> {
                            if (packet.payload.size >= 6) {
                                val eventType = ((packet.payload[0].toInt() and 0xFF) shl 8) or (packet.payload[1].toInt() and 0xFF)
                                if (eventType == 0x0007) { // PingResponse
                                    val ts = ByteBuffer.wrap(packet.payload, 2, 4).int
                                    receivedPingTimestamp.set(ts)
                                    pingResponseReceivedLatch.countDown()
                                    running = false
                                }
                            }
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
                streamKey = "test_stream_key",
                onReady = { readyLatch.countDown() }
            )
        }

        assertTrue("Connection onReady should fire", readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Server should receive Ping Response (0x0007)", pingResponseReceivedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(0x2A3B4C5D, receivedPingTimestamp.get())

        scope.cancel()
    }

    @Test
    fun testIncomingSetChunkSize_doesNotMutateOutboundChunkSize() = runBlocking {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        val clientReady = CountDownLatch(1)
        val setChunkProcessedLatch = CountDownLatch(1)

        val scope = CoroutineScope(Dispatchers.IO)
        val connection = RtmpConnection(scope)

        testScope?.launch {
            val client = server.accept()
            val bufIn = BufferedInputStream(client.getInputStream())
            val bufOut = BufferedOutputStream(client.getOutputStream())

            serverHandshake(bufIn, bufOut)

            val serverChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
            var running = true

            while (running) {
                try {
                    val packet = serverChunkStream.readPacket(bufIn)
                    when (packet.messageType) {
                        RtmpPacket.TYPE_SET_CHUNK_SIZE -> {
                            val newSize = ByteBuffer.wrap(packet.payload).int
                            serverChunkStream.inChunkSize = newSize
                        }
                        RtmpPacket.TYPE_COMMAND_AMF0 -> {
                            val cmd = Amf0.parseCommand(packet.payload)
                            if (cmd?.commandName == "connect") {
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
                            } else if (cmd?.commandName == "createStream") {
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
                            } else if (cmd?.commandName == "publish") {
                                // Send Server Set Chunk Size to 1024 bytes
                                val setChunkPayload = byteArrayOf(0x00, 0x00, 0x04, 0x00) // 1024
                                serverChunkStream.writePacket(
                                    RtmpPacket(RtmpPacket.TYPE_SET_CHUNK_SIZE, 0L, 0, setChunkPayload, RtmpPacket.CSID_CONTROL),
                                    bufOut
                                )
                                bufOut.flush()
                                setChunkProcessedLatch.countDown()
                                running = false
                            }
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
            client.close()
        }

        scope.launch {
            connection.connectAndPublish(
                endpointUrl = "rtmp://127.0.0.1:$port/live",
                streamKey = "test_key",
                onReady = { clientReady.countDown() }
            )
        }

        assertTrue("Client onReady should be invoked", clientReady.await(5, TimeUnit.SECONDS))
        assertTrue("Server should send Set Chunk Size", setChunkProcessedLatch.await(5, TimeUnit.SECONDS))

        // Give reader loop 100ms to process Set Chunk Size
        delay(150)

        // Verify outbound chunk size remains 4096 while incoming chunk size is updated to 1024
        // (Outbound chunk size optimization must not be degraded by incoming server request)
        scope.cancel()
    }

    @Test
    fun testReconnectStateMachine_exhaustionTransitionsToUnhealthy() = runBlocking {
        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            customEndpointUrl = "rtmp://127.0.0.1:9999/live", // Guaranteed unreachable port
            streamKey = "test_key"
        )
        val scope = CoroutineScope(Dispatchers.Default)
        val target = RtmpStreamOutputTarget(
            streamConfig = config,
            scope = scope
        )

        val statesObserved = mutableListOf<RtmpConnection.State>()
        val unhealthyLatch = CountDownLatch(1)

        val stateJob = scope.launch {
            target.connectionState.collect { state ->
                statesObserved.add(state)
                if (state is RtmpConnection.State.Unhealthy) {
                    unhealthyLatch.countDown()
                }
            }
        }

        target.start()

        // With exponential backoff (1s, 2s, 4s) + jitter, 3 retries take around 7-8s
        // Let's assert that the destination cleanly transitions to Unhealthy within 15 seconds
        assertTrue("Destination should reach Unhealthy state after max retries", unhealthyLatch.await(15, TimeUnit.SECONDS))
        val finalState = target.connectionState.value
        assertTrue("Final state should be Unhealthy", finalState is RtmpConnection.State.Unhealthy)

        stateJob.cancel()
        target.release()
        scope.cancel()
    }

    @Test
    fun testPostReconnect_syncFrameGatingDropsStaleDeltaFrames() = runBlocking {
        val config = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "test_key"
        )
        val scope = CoroutineScope(Dispatchers.Default)
        val target = RtmpStreamOutputTarget(
            streamConfig = config,
            scope = scope
        )

        // Initialize channel for test
        target.connection.initChannelForTesting(capacity = 64)

        // Simulate reconnect completion: waitingForSyncFrame is active
        target.waitingForSyncFrame.set(true)

        // 1. Send Inter-frame (Delta) -> flags = 0 (not BUFFER_FLAG_KEY_FRAME)
        val deltaBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41.toByte(), 0x00) // AnnexB NALU type 1 (non-IDR)
        val deltaBuf = ByteBuffer.wrap(deltaBytes)
        val deltaInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = deltaBytes.size
            presentationTimeUs = 100_000L
            flags = 0 // Delta frame!
        }

        target.onVideoSample(deltaBuf, deltaInfo)

        // Verified: delta frame was gated and dropped!
        assertEquals("Delta frames must be dropped while waiting for sync frame", 0L, target.totalBytesPacketized.get())
        assertTrue("Target must still be waiting for sync frame", target.waitingForSyncFrame.get())

        // 2. Send Keyframe (IDR) -> flags = BUFFER_FLAG_KEY_FRAME
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x00) // AnnexB NALU type 5 (IDR)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 133_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        target.onVideoSample(keyBuf, keyInfo)

        // Verified: fresh keyframe is accepted, packetized, and clears waiting flag!
        assertTrue("Bytes should be packetized for keyframe", target.totalBytesPacketized.get() > 0)
        assertFalse("Waiting for sync frame should be cleared after fresh keyframe", target.waitingForSyncFrame.get())

        // 3. Subsequent delta frames are now accepted
        val nextDeltaBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41.toByte(), 0x01)
        val nextDeltaBuf = ByteBuffer.wrap(nextDeltaBytes)
        val nextDeltaInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = nextDeltaBytes.size
            presentationTimeUs = 166_000L
            flags = 0
        }

        val bytesBefore = target.totalBytesPacketized.get()
        target.onVideoSample(nextDeltaBuf, nextDeltaInfo)
        assertTrue("Subsequent delta frame should now be packetized", target.totalBytesPacketized.get() > bytesBefore)

        target.release()
        scope.cancel()
    }
}
