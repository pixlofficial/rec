package pixl.rec.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCalculatorTest {

    @Test
    fun testMbPerMinuteFormula() {
        // Spec test: 50 Mbps Video + 256 kbps Audio
        // 50,000,000 + 256,000 = 50,256,000 bps
        // Bytes/sec = 50,256,000 / 8 = 6,282,000 B/s
        // MB/min = (6,282,000 / (1024 * 1024)) * 60 ~= 359.46 MB/min
        val mbPerMin = StorageCalculator.calculateMbPerMinute(50_000_000, 256_000)
        assertEquals(359.46, mbPerMin, 0.5)
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", StorageCalculator.formatBytes(0))
        assertEquals("1.0 KB", StorageCalculator.formatBytes(1024))
        assertEquals("1.5 MB", StorageCalculator.formatBytes(1572864))
        assertEquals("2.0 GB", StorageCalculator.formatBytes(2147483648L))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:00", StorageCalculator.formatDuration(0))
        assertEquals("00:45", StorageCalculator.formatDuration(45_000))
        assertEquals("02:15", StorageCalculator.formatDuration(135_000))
        assertEquals("01:05:20", StorageCalculator.formatDuration(3920_000))
    }

    @Test
    fun testIsStorageLowThreshold() {
        val lowBytes = 1_500_000_000L // 1.5 GB
        val okBytes = 5_000_000_000L // 5 GB

        assertTrue(StorageCalculator.isStorageLow(lowBytes))
        assertFalse(StorageCalculator.isStorageLow(okBytes))
    }

    @Test
    fun testIsStorageCriticallyLowThreshold() {
        val criticalBytes = 150_000_000L // 150 MB (< 200 MB)
        val safeBytes = 500_000_000L // 500 MB (> 200 MB)

        assertTrue(StorageCalculator.isStorageCriticallyLow(criticalBytes))
        assertFalse(StorageCalculator.isStorageCriticallyLow(safeBytes))
    }

    @Test
    fun testEstimateRemainingMinutes() {
        // 6,282,000 Bytes/sec (~359.46 MB/min).
        // For 62,820,000 bytes available, should be exactly 10 seconds = 0.1667 minutes
        val remainingMin = StorageCalculator.estimateRemainingMinutes(
            availableBytes = 62_820_000L,
            videoBitrateBps = 50_000_000,
            audioBitrateBps = 256_000
        )
        assertEquals(0.1667, remainingMin, 0.01)
    }
}
