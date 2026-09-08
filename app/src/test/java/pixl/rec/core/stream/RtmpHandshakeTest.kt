package pixl.rec.core.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class RtmpHandshakeTest {

    @Test
    fun testCreateC0C1_structureAndSizes() {
        val timestamp = 12345678
        val randomBytes = ByteArray(1528) { (it % 256).toByte() }

        val c0c1 = RtmpHandshake.createC0C1(timestamp, randomBytes)

        assertEquals(1537, c0c1.size)
        assertEquals(0x03.toByte(), c0c1[0]) // C0 RTMP version

        val buffer = ByteBuffer.wrap(c0c1, 1, 1536)
        assertEquals(timestamp, buffer.int) // Timestamp
        assertEquals(0, buffer.int) // Zero field

        val extractedRandom = ByteArray(1528)
        buffer.get(extractedRandom)
        assertArrayEquals(randomBytes, extractedRandom)
    }

    @Test
    fun testCreateC2_echoesS1Properly() {
        val s1 = ByteArray(1536)
        val s1Buffer = ByteBuffer.wrap(s1)
        val s1Time = 998877
        s1Buffer.putInt(s1Time)
        s1Buffer.putInt(0) // Zero / version
        val s1Random = ByteArray(1528) { (it xor 0xAA).toByte() }
        s1Buffer.put(s1Random)

        val c2Time = 998899
        val c2 = RtmpHandshake.createC2(s1, timestamp = c2Time)

        assertEquals(1536, c2.size)
        val c2Buffer = ByteBuffer.wrap(c2)
        assertEquals(s1Time, c2Buffer.int) // Echoes S1 time
        assertEquals(c2Time, c2Buffer.int) // Time2

        val echoedRandom = ByteArray(1528)
        c2Buffer.get(echoedRandom)
        assertArrayEquals(s1Random, echoedRandom)
    }

    @Test
    fun testValidateS0() {
        assertTrue(RtmpHandshake.validateS0(0x03.toByte()))
        assertFalse(RtmpHandshake.validateS0(0x02.toByte()))
        assertFalse(RtmpHandshake.validateS0(0x04.toByte()))
    }

    @Test
    fun testPerformHandshake_withMockServer() {
        // Prepare server response stream: S0 (0x03) + S1 (1536 bytes) + S2 (1536 bytes)
        val s1 = ByteArray(1536) { 0x11.toByte() }
        val s2 = ByteArray(1536) { 0x22.toByte() }

        val serverResponseBaos = ByteArrayOutputStream()
        serverResponseBaos.write(0x03) // S0
        serverResponseBaos.write(s1)   // S1
        serverResponseBaos.write(s2)   // S2

        val inputStream = ByteArrayInputStream(serverResponseBaos.toByteArray())
        val clientOutputBaos = ByteArrayOutputStream()

        RtmpHandshake.performHandshake(inputStream, clientOutputBaos)

        val clientWritten = clientOutputBaos.toByteArray()
        // Client writes C0C1 (1537) + C2 (1536) = 3073 bytes
        assertEquals(1537 + 1536, clientWritten.size)
        assertEquals(0x03.toByte(), clientWritten[0])
    }
}
