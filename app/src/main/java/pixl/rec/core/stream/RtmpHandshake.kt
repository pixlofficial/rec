package pixl.rec.core.stream

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.random.Random

/**
 * RTMP Handshake protocol implementation (C0/C1/C2 and S0/S1/S2 validation).
 * Implements the standard 1536-byte RTMP Handshake specified in the Adobe RTMP Specification 1.0.
 */
object RtmpHandshake {
    const val HANDSHAKE_SIZE = 1536
    const val RTMP_VERSION: Byte = 0x03

    private val secureRandom = SecureRandom()

    /**
     * Generates C0 (1 byte) + C1 (1536 bytes) = 1537 bytes.
     * C1 format:
     * - 4 bytes: Time (monotonic or epoch timestamp)
     * - 4 bytes: Zero (0x00000000)
     * - 1528 bytes: Cryptographically strong pseudo-random bytes
     */
    fun createC0C1(
        timestamp: Int = (System.currentTimeMillis() / 1000).toInt(),
        randomBytes: ByteArray? = null
    ): ByteArray {
        val buffer = ByteBuffer.allocate(1 + HANDSHAKE_SIZE)
        // C0
        buffer.put(RTMP_VERSION)

        // C1
        buffer.putInt(timestamp)
        buffer.putInt(0) // 4 zero bytes

        val random = randomBytes ?: ByteArray(HANDSHAKE_SIZE - 8).apply {
            secureRandom.nextBytes(this)
        }
        require(random.size == HANDSHAKE_SIZE - 8) {
            "Random bytes must be exactly ${HANDSHAKE_SIZE - 8} bytes"
        }
        buffer.put(random)

        return buffer.array()
    }

    /**
     * Generates C2 (1536 bytes) in response to server's S1.
     * C2 format:
     * - 4 bytes: Time from S1
     * - 4 bytes: Time2 (Current timestamp when sending C2)
     * - 1528 bytes: Random bytes echoed directly from S1
     */
    fun createC2(
        s1: ByteArray,
        timestamp: Int = (System.currentTimeMillis() / 1000).toInt()
    ): ByteArray {
        require(s1.size == HANDSHAKE_SIZE) {
            "S1 packet must be exactly $HANDSHAKE_SIZE bytes (received ${s1.size})"
        }

        val buffer = ByteBuffer.allocate(HANDSHAKE_SIZE)
        val s1Buffer = ByteBuffer.wrap(s1)

        val s1Time = s1Buffer.int
        val s1ZeroOrVersion = s1Buffer.int // S1 bytes 4..7

        buffer.putInt(s1Time)
        buffer.putInt(timestamp) // Time2

        // Echo the 1528 random bytes from S1
        val randomPayload = ByteArray(HANDSHAKE_SIZE - 8)
        s1Buffer.get(randomPayload)
        buffer.put(randomPayload)

        return buffer.array()
    }

    /**
     * Validates S0 server protocol version byte.
     */
    fun validateS0(s0: Byte): Boolean = s0 == RTMP_VERSION

    /**
     * Executes the full synchronous handshake on the underlying socket streams:
     * 1. Writes C0 + C1 (1537 bytes)
     * 2. Reads S0 (1 byte) and verifies it is 0x03
     * 3. Reads S1 (1536 bytes)
     * 4. Writes C2 (1536 bytes)
     * 5. Reads S2 (1536 bytes)
     */
    @Throws(Exception::class)
    fun performHandshake(inputStream: InputStream, outputStream: OutputStream) {
        val c0c1 = createC0C1()
        outputStream.write(c0c1)
        outputStream.flush()

        // Read S0 (1 byte)
        val s0 = inputStream.read()
        if (s0 == -1) throw EOFException("Connection closed during RTMP S0 handshake")
        if (!validateS0(s0.toByte())) {
            throw IllegalStateException("Unsupported RTMP server version in S0: $s0 (expected $RTMP_VERSION)")
        }

        // Read S1 (1536 bytes)
        val s1 = ByteArray(HANDSHAKE_SIZE)
        readFully(inputStream, s1)

        // Generate and send C2 (1536 bytes)
        val c2 = createC2(s1)
        outputStream.write(c2)
        outputStream.flush()

        // Read S2 (1536 bytes)
        val s2 = ByteArray(HANDSHAKE_SIZE)
        readFully(inputStream, s2)
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val count = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
            if (count == -1) {
                throw EOFException("Connection closed unexpectedly during RTMP handshake at byte $bytesRead/${buffer.size}")
            }
            bytesRead += count
        }
    }
}
