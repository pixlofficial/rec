package pixl.rec.core.stream

/**
 * Fundamental RTMP Protocol packet definition.
 * Encapsulates message type, stream ID, monotonic presentation timestamp (ms), and binary payload.
 */
data class RtmpPacket(
    val messageType: Int,
    val timestamp: Long,
    val streamId: Int = 0,
    val payload: ByteArray,
    val csid: Int = defaultCsid(messageType)
) {
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
