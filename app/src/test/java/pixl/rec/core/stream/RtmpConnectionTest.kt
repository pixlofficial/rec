package pixl.rec.core.stream

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RtmpConnectionTest {

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

    /**
     * Helper to perform mock server-side RTMP handshake:
     * Reads C0 + C1 (1537 bytes), sends S0 + S1 (1537 bytes) + S2 (1536 bytes),
     * and reads C2 (1536 bytes).
     */
    private fun serverHandshake(input: BufferedInputStream, output: BufferedOutputStream) {
        val c0c1 = ByteArray(1537)
        var totalRead = 0
        while (totalRead < 1537) {
            val r = input.read(c0c1, totalRead, 1537 - totalRead)
            if (r < 0) throw IllegalStateException("EOF during C0/C1 read")
            totalRead += r
        }

        // Send S0 + S1 (1537 bytes)
        val s0s1 = ByteArray(1537)
        s0s1[0] = 3 // RTMP version 3
        output.write(s0s1)

        // Send S2 (1536 bytes echo of C1)
        val s2 = ByteArray(1536)
        System.arraycopy(c0c1, 1, s2, 0, 1536)
        output.write(s2)
        output.flush()

        // Read C2 (1536 bytes)
        val c2 = ByteArray(1536)
        totalRead = 0
        while (totalRead < 1536) {
            val r = input.read(c2, totalRead, 1536 - totalRead)
            if (r < 0) throw IllegalStateException("EOF during C2 read")
            totalRead += r
        }
    }

    @Test
    fun testProbe_successWithMockServer() = runBlocking {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        val serverAccepted = CountDownLatch(1)

        testScope?.launch {
            val client = server.accept()
            val bufIn = BufferedInputStream(client.getInputStream())
            val bufOut = BufferedOutputStream(client.getOutputStream())

            // 1. Handshake
            serverHandshake(bufIn, bufOut)

            // 2. Read SetChunkSize and Connect command
            val serverChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
            var handled = false
            while (!handled) {
                val packet = serverChunkStream.readPacket(bufIn)
                if (packet.messageType == RtmpPacket.TYPE_SET_CHUNK_SIZE) {
                    val newSize = java.nio.ByteBuffer.wrap(packet.payload).int
                    serverChunkStream.inChunkSize = newSize
                } else if (packet.messageType == RtmpPacket.TYPE_COMMAND_AMF0) {
                    val cmd = Amf0.parseCommand(packet.payload)
                    if (cmd?.commandName == "connect") {
                        // Send _result for transaction 1.0
                        val resultPayload = Amf0.Writer()
                            .writeString("_result")
                            .writeNumber(1.0)
                            .writeObject(mapOf("fmsVer" to "FMS/3,5,7,7009", "capabilities" to 31.0))
                            .writeObject(mapOf("level" to "status", "code" to "NetConnection.Connect.Success"))
                            .toByteArray()

                        val resultPacket = RtmpPacket(
                            messageType = RtmpPacket.TYPE_COMMAND_AMF0,
                            timestamp = 0,
                            streamId = 0,
                            payload = resultPayload,
                            csid = RtmpPacket.CSID_COMMAND
                        )
                        serverChunkStream.writePacket(resultPacket, bufOut)
                        bufOut.flush()
                        handled = true
                    }
                }
            }
            serverAccepted.countDown()
            client.close()
        }

        val result = RtmpConnection.testProbe("rtmp://127.0.0.1:$port/live", timeoutMs = 4000)
        assertTrue("Probe should succeed against mock RTMP server", result.isSuccess)
        assertTrue(serverAccepted.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun testProbe_blankEndpointFails() = runBlocking {
        val result = RtmpConnection.testProbe("   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun testProbe_invalidHostFails() = runBlocking {
        val result = RtmpConnection.testProbe("rtmp://127.0.0.1:59999/live", timeoutMs = 500)
        assertTrue(result.isFailure)
    }

    @Test
    fun testConnectAndPublish_fullHandshakeAndPublish() = runBlocking {
        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        val publishReceivedLatch = CountDownLatch(1)
        val videoPacketReceivedLatch = CountDownLatch(1)

        testScope?.launch {
            val client = server.accept()
            val bufIn = BufferedInputStream(client.getInputStream())
            val bufOut = BufferedOutputStream(client.getOutputStream())

            serverHandshake(bufIn, bufOut)

            val serverChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
            var isStreaming = true

            while (isStreaming) {
                try {
                    val packet = serverChunkStream.readPacket(bufIn)
                    when (packet.messageType) {
                        RtmpPacket.TYPE_SET_CHUNK_SIZE -> {
                            val newSize = java.nio.ByteBuffer.wrap(packet.payload).int
                            serverChunkStream.inChunkSize = newSize
                        }
                        RtmpPacket.TYPE_COMMAND_AMF0 -> {
                            val cmd = Amf0.parseCommand(packet.payload)
                            when (cmd?.commandName) {
                                "connect" -> {
                                    val result = Amf0.Writer()
                                        .writeString("_result")
                                        .writeNumber(1.0)
                                        .writeObject(mapOf("fmsVer" to "FMS/3,5,7,7009"))
                                        .writeObject(mapOf("level" to "status", "code" to "NetConnection.Connect.Success"))
                                        .toByteArray()
                                    serverChunkStream.writePacket(
                                        RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, result, RtmpPacket.CSID_COMMAND),
                                        bufOut
                                    )
                                    bufOut.flush()
                                }
                                "createStream" -> {
                                    val result = Amf0.Writer()
                                        .writeString("_result")
                                        .writeNumber(4.0)
                                        .writeNull()
                                        .writeNumber(1.0) // streamId = 1
                                        .toByteArray()
                                    serverChunkStream.writePacket(
                                        RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, result, RtmpPacket.CSID_COMMAND),
                                        bufOut
                                    )
                                    bufOut.flush()
                                }
                                "publish" -> {
                                    publishReceivedLatch.countDown()
                                }
                            }
                        }
                        RtmpPacket.TYPE_VIDEO -> {
                            videoPacketReceivedLatch.countDown()
                            isStreaming = false
                        }
                    }
                } catch (_: Throwable) {
                    break
                }
            }
            client.close()
        }

        val scope = CoroutineScope(Dispatchers.IO)
        val connection = RtmpConnection(scope)
        val readyLatch = CountDownLatch(1)

        scope.launch {
            connection.connectAndPublish(
                endpointUrl = "rtmp://127.0.0.1:$port/live",
                streamKey = "pixl_test_key_123",
                onReady = { readyLatch.countDown() }
            )
        }

        assertTrue("Client should invoke onReady", readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Server should receive publish command", publishReceivedLatch.await(3, TimeUnit.SECONDS))
        assertEquals(RtmpConnection.State.Streaming, connection.state.value)

        // Test enqueuing a video packet
        val videoPayload = byteArrayOf(0x17, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x65)
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 100,
            streamId = 1,
            payload = videoPayload,
            csid = RtmpPacket.CSID_VIDEO
        )
        val enqueued = connection.enqueuePacket(packet)
        assertTrue("Packet should be successfully enqueued", enqueued)
        assertEquals(1L, connection.totalFramesSent.get())
        assertEquals(0L, connection.totalFramesDropped.get())

        assertTrue("Server should receive video packet", videoPacketReceivedLatch.await(3, TimeUnit.SECONDS))

        scope.cancel()
    }
}
