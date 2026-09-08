package pixl.rec.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.stream.FlvPacketizer
import pixl.rec.core.stream.RtmpPacket

class StreamOutputTargetTest {

    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
    }

    @Test
    fun testRtmpStreamOutputTarget_initialState() {
        val config = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "test_yt_key",
            enableAbr = true,
            videoBitrate = 6_000_000
        )
        val target = RtmpStreamOutputTarget(
            streamConfig = config,
            scope = testScope!!
        )

        assertNotNull(target.connection)
        assertEquals(config, target.streamConfig)
        target.release()
    }

    @Test
    fun testAvcAndHevcSequenceHeaderCreation() {
        val sps = byteArrayOf(0x67, 0x64, 0x00, 0x1F, 0xAC.toByte())
        val pps = byteArrayOf(0x68, 0xEE.toByte(), 0x3C, 0x80.toByte())

        val avcPacket = FlvPacketizer.createAvcSequenceHeaderPacket(sps, pps)
        assertEquals(RtmpPacket.TYPE_VIDEO, avcPacket.messageType)
        assertEquals(RtmpPacket.CSID_VIDEO, avcPacket.csid)
        assertEquals(0x17.toByte(), avcPacket.payload[0]) // Keyframe AVC

        val vps = byteArrayOf(0x40, 0x01, 0x0C)
        val hevcSeqPacket = FlvPacketizer.createHevcSequenceStartPacket(vps, sps, pps)
        assertEquals(RtmpPacket.TYPE_VIDEO, hevcSeqPacket.messageType)
        assertEquals(0x90.toByte(), hevcSeqPacket.payload[0]) // ExHeader + Keyframe + SeqStart
        assertEquals("hvc1", String(hevcSeqPacket.payload, 1, 4))
    }

    @Test
    fun testAacAscCreation() {
        val asc = FlvPacketizer.createAudioSpecificConfig(sampleRate = 48000, channels = 2)
        assertEquals(2, asc.size)
        val aacHeaderPacket = FlvPacketizer.createAacSequenceHeaderPacket(48000, 2)
        assertEquals(RtmpPacket.TYPE_AUDIO, aacHeaderPacket.messageType)
        assertEquals(0xAF.toByte(), aacHeaderPacket.payload[0])
    }

    @Test
    fun testExponentialBackoffCalculation() {
        fun computeBackoff(attempt: Int): Long {
            return (1000L * (1 shl (attempt - 1))).coerceAtMost(8000L)
        }

        assertEquals(1000L, computeBackoff(1))
        assertEquals(2000L, computeBackoff(2))
        assertEquals(4000L, computeBackoff(3))
        assertEquals(8000L, computeBackoff(4))
        assertEquals(8000L, computeBackoff(5))
    }
}
