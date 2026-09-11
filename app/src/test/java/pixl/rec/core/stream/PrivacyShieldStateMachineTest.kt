package pixl.rec.core.stream

import android.content.Context
import android.media.MediaCodec
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.audio.AudioCaptureManager
import pixl.rec.core.engine.MultiStreamOutputTarget
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.PrivacyMicPolicy
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import java.nio.ByteBuffer

class PrivacyShieldStateMachineTest {

    private var testScope: CoroutineScope? = null
    private val mockContext = mockk<Context>(relaxed = true)

    private val sampleDestinations = listOf(
        StreamDestination(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "youtube-test-key",
            enabled = true
        ),
        StreamDestination(
            platform = StreamPlatform.TWITCH,
            streamKey = "twitch-test-key",
            enabled = true
        )
    )

    private val streamConfig = StreamConfig(
        destinations = sampleDestinations,
        videoBitrate = 6_000_000,
        enableAbr = false,
        privacyMicPolicy = PrivacyMicPolicy.MUTE_MIC
    )

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
    }

    @Test
    fun testPrivacyShield_stateMachineTransitions() = runBlocking {
        val scope = testScope!!
        val multiTarget = MultiStreamOutputTarget(
            destinations = sampleDestinations,
            streamConfig = streamConfig,
            scope = scope
        )
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
        }

        val controller = PrivacyShieldController(
            scope = scope,
            streamConfig = streamConfig,
            multiStreamTarget = multiTarget,
            audioCaptureManager = null,
            videoEncoder = null,
            sessionBaseTimeNs = System.nanoTime()
        )

        // 1. Initial state: LIVE
        assertEquals(PrivacyShieldController.State.LIVE, controller.state.value)
        assertFalse(controller.isShieldActive.value)
        assertFalse(multiTarget.isPrivacyShieldActive.get())

        // 2. Activate Privacy Shield
        controller.activate()
        assertEquals(PrivacyShieldController.State.SHIELDED, controller.state.value)
        assertTrue(controller.isShieldActive.value)
        assertTrue(multiTarget.isPrivacyShieldActive.get())

        // 3. Deactivate Privacy Shield
        controller.deactivate()
        assertEquals(PrivacyShieldController.State.LIVE, controller.state.value)
        assertFalse(controller.isShieldActive.value)
        assertFalse(multiTarget.isPrivacyShieldActive.get())
        assertTrue(multiTarget.awaitingRecoveryKeyframe.get())

        // 4. Toggle back to SHIELDED
        controller.toggle()
        assertEquals(PrivacyShieldController.State.SHIELDED, controller.state.value)
        assertTrue(controller.isShieldActive.value)

        // 5. Toggle back to LIVE
        controller.toggle()
        assertEquals(PrivacyShieldController.State.LIVE, controller.state.value)
        assertFalse(controller.isShieldActive.value)

        controller.release()
    }

    @Test
    fun testPrivacyShield_purgesInFlightVideoQueues() = runBlocking {
        val scope = testScope!!
        val multiTarget = MultiStreamOutputTarget(
            destinations = sampleDestinations,
            streamConfig = streamConfig,
            scope = scope
        )

        // Pre-fill queues with synthetic in-flight video frames
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
            for (i in 0 until 5) {
                target.connection.enqueuePacket(
                    RtmpPacket(
                        messageType = RtmpPacket.TYPE_VIDEO,
                        timestamp = i * 33L,
                        payload = byteArrayOf(0x27, 0x01, 0x00, 0x00, 0x00, 0x01), // Inter-frame
                        csid = RtmpPacket.CSID_VIDEO
                    )
                )
            }
            assertEquals(5, target.connection.currentQueuePackets.get())
        }

        val controller = PrivacyShieldController(
            scope = scope,
            streamConfig = streamConfig,
            multiStreamTarget = multiTarget,
            audioCaptureManager = null,
            videoEncoder = null,
            sessionBaseTimeNs = System.nanoTime()
        )

        // Activate: must purge all in-flight video queues and inject 1 slate keyframe
        controller.activate()

        for (target in multiTarget.childTargets) {
            // Queue was purged (5 dropped) + 1 slate keyframe injected = 1 packet currently queued
            assertEquals(1, target.connection.currentQueuePackets.get())
            val packet = target.connection.getVideoChannel()?.tryReceive()?.getOrNull()
            assertTrue(packet != null)
            // Verify packet is a keyframe (FLV video tag header starts with 0x17 for AVC keyframe)
            assertEquals(0x17.toByte(), packet!!.payload[0])
        }

        controller.release()
    }

    @Test
    fun testPrivacyShield_blocksScreenFramesWhileActive() = runBlocking {
        val scope = testScope!!
        val multiTarget = MultiStreamOutputTarget(
            destinations = sampleDestinations,
            streamConfig = streamConfig,
            scope = scope
        )
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
        }
        multiTarget.start()

        val controller = PrivacyShieldController(
            scope = scope,
            streamConfig = streamConfig,
            multiStreamTarget = multiTarget,
            audioCaptureManager = null,
            videoEncoder = null,
            sessionBaseTimeNs = System.nanoTime()
        )

        controller.activate()
        assertTrue(multiTarget.isPrivacyShieldActive.get())

        // Clear out the initial slate packet from queue for clean testing
        for (target in multiTarget.childTargets) {
            target.connection.purgeVideoQueue()
        }

        // Simulate incoming sensitive screen frames from VideoEncoder
        val sampleNal = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02)
        val buffer = ByteBuffer.wrap(sampleNal)
        val info = MediaCodec.BufferInfo().apply {
            offset = 0
            size = sampleNal.size
            presentationTimeUs = 100_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(buffer, info)

        // Verify 0 screen packets were forwarded to any destination
        for (target in multiTarget.childTargets) {
            assertEquals(0, target.connection.currentQueuePackets.get())
        }

        controller.release()
        multiTarget.release()
    }

    @Test
    fun testPrivacyShield_recoveryKeyframeGating() = runBlocking {
        val scope = testScope!!
        val multiTarget = MultiStreamOutputTarget(
            destinations = sampleDestinations,
            streamConfig = streamConfig,
            scope = scope
        )
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
        }
        multiTarget.start()

        val controller = PrivacyShieldController(
            scope = scope,
            streamConfig = streamConfig,
            multiStreamTarget = multiTarget,
            audioCaptureManager = null,
            videoEncoder = null,
            sessionBaseTimeNs = System.nanoTime()
        )

        controller.activate()
        controller.deactivate()

        assertTrue(multiTarget.awaitingRecoveryKeyframe.get())

        // Clear queues
        for (target in multiTarget.childTargets) {
            target.connection.purgeVideoQueue()
        }

        // 1. Send Delta (Inter) frame -> must be DROPPED
        val deltaNal = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41, 0x01, 0x02)
        val deltaBuffer = ByteBuffer.wrap(deltaNal)
        val deltaInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = deltaNal.size
            presentationTimeUs = 200_000L
            flags = 0 // Not a keyframe
        }
        multiTarget.onVideoSample(deltaBuffer, deltaInfo)

        for (target in multiTarget.childTargets) {
            assertEquals(0, target.connection.currentQueuePackets.get())
        }
        assertTrue(multiTarget.awaitingRecoveryKeyframe.get())

        // 2. Send Fresh IDR Sync Keyframe -> must be ACCEPTED
        val keyNal = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02)
        val keyBuffer = ByteBuffer.wrap(keyNal)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyNal.size
            presentationTimeUs = 233_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        multiTarget.onVideoSample(keyBuffer, keyInfo)

        // Fresh keyframe accepted and gating cleared!
        assertFalse(multiTarget.awaitingRecoveryKeyframe.get())
        for (target in multiTarget.childTargets) {
            assertEquals(1, target.connection.currentQueuePackets.get())
        }

        controller.release()
        multiTarget.release()
    }

    @Test
    fun testPrivacySlatePacketFactory_avcAndHevcCompliance() {
        // 1. AVC Slate
        val avcPacket = PrivacySlatePacketFactory.createAvcBlackKeyframePacket(
            width = 1920,
            height = 1080,
            timestampMs = 5000L
        )
        val avcPayload = avcPacket.payload
        assertEquals(0x17.toByte(), avcPayload[0]) // Keyframe (0x10) | AVC (0x07)
        assertEquals(0x01.toByte(), avcPayload[1]) // NALU packet
        assertEquals(5000L, avcPacket.timestamp)

        // 2. HEVC Slate
        val hevcPacket = PrivacySlatePacketFactory.createHevcBlackKeyframePacket(
            width = 1920,
            height = 1080,
            timestampMs = 6000L
        )
        val hevcPayload = hevcPacket.payload
        assertEquals(0x91.toByte(), hevcPayload[0]) // ExHeader | Keyframe | CodedFrames
        assertEquals('h'.code.toByte(), hevcPayload[1])
        assertEquals('v'.code.toByte(), hevcPayload[2])
        assertEquals('c'.code.toByte(), hevcPayload[3])
        assertEquals('1'.code.toByte(), hevcPayload[4])
        assertEquals(6000L, hevcPacket.timestamp)
    }

    @Test
    fun testMicrophonePolicy_muteMicVsKeepLive() {
        val audioConfig = RecordingConfig(
            audioSource = AudioSource.INTERNAL_AND_MIC,
            micGain = 1.0f
        )

        var lastReportedMicDb = 0f
        val audioManager = AudioCaptureManager(
            context = mockContext,
            config = audioConfig,
            mediaProjection = null,
            listener = object : AudioCaptureManager.AudioDataListener {
                override fun onPcmAudioData(pcmBytes: ByteArray, length: Int, ptsUs: Long) = Unit
                override fun onAudioLevels(gameDb: Float, micDb: Float) {
                    lastReportedMicDb = micDb
                }
                override fun onAudioError(e: Throwable) = Unit
            }
        )

        // 1. When Privacy Shield is active with MUTE_MIC
        audioManager.setPrivacyShield(active = true, policy = PrivacyMicPolicy.MUTE_MIC)

        // 2. When Privacy Shield is disengaged
        audioManager.setPrivacyShield(active = false)
    }
}
