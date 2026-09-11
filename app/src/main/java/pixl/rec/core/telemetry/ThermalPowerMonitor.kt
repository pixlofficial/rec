package pixl.rec.core.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import pixl.rec.core.model.DeviceBaselineSnapshot
import pixl.rec.core.model.DifferentialTelemetrySnapshot
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Monitors device thermals, battery power, and PSS memory slope.
 * Complies with Section 10 & 12 of the Measured Live Streaming Blueprint:
 * - All metrics are computed differentially against a measured [DeviceBaselineSnapshot]
 *   (e.g., pre-session game-only baseline) rather than universal unsupported guarantees.
 * - Memory slope is computed via linear regression over a sliding window to detect memory leaks.
 * - Thermal throttling transitions are explicitly counted.
 */
class ThermalPowerMonitor(
    private val metricsProvider: MetricsProvider
) {

    constructor(context: Context) : this(DefaultMetricsProvider(context))

    interface MetricsProvider {
        fun getBatteryLevelPercent(): Float
        fun getBatteryTemperatureCelsius(): Float
        fun getBatteryCurrentNowUa(): Long
        fun getThermalStatus(): Int
        fun getProcessPssKb(): Long
        fun getTimestampNs(): Long
    }

    class DefaultMetricsProvider(private val context: Context) : MetricsProvider {
        private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        override fun getBatteryLevelPercent(): Float {
            return runCatching {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100f) / scale else 100f
            }.getOrDefault(100f)
        }

        override fun getBatteryTemperatureCelsius(): Float {
            return runCatching {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
                rawTemp / 10f
            }.getOrDefault(25f)
        }

        override fun getBatteryCurrentNowUa(): Long {
            return runCatching {
                batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
            }.getOrDefault(0L)
        }

        override fun getThermalStatus(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                powerManager.currentThermalStatus
            } else {
                PowerManager.THERMAL_STATUS_NONE
            }
        }

        override fun getProcessPssKb(): Long {
            return runCatching {
                Debug.getPss()
            }.getOrDefault(0L)
        }

        override fun getTimestampNs(): Long = System.nanoTime()
    }

    private data class SamplePoint(
        val timestampNs: Long,
        val pssKb: Long,
        val batteryLevel: Float,
        val tempCelsius: Float,
        val thermalStatus: Int
    )

    private var baseline: DeviceBaselineSnapshot = DeviceBaselineSnapshot()
    private val historyCapacity = 120
    private val history = arrayOfNulls<SamplePoint>(historyCapacity)
    private var historyHead = 0
    private var historyCount = 0

    private var lastThermalStatus = PowerManager.THERMAL_STATUS_NONE
    private val throttlingEventsCount = AtomicInteger(0)
    private var latestSnapshot: DifferentialTelemetrySnapshot = DifferentialTelemetrySnapshot()

    @Synchronized
    fun captureBaseline(gameFps: Float = 0f): DeviceBaselineSnapshot {
        val now = metricsProvider.getTimestampNs()
        val battLevel = metricsProvider.getBatteryLevelPercent()
        val temp = metricsProvider.getBatteryTemperatureCelsius()
        val currentUa = metricsProvider.getBatteryCurrentNowUa()
        val thermal = metricsProvider.getThermalStatus()
        val pss = metricsProvider.getProcessPssKb()

        baseline = DeviceBaselineSnapshot(
            timestampNs = now,
            batteryLevelPercent = battLevel,
            batteryTempCelsius = temp,
            batteryCurrentNowUa = currentUa,
            thermalStatus = thermal,
            pssMemoryKb = pss,
            gameFps = gameFps
        )

        historyHead = 0
        historyCount = 0
        lastThermalStatus = thermal
        throttlingEventsCount.set(0)

        recordSamplePoint(SamplePoint(now, pss, battLevel, temp, thermal))

        latestSnapshot = DifferentialTelemetrySnapshot(
            baseline = baseline,
            elapsedDurationMs = 0L,
            currentBatteryTempCelsius = temp,
            deltaBatteryTempCelsius = 0f,
            batteryDrainRatePercentPerHour = 0f,
            batteryCurrentNowMa = abs(currentUa) / 1000f,
            currentThermalStatus = thermal,
            thermalThrottlingEvents = 0,
            currentPssMemoryKb = pss,
            memorySlopeKbPerHour = 0.0,
            encoderFps = 0f,
            gameFps = gameFps,
            deltaGameFps = 0f,
            activeDestinationsCount = 0,
            socketWriteLatencyP95Ms = 0L,
            totalThroughputBps = 0L
        )

        return baseline
    }

    @Synchronized
    fun recordSample(
        encoderFps: Float = 0f,
        currentGameFps: Float = 0f,
        activeDestinations: Int = 1,
        socketWriteP95Ms: Long = 0L,
        totalThroughputBps: Long = 0L
    ): DifferentialTelemetrySnapshot {
        val now = metricsProvider.getTimestampNs()
        val battLevel = metricsProvider.getBatteryLevelPercent()
        val temp = metricsProvider.getBatteryTemperatureCelsius()
        val currentUa = metricsProvider.getBatteryCurrentNowUa()
        val thermal = metricsProvider.getThermalStatus()
        val pss = metricsProvider.getProcessPssKb()

        // Thermal throttling transition detector (transitions to SEVERE or CRITICAL from lower status)
        val severeStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.THERMAL_STATUS_SEVERE
        } else {
            3
        }
        if (thermal >= severeStatus && (lastThermalStatus < severeStatus || thermal > lastThermalStatus)) {
            throttlingEventsCount.incrementAndGet()
        }
        lastThermalStatus = thermal

        recordSamplePoint(SamplePoint(now, pss, battLevel, temp, thermal))

        val elapsedNs = (now - baseline.timestampNs).coerceAtLeast(0L)
        val elapsedMs = elapsedNs / 1_000_000L
        val elapsedHours = elapsedNs.toDouble() / (3600.0 * 1_000_000_000.0)

        val deltaTemp = temp - baseline.batteryTempCelsius
        val deltaFps = if (baseline.gameFps > 0f && currentGameFps > 0f) {
            currentGameFps - baseline.gameFps
        } else {
            0f
        }

        // Battery drain rate (%/hr)
        val drainRate = if (elapsedHours >= (5.0 / 3600.0)) { // Minimum 5 seconds of sampling
            ((baseline.batteryLevelPercent - battLevel) / elapsedHours).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

        val memorySlope = computeLinearRegressionMemorySlope()

        latestSnapshot = DifferentialTelemetrySnapshot(
            baseline = baseline,
            elapsedDurationMs = elapsedMs,
            currentBatteryTempCelsius = temp,
            deltaBatteryTempCelsius = deltaTemp,
            batteryDrainRatePercentPerHour = drainRate,
            batteryCurrentNowMa = abs(currentUa) / 1000f,
            currentThermalStatus = thermal,
            thermalThrottlingEvents = throttlingEventsCount.get(),
            currentPssMemoryKb = pss,
            memorySlopeKbPerHour = memorySlope,
            encoderFps = encoderFps,
            gameFps = currentGameFps,
            deltaGameFps = deltaFps,
            activeDestinationsCount = activeDestinations,
            socketWriteLatencyP95Ms = socketWriteP95Ms,
            totalThroughputBps = totalThroughputBps
        )

        return latestSnapshot
    }

    @Synchronized
    fun getDifferentialSnapshot(): DifferentialTelemetrySnapshot = latestSnapshot

    @Synchronized
    fun getBaseline(): DeviceBaselineSnapshot = baseline

    @Synchronized
    fun reset() {
        baseline = DeviceBaselineSnapshot()
        historyHead = 0
        historyCount = 0
        lastThermalStatus = PowerManager.THERMAL_STATUS_NONE
        throttlingEventsCount.set(0)
        latestSnapshot = DifferentialTelemetrySnapshot()
    }

    private fun recordSamplePoint(point: SamplePoint) {
        history[historyHead] = point
        historyHead = (historyHead + 1) % historyCapacity
        if (historyCount < historyCapacity) {
            historyCount++
        }
    }

    /**
     * Calculates the linear regression slope of PSS memory usage over time:
     * slope = (N * sum(x * y) - sum(x) * sum(y)) / (N * sum(x^2) - (sum(x))^2)
     * where x = elapsed time in hours from oldest sample, y = PSS in kB.
     * Returns slope in kB / hour.
     */
    private fun computeLinearRegressionMemorySlope(): Double {
        val n = historyCount
        if (n < 2) return 0.0

        val oldestIdx = if (historyCount < historyCapacity) 0 else historyHead
        val oldestPoint = history[oldestIdx] ?: return 0.0
        val baseTimeNs = oldestPoint.timestampNs

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0

        for (i in 0 until n) {
            val idx = (oldestIdx + i) % historyCapacity
            val sample = history[idx] ?: continue
            val xHours = (sample.timestampNs - baseTimeNs).toDouble() / (3600.0 * 1_000_000_000.0)
            val yKb = sample.pssKb.toDouble()

            sumX += xHours
            sumY += yKb
            sumXY += xHours * yKb
            sumXX += xHours * xHours
        }

        val denominator = n * sumXX - sumX * sumX
        if (abs(denominator) < 1e-9) return 0.0

        val slope = (n * sumXY - sumX * sumY) / denominator
        return slope
    }
}
