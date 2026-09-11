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

    /**
     * Strips 3-byte (0x00 00 01) or 4-byte (0x00 00 00 01) Annex B start code prefix if present.
     */
    fun stripStartCode(nal: ByteArray): ByteArray {
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            val clean = ByteArray(nal.size - 4)
            System.arraycopy(nal, 4, clean, 0, clean.size)
            return clean
        }
        if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()) {
            val clean = ByteArray(nal.size - 3)
            System.arraycopy(nal, 3, clean, 0, clean.size)
            return clean
        }
        return nal
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
        val cleanSps = stripStartCode(sps)
        val cleanPps = stripStartCode(pps)
        val baos = ByteArrayOutputStream()

        // FLV Video Tag Header (AVC Keyframe Sequence Header)
        baos.write(0x17) // Keyframe (0x10) | CodecID AVC (0x07)
        baos.write(0x00) // AVCPacketType = 0 (Sequence Header)
        baos.write(0x00) // CompositionTime (3 bytes: 0)
        baos.write(0x00)
        baos.write(0x00)

        // AVCDecoderConfigurationRecord (ISO/IEC 14496-15)
        baos.write(0x01) // configurationVersion = 1
        baos.write((if (cleanSps.size > 1) cleanSps[1].toInt() and 0xFF else 0x64)) // AVCProfileIndication
        baos.write((if (cleanSps.size > 2) cleanSps[2].toInt() and 0xFF else 0x00)) // profile_compatibility
        baos.write((if (cleanSps.size > 3) cleanSps[3].toInt() and 0xFF else 0x1F)) // AVCLevelIndication
        baos.write(0xFF) // 6 bits reserved (111111) | lengthSizeMinusOne = 3 (4-byte NAL length)
        baos.write(0xE1) // 3 bits reserved (111) | numOfSequenceParameterSets = 1

        // SPS Length (2 bytes) + SPS
        baos.write((cleanSps.size shr 8) and 0xFF)
        baos.write(cleanSps.size and 0xFF)
        baos.write(cleanSps)

        // PPS Count (1) + PPS Length (2 bytes) + PPS
        baos.write(0x01)
        baos.write((cleanPps.size shr 8) and 0xFF)
        baos.write(cleanPps.size and 0xFF)
        baos.write(cleanPps)

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
     * Packages VPS, SPS, and PPS into an HVCDecoderConfigurationRecord based on standards-aware parsed SPS parameters.
     */
    fun createHevcSequenceStartPacket(
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        timestampMs: Long = 0,
        streamId: Int = 1
    ): RtmpPacket {
        val cleanVps = stripStartCode(vps)
        val cleanSps = stripStartCode(sps)
        val cleanPps = stripStartCode(pps)

        val spsInfo = HevcParser.parseSps(cleanSps)
        val baos = ByteArrayOutputStream()

        // Enhanced RTMP ExHeader
        // Byte 0: IsExHeader (0x80) | FrameType Keyframe (0x10) | PacketType SequenceStart (0x00) = 0x90
        baos.write(0x90)

        // FourCC = "hvc1" (0x68, 0x76, 0x63, 0x31)
        baos.write('h'.code)
        baos.write('v'.code)
        baos.write('c'.code)
        baos.write('1'.code)

        // HVCDecoderConfigurationRecord (ISO/IEC 14496-15 Section 8.3.3.1.2)
        baos.write(0x01) // configurationVersion = 1

        // general_profile_space (2) | general_tier_flag (1) | general_profile_idc (5)
        baos.write((spsInfo.profileSpace shl 6) or (spsInfo.tierFlag shl 5) or (spsInfo.profileIdc and 0x1F))

        // general_profile_compatibility_flags (4 bytes)
        baos.write(spsInfo.profileCompatibilityFlags)

        // general_constraint_indicator_flags (6 bytes)
        baos.write(spsInfo.constraintIndicatorFlags)

        // general_level_idc (1 byte)
        baos.write(spsInfo.levelIdc and 0xFF)

        // min_spatial_segmentation_idc (4 bits reserved 1111 | 12 bits value)
        baos.write(0xF0)
        baos.write(0x00)

        // parallelismType (6 bits reserved 111111 | 2 bits value = 0)
        baos.write(0xFC)

        // chromaFormat (6 bits reserved 111111 | 2 bits chroma_format_idc)
        baos.write(0xFC or (spsInfo.chromaFormatIdc and 0x03))

        // bitDepthLumaMinus8 (5 bits reserved 11111 | 3 bits value)
        baos.write(0xF8 or (spsInfo.bitDepthLumaMinus8 and 0x07))

        // bitDepthChromaMinus8 (5 bits reserved 11111 | 3 bits value)
        baos.write(0xF8 or (spsInfo.bitDepthChromaMinus8 and 0x07))

        // avgFrameRate (2 bytes = 0)
        baos.write(0x00)
        baos.write(0x00)

        // constantFrameRate(2) | numTemporalLayers(3) | temporalIdNested(1) | lengthSizeMinusOne(2)
        val constantFrameRate = 0
        val numTemporalLayers = spsInfo.numTemporalLayers.coerceIn(1, 7)
        val temporalIdNested = if (spsInfo.temporalIdNested) 1 else 0
        val lengthSizeMinusOne = 3 // 4-byte NAL lengths
        baos.write((constantFrameRate shl 6) or (numTemporalLayers shl 3) or (temporalIdNested shl 2) or lengthSizeMinusOne)

        baos.write(0x03) // numOfArrays = 3 (VPS, SPS, PPS)

        // Array 1: VPS (NAL type 32)
        writeHevcNalArray(baos, nalType = 32, listOf(cleanVps))
        // Array 2: SPS (NAL type 33)
        writeHevcNalArray(baos, nalType = 33, listOf(cleanSps))
        // Array 3: PPS (NAL type 34)
        writeHevcNalArray(baos, nalType = 34, listOf(cleanPps))

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
