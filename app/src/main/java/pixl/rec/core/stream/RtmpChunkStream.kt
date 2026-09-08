package pixl.rec.core.stream

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RTMP Chunk Stream protocol serializer and deserializer.
 * Handles Chunk Formats (0, 1, 2, 3), extended timestamps, and chunk multiplexing.
 */
class RtmpChunkStream(
    var outChunkSize: Int = DEFAULT_CHUNK_SIZE,
    var inChunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    constructor(chunkSize: Int) : this(outChunkSize = chunkSize, inChunkSize = chunkSize)

    /**
     * Backward-compatible property alias for chunkSize.
     */
    var chunkSize: Int
        get() = outChunkSize
        set(value) {
            outChunkSize = value
            inChunkSize = value
        }
    companion object {
        const val DEFAULT_CHUNK_SIZE = 128
        const val TARGET_CHUNK_SIZE = 4096 // Optimal balance between header overhead & multiplex latency

        const val FMT_FULL = 0        // 11 bytes message header
        const val FMT_SAME_STREAM = 1 // 7 bytes message header
        const val FMT_TIMESTAMP = 2   // 3 bytes message header
        const val FMT_CONTINUATION = 3// 0 bytes message header

        const val EXTENDED_TIMESTAMP_THRESHOLD = 0xFFFFFF
    }

    /**
     * Internal state per Chunk Stream ID (CSID) to track previous headers for Formats 1, 2, 3.
     */
    private data class ChunkHeaderState(
        var timestamp: Long = 0,
        var timestampDelta: Long = 0,
        var messageLength: Int = 0,
        var messageType: Int = 0,
        var streamId: Int = 0,
        var hasExtendedTimestamp: Boolean = false
    )

    private val readStates = mutableMapOf<Int, ChunkHeaderState>()
    private val readIncompletePayloads = mutableMapOf<Int, ByteArrayOutputStream>()

    // --- Writing / Chunking ---

    /**
     * Chunks an [RtmpPacket] and writes it to the output stream.
     * Uses Format 0 for the first chunk and Format 3 for continuation chunks.
     */
    @Throws(Exception::class)
    fun writePacket(packet: RtmpPacket, outputStream: OutputStream) {
        val payload = packet.payload
        val totalLength = payload.size
        val csid = packet.csid
        val timestamp = packet.timestamp.coerceAtLeast(0L)
        val streamId = packet.streamId
        val messageType = packet.messageType

        var offset = 0
        var isFirstChunk = true

        while (offset < totalLength || (totalLength == 0 && isFirstChunk)) {
            val bytesInChunk = (totalLength - offset).coerceAtMost(outChunkSize)

            if (isFirstChunk) {
                // Write Format 0 (Full 11-byte header)
                writeBasicHeader(outputStream, FMT_FULL, csid)

                val hasExtendedTimestamp = timestamp >= EXTENDED_TIMESTAMP_THRESHOLD
                val headerTimestamp = if (hasExtendedTimestamp) EXTENDED_TIMESTAMP_THRESHOLD else timestamp.toInt()

                // 3 bytes Timestamp
                outputStream.write((headerTimestamp shr 16) and 0xFF)
                outputStream.write((headerTimestamp shr 8) and 0xFF)
                outputStream.write(headerTimestamp and 0xFF)

                // 3 bytes Message Length (big-endian)
                outputStream.write((totalLength shr 16) and 0xFF)
                outputStream.write((totalLength shr 8) and 0xFF)
                outputStream.write(totalLength and 0xFF)

                // 1 byte Message Type ID
                outputStream.write(messageType and 0xFF)

                // 4 bytes Message Stream ID (LITTLE-ENDIAN per RTMP specification!)
                outputStream.write(streamId and 0xFF)
                outputStream.write((streamId shr 8) and 0xFF)
                outputStream.write((streamId shr 16) and 0xFF)
                outputStream.write((streamId shr 24) and 0xFF)

                // 4 bytes Extended Timestamp if applicable
                if (hasExtendedTimestamp) {
                    outputStream.write(((timestamp shr 24) and 0xFF).toInt())
                    outputStream.write(((timestamp shr 16) and 0xFF).toInt())
                    outputStream.write(((timestamp shr 8) and 0xFF).toInt())
                    outputStream.write((timestamp and 0xFF).toInt())
                }

                isFirstChunk = false
            } else {
                // Write Format 3 (Continuation chunk, 0-byte header)
                writeBasicHeader(outputStream, FMT_CONTINUATION, csid)

                // If timestamp required extended timestamp, Format 3 also appends extended timestamp
                if (timestamp >= EXTENDED_TIMESTAMP_THRESHOLD) {
                    outputStream.write(((timestamp shr 24) and 0xFF).toInt())
                    outputStream.write(((timestamp shr 16) and 0xFF).toInt())
                    outputStream.write(((timestamp shr 8) and 0xFF).toInt())
                    outputStream.write((timestamp and 0xFF).toInt())
                }
            }

            if (bytesInChunk > 0) {
                outputStream.write(payload, offset, bytesInChunk)
                offset += bytesInChunk
            } else {
                break // 0-byte packet written
            }
        }
    }

    /**
     * Encodes and writes RTMP Basic Header (1 to 3 bytes).
     */
    private fun writeBasicHeader(outputStream: OutputStream, fmt: Int, csid: Int) {
        when {
            csid in 2..63 -> {
                // 1 byte header
                outputStream.write((fmt shl 6) or csid)
            }
            csid in 64..319 -> {
                // 2 bytes header
                outputStream.write((fmt shl 6))
                outputStream.write(csid - 64)
            }
            else -> {
                // 3 bytes header
                outputStream.write((fmt shl 6) or 1)
                val offset = csid - 64
                outputStream.write(offset and 0xFF)
                outputStream.write((offset shr 8) and 0xFF)
            }
        }
    }

    // --- Reading / Dechunking ---

    /**
     * Reads the next complete [RtmpPacket] from the incoming input stream.
     * Blocks until a full message payload is reassembled.
     */
    @Throws(Exception::class)
    fun readPacket(inputStream: InputStream): RtmpPacket {
        val dis = DataInputStream(inputStream)

        while (true) {
            // 1. Read Basic Header
            val firstByte = dis.read()
            if (firstByte == -1) throw EOFException("End of stream reached while reading RTMP chunk")

            val fmt = (firstByte shr 6) and 0x03
            val csidCode = firstByte and 0x3F
            val csid = when (csidCode) {
                0 -> dis.readUnsignedByte() + 64
                1 -> {
                    val b1 = dis.readUnsignedByte()
                    val b2 = dis.readUnsignedByte()
                    b1 + (b2 shl 8) + 64
                }
                else -> csidCode
            }

            val state = readStates.getOrPut(csid) { ChunkHeaderState() }

            // 2. Read Message Header based on fmt
            when (fmt) {
                FMT_FULL -> {
                    val ts = read24BitInt(dis).toLong()
                    state.messageLength = read24BitInt(dis)
                    state.messageType = dis.readUnsignedByte()
                    state.streamId = read32BitLittleEndian(dis)
                    state.hasExtendedTimestamp = (ts == EXTENDED_TIMESTAMP_THRESHOLD.toLong())
                    state.timestamp = if (state.hasExtendedTimestamp) {
                        dis.readInt().toLong() and 0xFFFFFFFFL
                    } else {
                        ts
                    }
                    state.timestampDelta = 0
                }
                FMT_SAME_STREAM -> {
                    val delta = read24BitInt(dis).toLong()
                    state.messageLength = read24BitInt(dis)
                    state.messageType = dis.readUnsignedByte()
                    state.hasExtendedTimestamp = (delta == EXTENDED_TIMESTAMP_THRESHOLD.toLong())
                    state.timestampDelta = if (state.hasExtendedTimestamp) {
                        dis.readInt().toLong() and 0xFFFFFFFFL
                    } else {
                        delta
                    }
                    state.timestamp += state.timestampDelta
                }
                FMT_TIMESTAMP -> {
                    val delta = read24BitInt(dis).toLong()
                    state.hasExtendedTimestamp = (delta == EXTENDED_TIMESTAMP_THRESHOLD.toLong())
                    state.timestampDelta = if (state.hasExtendedTimestamp) {
                        dis.readInt().toLong() and 0xFFFFFFFFL
                    } else {
                        delta
                    }
                    state.timestamp += state.timestampDelta
                }
                FMT_CONTINUATION -> {
                    if (state.hasExtendedTimestamp) {
                        state.timestamp = dis.readInt().toLong() and 0xFFFFFFFFL
                    } else {
                        state.timestamp += state.timestampDelta
                    }
                }
            }

            // 3. Read Chunk Data
            val incompleteBaos = readIncompletePayloads.getOrPut(csid) { ByteArrayOutputStream() }
            val bytesRemaining = state.messageLength - incompleteBaos.size()
            val bytesToRead = bytesRemaining.coerceAtMost(inChunkSize)

            if (bytesToRead > 0) {
                val chunkBuffer = ByteArray(bytesToRead)
                dis.readFully(chunkBuffer)
                incompleteBaos.write(chunkBuffer)
            }

            // 4. Check if packet assembly is complete
            if (incompleteBaos.size() >= state.messageLength) {
                val completePayload = incompleteBaos.toByteArray()
                readIncompletePayloads.remove(csid)

                val packet = RtmpPacket(
                    messageType = state.messageType,
                    timestamp = state.timestamp,
                    streamId = state.streamId,
                    payload = completePayload,
                    csid = csid
                )

                // Handle protocol control messages internally
                if (packet.messageType == RtmpPacket.TYPE_SET_CHUNK_SIZE && completePayload.size >= 4) {
                    val newChunkSize = ByteBuffer.wrap(completePayload).int and 0x7FFFFFFF
                    if (newChunkSize > 0) {
                        this.inChunkSize = newChunkSize
                    }
                }

                return packet
            }
        }
    }

    private fun read24BitInt(dis: DataInputStream): Int {
        val b1 = dis.readUnsignedByte()
        val b2 = dis.readUnsignedByte()
        val b3 = dis.readUnsignedByte()
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun read32BitLittleEndian(dis: DataInputStream): Int {
        val b1 = dis.readUnsignedByte()
        val b2 = dis.readUnsignedByte()
        val b3 = dis.readUnsignedByte()
        val b4 = dis.readUnsignedByte()
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    /**
     * Creates a Set Chunk Size protocol packet.
     */
    fun createSetChunkSizePacket(newChunkSize: Int): RtmpPacket {
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(newChunkSize and 0x7FFFFFFF)
        return RtmpPacket(
            messageType = RtmpPacket.TYPE_SET_CHUNK_SIZE,
            timestamp = 0,
            streamId = 0,
            payload = buffer.array(),
            csid = RtmpPacket.CSID_CONTROL
        )
    }
}
