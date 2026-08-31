package pixl.rec.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

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
    private val shakeThresholdGravitySq = shakeThresholdGravity * shakeThresholdGravity
    private val shakeSlopTimeMs = 1000L // 1 second debounce between shakes

    fun start() {
        if (accelerometer != null) {
            // SENSOR_DELAY_NORMAL (~5Hz) eliminates 90% of sensor wakeups while reliably detecting human shake
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
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

        // Net g-force vector magnitude squared (avoids sqrt per callback)
        val gForceSq = gX * gX + gY * gY + gZ * gZ

        if (gForceSq > shakeThresholdGravitySq) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp >= shakeSlopTimeMs) {
                lastShakeTimestamp = now
                Log.i(tag, "Shake detected (gForceSq=$gForceSq), triggering callback")
                onShakeListener()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
