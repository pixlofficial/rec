package pixl.rec.core.stream

import pixl.rec.core.model.PacketMediaType

/**
 * Fundamental RTMP Protocol packet definition.
 * Encapsulates message type, stream ID, monotonic presentation timestamp (ms), and binary payload.
 */
data class RtmpPacket(
    val messageType: Int,
    val timestamp: Long,
    val streamId: Int = 0,
    val payload: ByteArray,
    val csid: Int = defaultCsid(messageType),
    val enqueueTimeNs: Long = System.nanoTime()
) {
    /**
     * Resolves the media type classification of this packet from its header and payload.
     * Guaranteed zero-allocation: directly inspects payload byte flags without array slicing.
     */
    val mediaType: PacketMediaType by lazy(LazyThreadSafetyMode.NONE) {
        when (messageType) {
            TYPE_AUDIO -> {
                if (payload.size >= 2 && (payload[1].toInt() and 0xFF) == 0x00) {
                    PacketMediaType.AUDIO_SEQUENCE_HEADER
                } else {
                    PacketMediaType.AUDIO
                }
            }
            TYPE_VIDEO -> {
                if (payload.isNotEmpty()) {
                    val firstByte = payload[0].toInt() and 0xFF
                    // Enhanced RTMP (FourCC) starts with high bit 0b1000_0000 (0x80)
                    val isEnhancedRtmp = (firstByte and 0x80) != 0
                    if (isEnhancedRtmp && payload.size >= 5) {
                        val packetType = firstByte and 0x0F
                        val frameType = (firstByte shr 4) and 0x07
                        when {
                            packetType == 0x00 -> PacketMediaType.VIDEO_SEQUENCE_HEADER
                            frameType == 1 -> PacketMediaType.VIDEO_KEYFRAME
                            else -> PacketMediaType.VIDEO_INTER
                        }
                    } else {
                        // Standard FLV Video Tag Header:
                        // High 4 bits: Frame Type (1 = Keyframe, 2 = Inter-frame)
                        // Low 4 bits: Codec ID (7 = AVC)
                        val frameType = (firstByte shr 4) and 0x0F
                        val avcPacketType = if (payload.size >= 2) payload[1].toInt() and 0xFF else -1

                        when {
                            avcPacketType == 0x00 -> PacketMediaType.VIDEO_SEQUENCE_HEADER
                            frameType == 1 -> PacketMediaType.VIDEO_KEYFRAME
                            else -> PacketMediaType.VIDEO_INTER
                        }
                    }
                } else {
                    PacketMediaType.VIDEO_INTER
                }
            }
            else -> PacketMediaType.METADATA_COMMAND
        }
    }

    companion object {
        // Protocol Control Messages (CSID 2)
        const val TYPE_SET_CHUNK_SIZE = 0x01
        const val TYPE_ABORT = 0x02
        const val TYPE_ACK = 0x03
        const val TYPE_USER_CONTROL = 0x04
        const val TYPE_WINDOW_ACK_SIZE = 0x05
        const val TYPE_SET_PEER_BANDWIDTH = 0x06

        // Media & Data Messages
        const val TYPE_AUDIO = 0x08
        const val TYPE_VIDEO = 0x09
        const val TYPE_DATA_AMF3 = 0x0F
        const val TYPE_COMMAND_AMF3 = 0x11
        const val TYPE_DATA_AMF0 = 0x12
        const val TYPE_COMMAND_AMF0 = 0x14

        // Standard RTMP Chunk Stream IDs
        const val CSID_CONTROL = 2
        const val CSID_COMMAND = 3
        const val CSID_AUDIO = 4
        const val CSID_VIDEO = 5
        const val CSID_DATA = 6

        /**
         * Resolves the recommended Chunk Stream ID (CSID) for a given message type.
         */
        fun defaultCsid(messageType: Int): Int = when (messageType) {
            TYPE_SET_CHUNK_SIZE, TYPE_ABORT, TYPE_ACK,
            TYPE_USER_CONTROL, TYPE_WINDOW_ACK_SIZE, TYPE_SET_PEER_BANDWIDTH -> CSID_CONTROL
            TYPE_COMMAND_AMF0, TYPE_COMMAND_AMF3 -> CSID_COMMAND
            TYPE_AUDIO -> CSID_AUDIO
            TYPE_VIDEO -> CSID_VIDEO
            TYPE_DATA_AMF0, TYPE_DATA_AMF3 -> CSID_DATA
            else -> CSID_COMMAND
        }

        /**
         * Factory creating an RTMP User Control Ping Response (Type 0x04, Event 0x0007).
         * Echoes the 4-byte timestamp received from the server's Ping Request.
         */
        fun createPingResponse(timestamp: Int): RtmpPacket {
            val payload = byteArrayOf(
                0x00, 0x07, // Event Type 7 = Ping Response
                ((timestamp shr 24) and 0xFF).toByte(),
                ((timestamp shr 16) and 0xFF).toByte(),
                ((timestamp shr 8) and 0xFF).toByte(),
                (timestamp and 0xFF).toByte()
            )
            return RtmpPacket(
                messageType = TYPE_USER_CONTROL,
                timestamp = 0L,
                streamId = 0,
                payload = payload,
                csid = CSID_CONTROL
            )
        }

        /**
         * Factory creating an RTMP Acknowledgement message (Type 0x03).
         * Carries the 4-byte cumulative sequence number of bytes received so far.
         */
        fun createAcknowledgement(sequenceNumber: Long): RtmpPacket {
            val seqInt = (sequenceNumber and 0xFFFFFFFFL).toInt()
            val payload = byteArrayOf(
                ((seqInt shr 24) and 0xFF).toByte(),
                ((seqInt shr 16) and 0xFF).toByte(),
                ((seqInt shr 8) and 0xFF).toByte(),
                (seqInt and 0xFF).toByte()
            )
            return RtmpPacket(
                messageType = TYPE_ACK,
                timestamp = 0L,
                streamId = 0,
                payload = payload,
                csid = CSID_CONTROL
            )
        }

        /**
         * Factory creating an RTMP Window Acknowledgement Size message (Type 0x05).
         */
        fun createWindowAckSize(ackSize: Int): RtmpPacket {
            val payload = byteArrayOf(
                ((ackSize shr 24) and 0xFF).toByte(),
                ((ackSize shr 16) and 0xFF).toByte(),
                ((ackSize shr 8) and 0xFF).toByte(),
                (ackSize and 0xFF).toByte()
            )
            return RtmpPacket(
                messageType = TYPE_WINDOW_ACK_SIZE,
                timestamp = 0L,
                streamId = 0,
                payload = payload,
                csid = CSID_CONTROL
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RtmpPacket
        if (messageType != other.messageType) return false
        if (timestamp != other.timestamp) return false
        if (streamId != other.streamId) return false
        if (!payload.contentEquals(other.payload)) return false
        if (csid != other.csid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageType
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + streamId
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + csid
        return result
    }
}
