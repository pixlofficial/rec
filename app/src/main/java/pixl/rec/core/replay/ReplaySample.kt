package pixl.rec.core.replay

import android.media.MediaCodec

/**
 * Encapsulates an encoded media sample (video or audio) stored in volatile RAM
 * inside the [ReplayRingBuffer].
 */
data class ReplaySample(
    val trackIndex: Int, // 0 = Video, 1 = Audio
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
) {
    val isKeyframe: Boolean
        get() = trackIndex == 0 && (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

    val sizeBytes: Int
        get() = data.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReplaySample

        if (trackIndex != other.trackIndex) return false
        if (!data.contentEquals(other.data)) return false
        if (presentationTimeUs != other.presentationTimeUs) return false
        if (flags != other.flags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackIndex
        result = 31 * result + data.contentHashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + flags
        return result
    }
}
