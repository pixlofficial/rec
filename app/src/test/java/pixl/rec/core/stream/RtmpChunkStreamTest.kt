package pixl.rec.core.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RtmpChunkStreamTest {

    @Test
    fun testSmallPacket_roundtrip() {
        val chunkStream = RtmpChunkStream(chunkSize = 128)
        val payload = "Hello RTMP World".toByteArray()
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_COMMAND_AMF0,
            timestamp = 500L,
            streamId = 1,
            payload = payload,
            csid = RtmpPacket.CSID_COMMAND
        )

        val baos = ByteArrayOutputStream()
        chunkStream.writePacket(packet, baos)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val decoded = chunkStream.readPacket(bais)

        assertEquals(packet.messageType, decoded.messageType)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertEquals(packet.streamId, decoded.streamId)
        assertEquals(packet.csid, decoded.csid)
        assertArrayEquals(packet.payload, decoded.payload)
    }

    @Test
    fun testLargePacket_multiChunkRoundtrip() {
        // 10 KB payload split across 4096-byte chunks (3 chunks: 4096 + 4096 + 1808)
        val chunkStream = RtmpChunkStream(chunkSize = 4096)
        val payload = ByteArray(10000) { (it % 256).toByte() }
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = 12345L,
            streamId = 1,
            payload = payload,
            csid = RtmpPacket.CSID_VIDEO
        )

        val baos = ByteArrayOutputStream()
        chunkStream.writePacket(packet, baos)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val decoded = chunkStream.readPacket(bais)

        assertEquals(packet.messageType, decoded.messageType)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertEquals(packet.streamId, decoded.streamId)
        assertArrayEquals(packet.payload, decoded.payload)
    }

    @Test
    fun testExtendedTimestamp_roundtrip() {
        val chunkStream = RtmpChunkStream(chunkSize = 4096)
        val extendedTimestamp = 0x01FFFFFFL // Greater than 24-bit 0xFFFFFF
        val payload = "Extended Timestamp Test".toByteArray()
        val packet = RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            timestamp = extendedTimestamp,
            streamId = 1,
            payload = payload,
            csid = RtmpPacket.CSID_AUDIO
        )

        val baos = ByteArrayOutputStream()
        chunkStream.writePacket(packet, baos)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val decoded = chunkStream.readPacket(bais)

        assertEquals(extendedTimestamp, decoded.timestamp)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun testCreateSetChunkSizePacket() {
        val chunkStream = RtmpChunkStream()
        val packet = chunkStream.createSetChunkSizePacket(8192)

        assertEquals(RtmpPacket.TYPE_SET_CHUNK_SIZE, packet.messageType)
        assertEquals(RtmpPacket.CSID_CONTROL, packet.csid)
        assertEquals(4, packet.payload.size)

        val bais = ByteArrayInputStream(packet.payload)
        val size = java.io.DataInputStream(bais).readInt()
        assertEquals(8192, size)
    }

    @Test
    fun testDecoupledChunkSizes_serverSetChunkSizeDoesNotOverwriteOutChunkSize() {
        val clientStream = RtmpChunkStream(outChunkSize = 4096, inChunkSize = 128)
        assertEquals(4096, clientStream.outChunkSize)
        assertEquals(128, clientStream.inChunkSize)

        // Server sends SetChunkSize = 512
        val serverStream = RtmpChunkStream(outChunkSize = 128)
        val setChunkPacket = serverStream.createSetChunkSizePacket(512)
        val baos = ByteArrayOutputStream()
        serverStream.writePacket(setChunkPacket, baos)

        // Client reads the server packet
        val bais = ByteArrayInputStream(baos.toByteArray())
        val decoded = clientStream.readPacket(bais)

        assertEquals(RtmpPacket.TYPE_SET_CHUNK_SIZE, decoded.messageType)
        assertEquals(512, clientStream.inChunkSize) // Updated from server
        assertEquals(4096, clientStream.outChunkSize) // Preserved client outgoing chunk size!
    }
}
