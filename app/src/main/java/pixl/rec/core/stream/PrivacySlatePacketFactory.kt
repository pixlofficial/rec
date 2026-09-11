package pixl.rec.core.stream

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for synthesizing standards-compliant H.264 (AVC) and H.265 (HEVC)
 * solid black / privacy slate IDR keyframes for live stream isolation.
 *
 * Emits valid NAL units with CAVLC macroblock data and compliant FLV / Enhanced RTMP
 * encapsulation, allowing RTMP servers and decoders to display a continuous black slate
 * with zero risk of sensitive screen content leakage.
 */
object PrivacySlatePacketFactory {

    private val avcSliceCache = ConcurrentHashMap<Pair<Int, Int>, ByteArray>()

    /**
     * BitWriter helper for packing Exp-Golomb and arbitrary bit fields.
     */
    class BitWriter {
        private val bytes = ByteArrayOutputStream()
        private var currentByte = 0
        private var bitOffset = 0 // 0 to 7

        fun writeBit(bit: Int) {
            currentByte = (currentByte shl 1) or (bit and 1)
            bitOffset++
            if (bitOffset == 8) {
                bytes.write(currentByte)
                currentByte = 0
                bitOffset = 0
            }
        }

        fun writeBits(value: Int, numBits: Int) {
            for (i in (numBits - 1) downTo 0) {
                writeBit((value shr i) and 1)
            }
        }

        fun writeUe(value: Int) {
            val v = value + 1
            val leadingZeros = 31 - Integer.numberOfLeadingZeros(v)
            for (i in 0 until leadingZeros) {
                writeBit(0)
            }
            writeBit(1)
            for (i in (leadingZeros - 1) downTo 0) {
                writeBit((v shr i) and 1)
            }
        }

        fun writeSe(value: Int) {
            val codeNum = if (value <= 0) -2 * value else 2 * value - 1
            writeUe(codeNum)
        }

        fun writeRbspTrailingBits() {
            writeBit(1) // rbsp_stop_one_bit
            while (bitOffset != 0) {
                writeBit(0) // rbsp_alignment_zero_bit
            }
        }

        fun toByteArray(): ByteArray {
            if (bitOffset != 0) {
                writeRbspTrailingBits()
            }
            return bytes.toByteArray()
        }
    }

    /**
     * Generates a compliant H.264 / AVC IDR slice NAL unit containing all-black
     * CAVLC I_16x16 macroblocks with zero transform coefficients.
     */
    fun getOrCreateAvcBlackSliceNal(width: Int, height: Int): ByteArray {
        val key = Pair(width, height)
        return avcSliceCache.computeIfAbsent(key) {
            val mbWidth = (width + 15) / 16
            val mbHeight = (height + 15) / 16
            val totalMbs = mbWidth * mbHeight

            // 1. NAL Unit Header: forbidden_zero_bit(0), nal_ref_idc(3), nal_unit_type(5 = IDR) -> 0x65
            val rbspWriter = BitWriter()

            // 2. Slice Header
            rbspWriter.writeUe(0) // first_mb_in_slice = 0
            rbspWriter.writeUe(7) // slice_type = 7 (I slice, all macroblocks are I)
            rbspWriter.writeUe(0) // pic_parameter_set_id = 0
            rbspWriter.writeBits(0, 4) // frame_num = 0 (4 bits)
            rbspWriter.writeUe(0) // idr_pic_id = 0
            rbspWriter.writeBits(0, 4) // pic_order_cnt_lsb = 0 (4 bits)
            rbspWriter.writeSe(0) // slice_qp_delta = 0

            // 3. Slice Data: Macroblock loop (CAVLC)
            // For each macroblock:
            // mb_type = 1: I_16x16_0_0_0 (DC intra-prediction, cbp_luma=0, cbp_chroma=0) -> ue(1) = 010 (3 bits)
            // intra_chroma_pred_mode = 0 (DC) -> ue(0) = 1 (1 bit)
            // mb_qp_delta = 0 -> se(0) = 1 (1 bit)
            // Since cbp is 0, no transform coefficients or residual bits are emitted.
            for (i in 0 until totalMbs) {
                rbspWriter.writeBits(0b010, 3) // mb_type = 1
                rbspWriter.writeBit(1) // intra_chroma_pred_mode = 0 (ue: '1')
                rbspWriter.writeBit(1) // mb_qp_delta = 0 (se: '1')
            }

            rbspWriter.writeRbspTrailingBits()
            val rbsp = rbspWriter.toByteArray()

            // Prefix with NAL header byte (0x65)
            val nal = ByteArray(1 + rbsp.size)
            nal[0] = 0x65.toByte() // nal_ref_idc = 3 (0x60) | nal_unit_type = 5 (0x05)
            System.arraycopy(rbsp, 0, nal, 1, rbsp.size)
            nal
        }
    }

    /**
     * Builds an AVC (H.264) FLV video keyframe packet containing a compliant black IDR slice.
     */
    fun createAvcBlackKeyframePacket(
        width: Int,
        height: Int,
        timestampMs: Long,
        streamId: Int = 1
    ): RtmpPacket {
        val nal = getOrCreateAvcBlackSliceNal(width, height)
        return FlvPacketizer.createAvcFramePacket(
            nalUnits = listOf(nal),
            isKeyframe = true,
            timestampMs = timestampMs,
            compositionTimeMs = 0,
            streamId = streamId
        )
    }

    /**
     * Builds an Enhanced RTMP HEVC (H.265) video keyframe packet containing a compliant black IDR slice.
     */
    fun createHevcBlackKeyframePacket(
        width: Int,
        height: Int,
        timestampMs: Long,
        streamId: Int = 1
    ): RtmpPacket {
        val avcNal = getOrCreateAvcBlackSliceNal(width, height)
        // Convert to HEVC IDR_W_RADL NAL (type 19)
        val hevcNal = ByteArray(1 + avcNal.size)
        // Byte 0: forbidden_zero(1) | nal_unit_type(19) | nuh_layer_id(6 bits, 0) -> (19 shl 1) = 0x26
        hevcNal[0] = 0x26.toByte()
        hevcNal[1] = 0x01.toByte() // nuh_temporal_id_plus1 = 1
        System.arraycopy(avcNal, 1, hevcNal, 2, avcNal.size - 1)

        return FlvPacketizer.createHevcCodedFramePacket(
            nalUnits = listOf(hevcNal),
            isKeyframe = true,
            timestampMs = timestampMs,
            compositionTimeMs = 0,
            streamId = streamId
        )
    }

    /**
     * Creates a codec-appropriate black slate keyframe packet.
     */
    fun createSlatePacket(
        isHevc: Boolean,
        width: Int,
        height: Int,
        timestampMs: Long,
        streamId: Int = 1
    ): RtmpPacket {
        return if (isHevc) {
            createHevcBlackKeyframePacket(width, height, timestampMs, streamId)
        } else {
            createAvcBlackKeyframePacket(width, height, timestampMs, streamId)
        }
    }
}
