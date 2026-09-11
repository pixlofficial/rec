package pixl.rec.core.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform

class CodecValidationTest {

    // Valid H.265 (HEVC) SPS NAL unit (NAL unit type 33):
    // Main profile, Level 4.0 (120), 1080p, 4:2:0, 8-bit
    // NAL header: 0x42 0x01 (nal_unit_type = 33)
    private val sampleHevcSps = byteArrayOf(
        0x42.toByte(), 0x01.toByte(), // NAL header (type 33)
        0x01.toByte(), // sps_video_parameter_set_id(0), sps_max_sub_layers_minus1(0), temporal_id_nesting(1)
        0x01.toByte(), // profile_space(0), tier(0), profile_idc(1 = Main)
        0x60.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // profile_compatibility_flags
        0xb0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // constraint flags
        120.toByte(), // general_level_idc = 120 (Level 4.0)
        0xa0.toByte(), // id(0 -> 1) + chroma(1 -> 010) + width leading 4 zeros (0000)
        0x03.toByte(), // width next 6 zeros (000000) + first 2 bits of 1921 (11)
        0xc0.toByte(), // width next 8 bits of 1921 (11000000)
        0x80.toByte(), // width last bit of 1921 (1) + height leading 7 zeros (0000000)
        0x10.toByte(), // height next 3 zeros (000) + first 5 bits of 1081 (10000)
        0xe5.toByte(), // height last 6 bits of 1081 (111001) + conf_win(0) + luma_depth(0 -> 1)
        0xc0.toByte()  // chroma_depth(0 -> 1) + rbsp trailing stop bit (1) + zeros (000000)
    )

    private val sampleHevcVps = byteArrayOf(
        0x40.toByte(), 0x01.toByte(), // NAL header (type 32)
        0x0c.toByte(), 0x01.toByte(), 0xff.toByte(), 0xff.toByte()
    )

    private val sampleHevcPps = byteArrayOf(
        0x44.toByte(), 0x01.toByte(), // NAL header (type 34)
        0xc0.toByte(), 0xf3.toByte(), 0xc0.toByte()
    )

    // Valid H.264 (AVC) SPS & PPS
    // NAL type 7 (0x67): Baseline Profile (0x42), Level 3.1 (0x1F)
    private val sampleAvcSps = byteArrayOf(
        0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1F.toByte(),
        0xE9.toByte(), 0x01.toByte(), 0x40.toByte(), 0x7B.toByte(), 0x40.toByte()
    )
    private val sampleAvcPps = byteArrayOf(
        0x68.toByte(), 0xCE.toByte(), 0x3C.toByte(), 0x80.toByte()
    )

    @Test
    fun testHevcParser_unescapesEmulationPreventionBytes() {
        // EBSP containing 0x00 0x00 0x03 -> should be unescaped to 0x00 0x00
        val ebsp = byteArrayOf(0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x03, 0x02)
        val rbsp = HevcParser.unescapeEbsp(ebsp)

        val expected = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x02)
        assertEquals(expected.size, rbsp.size)
        for (i in expected.indices) {
            assertEquals(expected[i], rbsp[i])
        }
    }

    @Test
    fun testHevcParser_extractsValidProfileTierLevelFromSps() {
        val spsInfo = HevcParser.parseSps(sampleHevcSps)

        assertEquals(0, spsInfo.profileSpace)
        assertEquals(0, spsInfo.tierFlag)
        assertEquals(1, spsInfo.profileIdc) // Main Profile
        assertEquals(120, spsInfo.levelIdc) // Level 4.0
        assertEquals(1, spsInfo.chromaFormatIdc) // 4:2:0
        assertEquals(0, spsInfo.bitDepthLumaMinus8) // 8-bit
        assertEquals(0, spsInfo.bitDepthChromaMinus8)
        assertTrue(spsInfo.temporalIdNested)
    }

    @Test
    fun testHevcSequenceStart_generatesCompliantHvcCRecord() {
        val packet = FlvPacketizer.createHevcSequenceStartPacket(
            vps = sampleHevcVps,
            sps = sampleHevcSps,
            pps = sampleHevcPps,
            timestampMs = 0
        )

        val payload = packet.payload
        assertTrue(payload.size > 27)

        // 1. Enhanced RTMP ExHeader check
        assertEquals(0x90.toByte(), payload[0]) // IsExHeader (0x80) | Keyframe (0x10) | SequenceStart (0x00)
        assertEquals('h'.code.toByte(), payload[1])
        assertEquals('v'.code.toByte(), payload[2])
        assertEquals('c'.code.toByte(), payload[3])
        assertEquals('1'.code.toByte(), payload[4])

        // 2. HVCDecoderConfigurationRecord (ISO/IEC 14496-15)
        assertEquals(0x01.toByte(), payload[5]) // configurationVersion = 1

        // general_profile_space(0) | tier(0) | profile_idc(1) = 0x01
        assertEquals(0x01.toByte(), payload[6])

        // general_level_idc = 120 (byte offset 5 + 12 = 17)
        assertEquals(120.toByte(), payload[17])

        // min_spatial_segmentation_idc
        assertEquals(0xF0.toByte(), payload[18])
        assertEquals(0x00.toByte(), payload[19])

        // parallelismType = 0xFC
        assertEquals(0xFC.toByte(), payload[20])

        // chromaFormat = 0xFC | 1 (4:2:0) = 0xFD
        assertEquals(0xFD.toByte(), payload[21])

        // bitDepthLuma = 0xF8 | 0 = 0xF8
        assertEquals(0xF8.toByte(), payload[22])

        // bitDepthChroma = 0xF8 | 0 = 0xF8
        assertEquals(0xF8.toByte(), payload[23])

        // avgFrameRate (2 bytes: 0) at bytes 24, 25
        assertEquals(0x00.toByte(), payload[24])
        assertEquals(0x00.toByte(), payload[25])

        // constantFrameRate(0) | numTemporalLayers(1) | temporalIdNested(1) | lengthSizeMinusOne(3) = 0x0F
        assertEquals(0x0F.toByte(), payload[26])

        // numOfArrays = 3 (VPS, SPS, PPS) at byte offset 27
        assertEquals(0x03.toByte(), payload[27])

        // Array 1: VPS (NAL type 32 -> 0x80 | 32 = 0xA0) at byte offset 28
        assertEquals(0xA0.toByte(), payload[28])
    }

    @Test
    fun testAvcSequenceHeader_generatesCompliantAvcCRecord() {
        val packet = FlvPacketizer.createAvcSequenceHeaderPacket(
            sps = sampleAvcSps,
            pps = sampleAvcPps,
            timestampMs = 0
        )

        val payload = packet.payload
        // 5 bytes FLV video tag header + AVCDecoderConfigurationRecord
        assertEquals(0x17.toByte(), payload[0]) // Keyframe (0x10) | AVC (0x07)
        assertEquals(0x00.toByte(), payload[1]) // AVCPacketType = 0 (Sequence Header)

        // AVCDecoderConfigurationRecord
        assertEquals(0x01.toByte(), payload[5]) // configurationVersion = 1
        assertEquals(0x42.toByte(), payload[6]) // AVCProfileIndication = Baseline (0x42)
        assertEquals(0x00.toByte(), payload[7]) // profile_compatibility
        assertEquals(0x1F.toByte(), payload[8]) // AVCLevelIndication = 3.1 (0x1F)
        assertEquals(0xFF.toByte(), payload[9]) // lengthSizeMinusOne = 3 (4 bytes)
        assertEquals(0xE1.toByte(), payload[10]) // numOfSps = 1
    }

    @Test
    fun testStartCodeStripping_inSequenceHeaders() {
        // Wrap SPS in 4-byte Annex B start code
        val spsWithStartCode = byteArrayOf(0x00, 0x00, 0x00, 0x01) + sampleAvcSps
        val ppsWithStartCode = byteArrayOf(0x00, 0x00, 0x01) + sampleAvcPps

        val packet = FlvPacketizer.createAvcSequenceHeaderPacket(spsWithStartCode, ppsWithStartCode)
        val payload = packet.payload

        // SPS length in AVCDecoderConfigurationRecord (bytes 11-12) should match raw SPS size (9 bytes), NOT 9+4=13
        val spsLength = ((payload[11].toInt() and 0xFF) shl 8) or (payload[12].toInt() and 0xFF)
        assertEquals(sampleAvcSps.size, spsLength)

        val ppsLengthOffset = 13 + sampleAvcSps.size + 1
        val ppsLength = ((payload[ppsLengthOffset].toInt() and 0xFF) shl 8) or (payload[ppsLengthOffset + 1].toInt() and 0xFF)
        assertEquals(sampleAvcPps.size, ppsLength)
    }

    @Test
    fun testDestinationCodecNegotiation_perPlatform() {
        // 1. YouTube Live: Supports Enhanced RTMP HEVC
        val ytConfig = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "yt_key",
            useEnhancedHevc = true
        )
        assertTrue(ytConfig.effectiveSupportsHevc)

        // 2. Twitch: Mandates AVC / H.264
        val twitchConfig = StreamConfig(
            platform = StreamPlatform.TWITCH,
            streamKey = "twitch_key",
            useEnhancedHevc = true
        )
        assertFalse(twitchConfig.effectiveSupportsHevc)

        // 3. Kick: Mandates AVC / H.264
        val kickConfig = StreamConfig(
            platform = StreamPlatform.KICK,
            streamKey = "kick_key",
            useEnhancedHevc = true
        )
        assertFalse(kickConfig.effectiveSupportsHevc)

        // 4. Multi-stream: YouTube (HEVC) + Twitch (AVC)
        // Must fallback to AVC to ensure Twitch ingest does not choke
        val multiConfig = StreamConfig(
            destinations = listOf(
                StreamDestination(platform = StreamPlatform.YOUTUBE, streamKey = "yt_key"),
                StreamDestination(platform = StreamPlatform.TWITCH, streamKey = "twitch_key")
            ),
            useEnhancedHevc = true
        )
        assertFalse(multiConfig.effectiveSupportsHevc)

        // 5. Multi-stream: YouTube + Custom RTMP (Both support HEVC)
        val multiHevcConfig = StreamConfig(
            destinations = listOf(
                StreamDestination(platform = StreamPlatform.YOUTUBE, streamKey = "yt_key"),
                StreamDestination(platform = StreamPlatform.CUSTOM, streamKey = "custom_key")
            ),
            useEnhancedHevc = true
        )
        assertTrue(multiHevcConfig.effectiveSupportsHevc)
    }
}
