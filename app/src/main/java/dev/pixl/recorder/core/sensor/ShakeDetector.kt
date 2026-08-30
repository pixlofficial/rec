package dev.pixl.recorder.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * High-reliability accelerometer shake detector with g-force magnitude filtering and debouncing.
 */
class ShakeDetector(
    context: Context,
    private val onShakeListener: () -> Unit
) : SensorEventListener {

    private val tag = "ShakeDetector"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeTimestamp = 0L
    private val shakeThresholdGravity = 2.7f // ~2.7g acceleration threshold
    private val shakeSlopTimeMs = 1000L // 1 second debounce between shakes

    fun start() {
        if (accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            Log.i(tag, "ShakeDetector registered")
        } else {
            Log.w(tag, "Accelerometer not available on this device")
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        Log.i(tag, "ShakeDetector unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Net g-force vector magnitude
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > shakeThresholdGravity) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp >= shakeSlopTimeMs) {
                lastShakeTimestamp = now
                Log.i(tag, "Shake detected (gForce=$gForce), triggering callback")
                onShakeListener()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
