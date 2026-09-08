package pixl.rec.core.stream

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * FLV Tag packetizer for RTMP streaming.
 * Converts raw MediaCodec output buffers (AVC/H.264, Enhanced RTMP HEVC/H.265, and AAC)
 * into specification-compliant FLV payloads for [RtmpPacket].
 */
object FlvPacketizer {

    // --- Audio Specific Configuration ---

    /**
     * Generates a 2-byte ISO/IEC 14496-3 AudioSpecificConfig (ASC) for AAC-LC.
     */
    fun createAudioSpecificConfig(sampleRate: Int = 48000, channels: Int = 2): ByteArray {
        val audioObjectType = 2 // AAC-LC (5 bits)
        val samplingFreqIndex = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 3 // Default 48000
        } // 4 bits
        val channelConfig = channels.coerceIn(1, 2) // 4 bits

        // 5 bits audioObjectType + 4 bits samplingFreqIndex + 4 bits channelConfig + 3 bits 0
        val byte1 = ((audioObjectType shl 3) or (samplingFreqIndex shr 1)).toByte()
        val byte2 = (((samplingFreqIndex and 1) shl 7) or (channelConfig shl 3)).toByte()

        return byteArrayOf(byte1, byte2)
    }

    /**
     * Creates an AAC Sequence Header FLV packet containing AudioSpecificConfig.
     */
    fun createAacSequenceHeaderPacket(
        sampleRate: Int = 48000,
        channels: Int = 2,
        timestampMs: Long = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val asc = createAudioSpecificConfig(sampleRate, channels)
        val payload = ByteArray(2 + asc.size)
        payload[0] = 0xAF.toByte() // AAC, 44.1k/48k, 16-bit, Stereo
        payload[1] = 0x00.toByte() // AACPacketType = 0 (Sequence Header)
        System.arraycopy(asc, 0, payload, 2, asc.size)

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = payload,
            csid = RtmpPacket.CSID_AUDIO
        )
    }

    /**
     * Packages a raw AAC audio frame into an FLV audio packet.
     * Strips 7-byte ADTS header if present.
     */
    fun createAacFramePacket(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        timestampMs: Long,
        streamId: Int = 1
    ): RtmpPacket {
        var startOffset = offset
        var frameLength = length

        // Strip ADTS header if present (starts with 0xFFF syncword)
        if (frameLength >= 7 && (data[startOffset].toInt() and 0xFF) == 0xFF &&
            (data[startOffset + 1].toInt() and 0xF0) == 0xF0
        ) {
            val hasCrc = (data[startOffset + 1].toInt() and 0x01) == 0
            val headerSize = if (hasCrc) 9 else 7
            startOffset += headerSize
            frameLength -= headerSize
        }

        val payload = ByteArray(2 + frameLength)
        payload[0] = 0xAF.toByte() // AAC, 44.1/48k, 16-bit, Stereo
        payload[1] = 0x01.toByte() // AACPacketType = 1 (AAC Raw Frame)
        System.arraycopy(data, startOffset, payload, 2, frameLength)

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_AUDIO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = payload,
            csid = RtmpPacket.CSID_AUDIO
        )
    }

    // --- AVC / H.264 Video Packetizing ---

    /**
     * Builds an AVCDecoderConfigurationRecord and wraps it in an AVC Sequence Header FLV packet.
     */
    fun createAvcSequenceHeaderPacket(
        sps: ByteArray,
        pps: ByteArray,
        timestampMs: Long = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val baos = ByteArrayOutputStream()

        // FLV Video Tag Header (AVC Keyframe Sequence Header)
        baos.write(0x17) // Keyframe (0x10) | CodecID AVC (0x07)
        baos.write(0x00) // AVCPacketType = 0 (Sequence Header)
        baos.write(0x00) // CompositionTime (3 bytes: 0)
        baos.write(0x00)
        baos.write(0x00)

        // AVCDecoderConfigurationRecord
        baos.write(0x01) // configurationVersion = 1
        baos.write((if (sps.size > 1) sps[1] else 0x64).toInt()) // AVCProfileIndication
        baos.write((if (sps.size > 2) sps[2] else 0x00).toInt()) // profile_compatibility
        baos.write((if (sps.size > 3) sps[3] else 0x1F).toInt()) // AVCLevelIndication
        baos.write(0xFF) // 6 bits reserved (111111) | lengthSizeMinusOne = 3 (4-byte NAL length)
        baos.write(0xE1) // 3 bits reserved (111) | numOfSequenceParameterSets = 1

        // SPS Length (2 bytes) + SPS
        baos.write((sps.size shr 8) and 0xFF)
        baos.write(sps.size and 0xFF)
        baos.write(sps)

        // PPS Count (1) + PPS Length (2 bytes) + PPS
        baos.write(0x01)
        baos.write((pps.size shr 8) and 0xFF)
        baos.write(pps.size and 0xFF)
        baos.write(pps)

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = baos.toByteArray(),
            csid = RtmpPacket.CSID_VIDEO
        )
    }

    /**
     * Packages an AVC NAL unit into an FLV video frame packet with a 4-byte length prefix.
     */
    fun createAvcFramePacket(
        nalUnits: List<ByteArray>,
        isKeyframe: Boolean,
        timestampMs: Long,
        compositionTimeMs: Int = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val totalNalSize = nalUnits.sumOf { 4 + it.size }
        val payload = ByteArray(5 + totalNalSize)

        // Tag Header: Keyframe (0x17) or Interframe (0x27)
        payload[0] = if (isKeyframe) 0x17.toByte() else 0x27.toByte()
        payload[1] = 0x01.toByte() // AVCPacketType = 1 (NALU)
        payload[2] = ((compositionTimeMs shr 16) and 0xFF).toByte()
        payload[3] = ((compositionTimeMs shr 8) and 0xFF).toByte()
        payload[4] = (compositionTimeMs and 0xFF).toByte()

        var offset = 5
        for (nal in nalUnits) {
            val length = nal.size
            payload[offset++] = ((length shr 24) and 0xFF).toByte()
            payload[offset++] = ((length shr 16) and 0xFF).toByte()
            payload[offset++] = ((length shr 8) and 0xFF).toByte()
            payload[offset++] = (length and 0xFF).toByte()
            System.arraycopy(nal, 0, payload, offset, length)
            offset += length
        }

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = payload,
            csid = RtmpPacket.CSID_VIDEO
        )
    }

    // --- Enhanced RTMP HEVC / H.265 Video Packetizing ---

    /**
     * Enhanced RTMP HEVC Sequence Start packet using FourCC 'hvc1'.
     * Packages VPS, SPS, and PPS into an HVCDecoderConfigurationRecord.
     */
    fun createHevcSequenceStartPacket(
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        timestampMs: Long = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val baos = ByteArrayOutputStream()

        // Enhanced RTMP ExHeader
        // Byte 0: IsExHeader (0x80) | FrameType Keyframe (0x10) | PacketType SequenceStart (0x00) = 0x90
        baos.write(0x90)

        // FourCC = "hvc1" (0x68, 0x76, 0x63, 0x31)
        baos.write('h'.code)
        baos.write('v'.code)
        baos.write('c'.code)
        baos.write('1'.code)

        // HEVCDecoderConfigurationRecord
        baos.write(0x01) // configurationVersion = 1

        // Parse profile/tier/level from SPS if available
        val generalProfileSpace = if (sps.size > 1) (sps[1].toInt() shr 6) and 0x03 else 0
        val generalTierFlag = if (sps.size > 1) (sps[1].toInt() shr 5) and 0x01 else 0
        val generalProfileIdc = if (sps.size > 1) sps[1].toInt() and 0x1F else 1 // Main profile
        baos.write((generalProfileSpace shl 6) or (generalTierFlag shl 5) or generalProfileIdc)

        // Profile compatibility flags (4 bytes)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // Constraint indicator flags (6 bytes)
        for (i in 0 until 6) baos.write(0x00)

        baos.write(if (sps.size > 12) sps[12].toInt() and 0xFF else 120) // general_level_idc
        baos.write(0xF0) // min_spatial_segmentation_idc (4 bits reserved 1111)
        baos.write(0x00)
        baos.write(0xFC) // parallelismType (6 bits reserved 111111)
        baos.write(0xFD) // chromaFormat (6 bits reserved 111111, 4:2:0 = 1)
        baos.write(0xF8) // bitDepthLumaMinus8 (5 bits reserved 11111)
        baos.write(0xF8) // bitDepthChromaMinus8 (5 bits reserved 11111)
        baos.write(0x00) // avgFrameRate (2 bytes)
        baos.write(0x00)
        baos.write(0x0F) // constantFrameRate(2), numTemporalLayers(3), temporalIdNested(1), lengthSizeMinusOne(2) = 3 (4 bytes)
        baos.write(0x03) // numOfArrays = 3 (VPS, SPS, PPS)

        // Array 1: VPS (NAL type 32)
        writeHevcNalArray(baos, nalType = 32, listOf(vps))
        // Array 2: SPS (NAL type 33)
        writeHevcNalArray(baos, nalType = 33, listOf(sps))
        // Array 3: PPS (NAL type 34)
        writeHevcNalArray(baos, nalType = 34, listOf(pps))

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = baos.toByteArray(),
            csid = RtmpPacket.CSID_VIDEO
        )
    }

    private fun writeHevcNalArray(baos: ByteArrayOutputStream, nalType: Int, nals: List<ByteArray>) {
        baos.write(0x80 or (nalType and 0x3F)) // array_completeness (1 bit = 1) | reserved (1 bit = 0) | NAL_unit_type (6 bits)
        baos.write((nals.size shr 8) and 0xFF)
        baos.write(nals.size and 0xFF)
        for (nal in nals) {
            baos.write((nal.size shr 8) and 0xFF)
            baos.write(nal.size and 0xFF)
            baos.write(nal)
        }
    }

    /**
     * Enhanced RTMP HEVC Coded Frames packet.
     * Packages length-prefixed NAL units using FourCC 'hvc1'.
     */
    fun createHevcCodedFramePacket(
        nalUnits: List<ByteArray>,
        isKeyframe: Boolean,
        timestampMs: Long,
        compositionTimeMs: Int = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val totalNalSize = nalUnits.sumOf { 4 + it.size }
        // 1 byte ExHeader + 4 bytes FourCC + 3 bytes CompositionTime + NALUs
        val payload = ByteArray(8 + totalNalSize)

        // Byte 0: IsExHeader (0x80) | FrameType (0x10 key, 0x20 inter) | PacketType CodedFrames (0x01)
        payload[0] = (0x80 or (if (isKeyframe) 0x10 else 0x20) or 0x01).toByte()

        // FourCC = "hvc1"
        payload[1] = 'h'.code.toByte()
        payload[2] = 'v'.code.toByte()
        payload[3] = 'c'.code.toByte()
        payload[4] = '1'.code.toByte()

        // Composition Time (3 bytes)
        payload[5] = ((compositionTimeMs shr 16) and 0xFF).toByte()
        payload[6] = ((compositionTimeMs shr 8) and 0xFF).toByte()
        payload[7] = (compositionTimeMs and 0xFF).toByte()

        var offset = 8
        for (nal in nalUnits) {
            val length = nal.size
            payload[offset++] = ((length shr 24) and 0xFF).toByte()
            payload[offset++] = ((length shr 16) and 0xFF).toByte()
            payload[offset++] = ((length shr 8) and 0xFF).toByte()
            payload[offset++] = (length and 0xFF).toByte()
            System.arraycopy(nal, 0, payload, offset, length)
            offset += length
        }

        return RtmpPacket(
            messageType = RtmpPacket.TYPE_VIDEO,
            timestamp = timestampMs,
            streamId = streamId,
            payload = payload,
            csid = RtmpPacket.CSID_VIDEO
        )
    }

    // --- NAL Unit Parser Utility ---

    /**
     * Scans an Annex B byte array and splits it into discrete NAL units
     * by locating 3-byte (0x00 00 01) or 4-byte (0x00 00 00 01) start codes.
     */
    fun extractAnnexBNalUnits(data: ByteArray, offset: Int = 0, length: Int = data.size): List<ByteArray> {
        val nals = mutableListOf<ByteArray>()
        var i = offset
        val end = offset + length
        var nalStart = -1

        while (i <= end - 3) {
            val is4ByteStartCode = (i <= end - 4) &&
                    data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()
            val is3ByteStartCode = !is4ByteStartCode &&
                    data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()

            if (is4ByteStartCode || is3ByteStartCode) {
                if (nalStart != -1) {
                    val nalLength = i - nalStart
                    if (nalLength > 0) {
                        val nal = ByteArray(nalLength)
                        System.arraycopy(data, nalStart, nal, 0, nalLength)
                        nals.add(nal)
                    }
                }
                val codeLength = if (is4ByteStartCode) 4 else 3
                nalStart = i + codeLength
                i += codeLength
            } else {
                i++
            }
        }

        // Add final NAL
        if (nalStart != -1 && nalStart < end) {
            val nalLength = end - nalStart
            if (nalLength > 0) {
                val nal = ByteArray(nalLength)
                System.arraycopy(data, nalStart, nal, 0, nalLength)
                nals.add(nal)
            }
        }

        return nals
    }
}
