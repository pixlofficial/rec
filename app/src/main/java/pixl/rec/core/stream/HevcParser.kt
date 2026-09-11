package pixl.rec.core.stream

/**
 * Standards-compliant ISO/IEC 23008-2 (H.265 / HEVC) SPS parser and bitstream reader.
 *
 * Unescapes Emulation Prevention Bytes (0x00 00 03 -> 0x00 00) and extracts profile, tier,
 * level, compatibility flags, constraint indicator flags, chroma format, and bit depth
 * required to build valid [HVCDecoderConfigurationRecord] headers (ISO/IEC 14496-15).
 */
object HevcParser {

    /**
     * Decoded HEVC Sequence Parameter Set (SPS) metadata.
     */
    data class HevcSpsInfo(
        val profileSpace: Int = 0,
        val tierFlag: Int = 0,
        val profileIdc: Int = 1, // Main Profile = 1, Main 10 = 2
        val profileCompatibilityFlags: ByteArray = byteArrayOf(0x60.toByte(), 0x00, 0x00, 0x00),
        val constraintIndicatorFlags: ByteArray = ByteArray(6),
        val levelIdc: Int = 120, // Level 4.0 = 120
        val chromaFormatIdc: Int = 1, // 4:2:0 = 1
        val bitDepthLumaMinus8: Int = 0, // 8-bit = 0, 10-bit = 2
        val bitDepthChromaMinus8: Int = 0,
        val picWidth: Int = 1920,
        val picHeight: Int = 1080,
        val numTemporalLayers: Int = 1,
        val temporalIdNested: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HevcSpsInfo

            if (profileSpace != other.profileSpace) return false
            if (tierFlag != other.tierFlag) return false
            if (profileIdc != other.profileIdc) return false
            if (!profileCompatibilityFlags.contentEquals(other.profileCompatibilityFlags)) return false
            if (!constraintIndicatorFlags.contentEquals(other.constraintIndicatorFlags)) return false
            if (levelIdc != other.levelIdc) return false
            if (chromaFormatIdc != other.chromaFormatIdc) return false
            if (bitDepthLumaMinus8 != other.bitDepthLumaMinus8) return false
            if (bitDepthChromaMinus8 != other.bitDepthChromaMinus8) return false
            if (picWidth != other.picWidth) return false
            if (picHeight != other.picHeight) return false
            if (numTemporalLayers != other.numTemporalLayers) return false
            if (temporalIdNested != other.temporalIdNested) return false

            return true
        }

        override fun hashCode(): Int {
            var result = profileSpace
            result = 31 * result + tierFlag
            result = 31 * result + profileIdc
            result = 31 * result + profileCompatibilityFlags.contentHashCode()
            result = 31 * result + constraintIndicatorFlags.contentHashCode()
            result = 31 * result + levelIdc
            result = 31 * result + chromaFormatIdc
            result = 31 * result + bitDepthLumaMinus8
            result = 31 * result + bitDepthChromaMinus8
            result = 31 * result + picWidth
            result = 31 * result + picHeight
            result = 31 * result + numTemporalLayers
            result = 31 * result + temporalIdNested.hashCode()
            return result
        }
    }

    /**
     * Removes emulation prevention bytes (0x00 0x00 0x03) from Annex B / EBSP data
     * to recover the raw byte sequence payload (RBSP).
     */
    fun unescapeEbsp(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray {
        val out = ByteArray(length)
        var outIndex = 0
        var i = offset
        val end = offset + length

        while (i < end) {
            if (i <= end - 3 && data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 3.toByte()) {
                out[outIndex++] = 0
                out[outIndex++] = 0
                i += 3 // Skip the 0x03 emulation prevention byte
            } else {
                out[outIndex++] = data[i++]
            }
        }

        val result = ByteArray(outIndex)
        System.arraycopy(out, 0, result, 0, outIndex)
        return result
    }

    /**
     * Helper bit reader for Exp-Golomb and arbitrary bit length decoding.
     */
    class BitReader(private val buffer: ByteArray) {
        private var byteOffset = 0
        private var bitOffset = 0

        fun hasMoreBits(): Boolean = byteOffset < buffer.size

        fun readBits(numBits: Int): Int {
            if (numBits <= 0) return 0
            var result = 0
            for (i in 0 until numBits) {
                result = (result shl 1) or readBit()
            }
            return result
        }

        fun readBit(): Int {
            if (byteOffset >= buffer.size) return 0
            val currentByte = buffer[byteOffset].toInt() and 0xFF
            val bit = (currentByte shr (7 - bitOffset)) and 1
            bitOffset++
            if (bitOffset == 8) {
                bitOffset = 0
                byteOffset++
            }
            return bit
        }

        fun readBytes(count: Int): ByteArray {
            val bytes = ByteArray(count)
            for (i in 0 until count) {
                bytes[i] = readBits(8).toByte()
            }
            return bytes
        }

        fun readUe(): Int {
            var leadingZeros = 0
            while (readBit() == 0 && hasMoreBits() && leadingZeros < 32) {
                leadingZeros++
            }
            if (leadingZeros == 0) return 0
            return ((1 shl leadingZeros) - 1) + readBits(leadingZeros)
        }

        fun skipBits(numBits: Int) {
            for (i in 0 until numBits) {
                readBit()
            }
        }
    }

    /**
     * Parses a raw HEVC SPS NAL unit (with or without start code) and extracts codec metadata.
     * Falls back to standard Main Profile, Level 4.0 if parsing encounters truncated data.
     */
    fun parseSps(spsNal: ByteArray): HevcSpsInfo {
        try {
            // Strip any Annex B start code prefix (0x00 00 00 01 or 0x00 00 01)
            var startOffset = 0
            if (spsNal.size >= 4 && spsNal[0] == 0.toByte() && spsNal[1] == 0.toByte() && spsNal[2] == 0.toByte() && spsNal[3] == 1.toByte()) {
                startOffset = 4
            } else if (spsNal.size >= 3 && spsNal[0] == 0.toByte() && spsNal[1] == 0.toByte() && spsNal[2] == 1.toByte()) {
                startOffset = 3
            }

            val nalBytes = if (startOffset > 0) {
                val cleaned = ByteArray(spsNal.size - startOffset)
                System.arraycopy(spsNal, startOffset, cleaned, 0, cleaned.size)
                cleaned
            } else {
                spsNal
            }

            if (nalBytes.size < 4) return HevcSpsInfo()

            // Verify NAL unit type is SPS (type 33: (byte[0] >> 1) & 0x3F == 33)
            val nalType = (nalBytes[0].toInt() shr 1) and 0x3F
            if (nalType != 33) {
                // Not an SPS NAL unit
                return HevcSpsInfo()
            }

            // Unescape EBSP to RBSP (skip 2-byte NAL header)
            val rbsp = unescapeEbsp(nalBytes, offset = 2)
            val reader = BitReader(rbsp)

            // sps_video_parameter_set_id: u(4)
            reader.readBits(4)
            // sps_max_sub_layers_minus1: u(3)
            val maxSubLayersMinus1 = reader.readBits(3)
            // sps_temporal_id_nesting_flag: u(1)
            val temporalIdNestingFlag = reader.readBit() == 1

            // --- profile_tier_level(1, sps_max_sub_layers_minus1) ---
            val profileSpace = reader.readBits(2)
            val tierFlag = reader.readBit()
            val profileIdc = reader.readBits(5)

            // general_profile_compatibility_flags: 32 bits (4 bytes)
            val profileCompatibilityFlags = reader.readBytes(4)

            // general_constraint_indicator_flags: 48 bits (6 bytes)
            val constraintIndicatorFlags = reader.readBytes(6)

            // general_level_idc: u(8)
            val levelIdc = reader.readBits(8)

            // Read sub-layer flags
            val subLayerProfilePresentFlags = BooleanArray(maxSubLayersMinus1)
            val subLayerLevelPresentFlags = BooleanArray(maxSubLayersMinus1)
            for (i in 0 until maxSubLayersMinus1) {
                subLayerProfilePresentFlags[i] = reader.readBit() == 1
                subLayerLevelPresentFlags[i] = reader.readBit() == 1
            }

            if (maxSubLayersMinus1 > 0) {
                for (i in maxSubLayersMinus1 until 8) {
                    reader.readBits(2) // reserved zero bits
                }
            }

            for (i in 0 until maxSubLayersMinus1) {
                if (subLayerProfilePresentFlags[i]) {
                    reader.readBits(2 + 1 + 5) // profile_space, tier, profile_idc
                    reader.readBytes(4) // sub_layer_profile_compatibility_flags
                    reader.readBytes(6) // sub_layer_constraint_indicator_flags
                }
                if (subLayerLevelPresentFlags[i]) {
                    reader.readBits(8) // sub_layer_level_idc
                }
            }

            // sps_seq_parameter_set_id: ue(v)
            reader.readUe()

            // chroma_format_idc: ue(v) (0=mono, 1=4:2:0, 2=4:2:2, 3=4:4:4)
            val chromaFormatIdc = reader.readUe()
            if (chromaFormatIdc == 3) {
                reader.readBit() // separate_colour_plane_flag
            }

            // pic_width_in_luma_samples: ue(v)
            val picWidth = reader.readUe()
            // pic_height_in_luma_samples: ue(v)
            val picHeight = reader.readUe()

            // conformance_window_flag: u(1)
            val conformanceWindow = reader.readBit() == 1
            if (conformanceWindow) {
                reader.readUe() // conf_win_left_offset
                reader.readUe() // conf_win_right_offset
                reader.readUe() // conf_win_top_offset
                reader.readUe() // conf_win_bottom_offset
            }

            // bit_depth_luma_minus8: ue(v)
            val bitDepthLumaMinus8 = reader.readUe()
            // bit_depth_chroma_minus8: ue(v)
            val bitDepthChromaMinus8 = reader.readUe()

            return HevcSpsInfo(
                profileSpace = profileSpace,
                tierFlag = tierFlag,
                profileIdc = if (profileIdc > 0) profileIdc else 1,
                profileCompatibilityFlags = profileCompatibilityFlags,
                constraintIndicatorFlags = constraintIndicatorFlags,
                levelIdc = if (levelIdc > 0) levelIdc else 120,
                chromaFormatIdc = chromaFormatIdc.coerceIn(0, 3),
                bitDepthLumaMinus8 = bitDepthLumaMinus8.coerceIn(0, 8),
                bitDepthChromaMinus8 = bitDepthChromaMinus8.coerceIn(0, 8),
                picWidth = if (picWidth > 0) picWidth else 1920,
                picHeight = if (picHeight > 0) picHeight else 1080,
                numTemporalLayers = maxSubLayersMinus1 + 1,
                temporalIdNested = temporalIdNestingFlag
            )
        } catch (_: Exception) {
            // Safe fallback on truncated/corrupted SPS data
            return HevcSpsInfo()
        }
    }
}
