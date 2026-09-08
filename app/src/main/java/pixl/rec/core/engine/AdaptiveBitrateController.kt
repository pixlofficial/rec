package pixl.rec.core.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Health telemetry state of the streaming network uplink.
 */
enum class UplinkHealth {
    /** Optimal network conditions, zero packet drops, clean buffer queues. */
    CLEAN,
    /** Transient network congestion detected, rate adaptation in progress. */
    ADAPTING,
    /** Severe packet loss or buffer saturation, throttled to minimum bitrate. */
    CONGESTED
}

/**
 * Adaptive Bitrate Controller (ABR) for RTMP Live Streaming.
 * Continuously evaluates socket buffer queues, backpressure drops, and network throughput,
 * dynamically adjusting hardware [VideoEncoder] bitrates via MediaCodec runtime parameters.
 */
class AdaptiveBitrateController(
    private val scope: CoroutineScope,
    val initialBitrateBps: Int,
    val minBitrateBps: Int = 2_000_000,
    val maxBitrateBps: Int = 14_000_000,
    private val onBitrateAdjusted: (newBitrateBps: Int) -> Unit,
    private val onRequestSyncFrame: () -> Unit = {}
) {
    private val tag = "ABRController"

    private val _currentBitrateBps = MutableStateFlow(initialBitrateBps)
    val currentBitrateBps: StateFlow<Int> = _currentBitrateBps.asStateFlow()

    private val _uplinkHealth = MutableStateFlow(UplinkHealth.CLEAN)
    val uplinkHealth: StateFlow<UplinkHealth> = _uplinkHealth.asStateFlow()

    private val lastEvaluatedDropCount = AtomicLong(0L)
    private val consecutiveCleanSeconds = AtomicInteger(0)
    private val isRunning = AtomicBoolean(false)
    private var tickerJob: Job? = null

    /**
     * Starts the 1-second periodic ABR evaluation loop.
     */
    fun start(currentTotalDroppedFramesProvider: () -> Long) {
        if (isRunning.getAndSet(true)) return

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(1000L) // 1Hz ABR evaluation

                val totalDropped = currentTotalDroppedFramesProvider()
                val prevDropped = lastEvaluatedDropCount.getAndSet(totalDropped)
                val newDrops = (totalDropped - prevDropped).coerceAtLeast(0L)

                evaluateBitrate(newDrops)
            }
        }
    }

    /**
     * Evaluates network health based on new dropped frame delta.
     */
    internal fun evaluateBitrate(newDrops: Long) {
        val current = _currentBitrateBps.value

        if (newDrops > 0) {
            // Congestion detected! Fast Multiplicative Decrease (-25%)
            consecutiveCleanSeconds.set(0)
            val reduced = (current * 0.75).toInt().coerceAtLeast(minBitrateBps)

            _uplinkHealth.value = if (reduced <= minBitrateBps) UplinkHealth.CONGESTED else UplinkHealth.ADAPTING

            if (reduced != current) {
                Log.w(tag, "Network backpressure ($newDrops drops). Stepping down bitrate: ${current / 1000}k -> ${reduced / 1000}k")
                _currentBitrateBps.value = reduced
                onBitrateAdjusted(reduced)
                onRequestSyncFrame() // Request clean IDR sync frame to resync remote player
            }
        } else {
            // Stable transmission
            val cleanSec = consecutiveCleanSeconds.incrementAndGet()

            if (cleanSec >= 4 && current < maxBitrateBps) {
                // Additive Increase (+10% every 4 clean seconds)
                val increased = (current * 1.10).toInt().coerceAtMost(maxBitrateBps)
                Log.i(tag, "Uplink stable for ${cleanSec}s. Probing upward: ${current / 1000}k -> ${increased / 1000}k")
                _currentBitrateBps.value = increased
                onBitrateAdjusted(increased)
                consecutiveCleanSeconds.set(0)
            }

            if (cleanSec >= 2 && _uplinkHealth.value != UplinkHealth.CLEAN) {
                _uplinkHealth.value = UplinkHealth.CLEAN
            }
        }
    }

    /**
     * Stops the ABR controller.
     */
    fun stop() {
        isRunning.set(false)
        tickerJob?.cancel()
        tickerJob = null
    }
}
