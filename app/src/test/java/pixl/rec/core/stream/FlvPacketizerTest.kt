package pixl.rec.core.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FlvPacketizerTest {

    @Test
    fun testAudioSpecificConfig_48000Hz_stereo() {
        val asc = FlvPacketizer.createAudioSpecificConfig(sampleRate = 48000, channels = 2)
        assertEquals(2, asc.size)
        // 48 kHz stereo is 0x11, 0x90
        assertEquals(0x11.toByte(), asc[0])
        assertEquals(0x90.toByte(), asc[1])
    }

    @Test
    fun testAudioSpecificConfig_44100Hz_stereo() {
        val asc = FlvPacketizer.createAudioSpecificConfig(sampleRate = 44100, channels = 2)
        assertEquals(2, asc.size)
        // 44.1 kHz stereo is 0x12, 0x10
        assertEquals(0x12.toByte(), asc[0])
        assertEquals(0x10.toByte(), asc[1])
    }

    @Test
    fun testAacSequenceHeaderPacket() {
        val packet = FlvPacketizer.createAacSequenceHeaderPacket(sampleRate = 48000, channels = 2)
        assertEquals(RtmpPacket.TYPE_AUDIO, packet.messageType)
        assertEquals(RtmpPacket.CSID_AUDIO, packet.csid)

        val payload = packet.payload
        assertEquals(4, payload.size) // 2-byte tag header + 2-byte ASC
        assertEquals(0xAF.toByte(), payload[0]) // AAC, 44.1k/48k, 16-bit, stereo
        assertEquals(0x00.toByte(), payload[1]) // Sequence Header
        assertEquals(0x11.toByte(), payload[2])
        assertEquals(0x90.toByte(), payload[3])
    }

    @Test
    fun testAacFramePacket_stripsAdtsHeader() {
        // Mock ADTS header (7 bytes) + 4 bytes raw payload
        val rawAudio = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val adtsPacket = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), 0x50.toByte(), 0x80.toByte(),
            0x01.toByte(), 0x3F.toByte(), 0xFC.toByte()
        ) + rawAudio

        val packet = FlvPacketizer.createAacFramePacket(
            data = adtsPacket,
            timestampMs = 100L
        )

        val payload = packet.payload
        assertEquals(2 + rawAudio.size, payload.size)
        assertEquals(0xAF.toByte(), payload[0])
        assertEquals(0x01.toByte(), payload[1]) // Raw AAC frame

        val extractedPayload = ByteArray(rawAudio.size)
        System.arraycopy(payload, 2, extractedPayload, 0, rawAudio.size)
        assertArrayEquals(rawAudio, extractedPayload)
    }

    @Test
    fun testAvcSequenceHeaderPacket() {
        val sps = byteArrayOf(0x67, 0x64, 0x00, 0x1F, 0xAC.toByte())
        val pps = byteArrayOf(0x68, 0xEE.toByte(), 0x3C, 0x80.toByte())

        val packet = FlvPacketizer.createAvcSequenceHeaderPacket(sps, pps)
        assertEquals(RtmpPacket.TYPE_VIDEO, packet.messageType)
        assertEquals(RtmpPacket.CSID_VIDEO, packet.csid)

        val payload = packet.payload
        assertEquals(0x17.toByte(), payload[0]) // Keyframe (0x10) | AVC (0x07)
        assertEquals(0x00.toByte(), payload[1]) // AVCPacketType = 0 (Sequence Header)

        // CompositionTime = 0
        assertEquals(0x00.toByte(), payload[2])
        assertEquals(0x00.toByte(), payload[3])
        assertEquals(0x00.toByte(), payload[4])

        // AVCDecoderConfigurationRecord starts at offset 5
        assertEquals(0x01.toByte(), payload[5]) // configurationVersion = 1
        assertEquals(sps[1], payload[6])        // profile
        assertEquals(sps[2], payload[7])        // profile_compat
        assertEquals(sps[3], payload[8])        // level
    }

    @Test
    fun testAvcFramePacket_lengthPrefixes() {
        val nal1 = byteArrayOf(0x65, 0x88.toByte(), 0x84.toByte())
        val nal2 = byteArrayOf(0x65, 0x88.toByte(), 0x85.toByte())

        val packet = FlvPacketizer.createAvcFramePacket(
            nalUnits = listOf(nal1, nal2),
            isKeyframe = true,
            timestampMs = 250L
        )

        val payload = packet.payload
        assertEquals(0x17.toByte(), payload[0]) // Keyframe AVC
        assertEquals(0x01.toByte(), payload[1]) // AVCPacketType = 1 (NALU)

        // First NALU length (4 bytes)
        val buf = ByteBuffer.wrap(payload, 5, payload.size - 5)
        val len1 = buf.int
        assertEquals(nal1.size, len1)

        val extractedNal1 = ByteArray(len1)
        buf.get(extractedNal1)
        assertArrayEquals(nal1, extractedNal1)

        // Second NALU
        val len2 = buf.int
        assertEquals(nal2.size, len2)
        val extractedNal2 = ByteArray(len2)
        buf.get(extractedNal2)
        assertArrayEquals(nal2, extractedNal2)
    }

    @Test
    fun testEnhancedRtmpHevc_sequenceStartAndCodedFrame() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C)
        val sps = byteArrayOf(0x42, 0x01, 0x01, 0x01, 0x60, 0x00, 0x00, 0x03, 0x00, 0x90.toByte(), 0x00, 0x00, 0x03, 0x00, 0x00, 0x78)
        val pps = byteArrayOf(0x44, 0x01, 0xC0.toByte())

        val seqPacket = FlvPacketizer.createHevcSequenceStartPacket(vps, sps, pps)
        val seqPayload = seqPacket.payload

        // Byte 0: 0x90 = IsExHeader (0x80) | Keyframe (0x10) | SequenceStart (0x00)
        assertEquals(0x90.toByte(), seqPayload[0])

        // FourCC = "hvc1"
        val fourCC = String(seqPayload, 1, 4)
        assertEquals("hvc1", fourCC)

        // Test Coded Frame
        val nal = byteArrayOf(0x26, 0x01, 0xAF.toByte())
        val framePacket = FlvPacketizer.createHevcCodedFramePacket(
            nalUnits = listOf(nal),
            isKeyframe = true,
            timestampMs = 500L
        )
        val framePayload = framePacket.payload

        // Byte 0: 0x91 = IsExHeader (0x80) | Keyframe (0x10) | CodedFrames (0x01)
        assertEquals(0x91.toByte(), framePayload[0])
        assertEquals("hvc1", String(framePayload, 1, 4))

        val frameBuf = ByteBuffer.wrap(framePayload, 8, framePayload.size - 8)
        val nalLen = frameBuf.int
        assertEquals(nal.size, nalLen)
    }

    @Test
    fun testExtractAnnexBNalUnits() {
        val nal1 = byteArrayOf(0x67, 0x42, 0xE0.toByte())
        val nal2 = byteArrayOf(0x68, 0xCE.toByte(), 0x3C)
        val stream = byteArrayOf(0x00, 0x00, 0x00, 0x01) + nal1 +
                byteArrayOf(0x00, 0x00, 0x01) + nal2

        val extracted = FlvPacketizer.extractAnnexBNalUnits(stream)
        assertEquals(2, extracted.size)
        assertArrayEquals(nal1, extracted[0])
        assertArrayEquals(nal2, extracted[1])
    }
}
