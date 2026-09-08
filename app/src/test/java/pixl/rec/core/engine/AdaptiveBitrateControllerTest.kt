package pixl.rec.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveBitrateControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testMultiplicativeDecrease_onDrops() {
        val adjustedBitrate = AtomicInteger(0)
        val syncFrameRequested = AtomicBoolean(false)

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            onBitrateAdjusted = { adjustedBitrate.set(it) },
            onRequestSyncFrame = { syncFrameRequested.set(true) }
        )

        // Initial check
        assertEquals(10_000_000, abr.currentBitrateBps.value)
        assertEquals(UplinkHealth.CLEAN, abr.uplinkHealth.value)

        // Simulate 5 dropped frames
        abr.evaluateBitrate(newDrops = 5)

        // Should reduce by 25% (10M * 0.75 = 7.5M)
        assertEquals(7_500_000, abr.currentBitrateBps.value)
        assertEquals(7_500_000, adjustedBitrate.get())
        assertTrue(syncFrameRequested.get())
        assertEquals(UplinkHealth.ADAPTING, abr.uplinkHealth.value)
    }

    @Test
    fun testMinBitrateFloorClamping() {
        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 2_500_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            onBitrateAdjusted = {},
            onRequestSyncFrame = {}
        )

        // 2.5M * 0.75 = 1.875M, which is below minBitrate (2M)
        abr.evaluateBitrate(newDrops = 10)

        assertEquals(2_000_000, abr.currentBitrateBps.value)
        assertEquals(UplinkHealth.CONGESTED, abr.uplinkHealth.value)
    }

    @Test
    fun testAdditiveIncrease_onCleanNetwork() {
        val adjustedBitrate = AtomicInteger(0)

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 6_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            onBitrateAdjusted = { adjustedBitrate.set(it) },
            onRequestSyncFrame = {}
        )

        // 3 clean seconds -> no increase yet (threshold is 4)
        abr.evaluateBitrate(0)
        abr.evaluateBitrate(0)
        abr.evaluateBitrate(0)
        assertEquals(6_000_000, abr.currentBitrateBps.value)

        // 4th clean second -> +10% (6M * 1.10 = 6.6M)
        abr.evaluateBitrate(0)
        assertEquals(6_600_000, abr.currentBitrateBps.value)
        assertEquals(6_600_000, adjustedBitrate.get())
        assertEquals(UplinkHealth.CLEAN, abr.uplinkHealth.value)
    }

    @Test
    fun testMaxBitrateCeilingClamping() {
        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 9_500_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            onBitrateAdjusted = {},
            onRequestSyncFrame = {}
        )

        // 4 clean seconds -> 9.5M * 1.10 = 10.45M, clamped to 10M
        for (i in 0 until 4) {
            abr.evaluateBitrate(0)
        }

        assertEquals(10_000_000, abr.currentBitrateBps.value)
    }
}
