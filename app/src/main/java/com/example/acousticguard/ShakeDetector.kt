package com.example.acousticguard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * ShakeDetector implements a motion detection algorithm to identify vigorous shaking.
 * It uses the accelerometer sensor and requires multiple directional changes within
 * a short time window to prevent false positives.
 */
class ShakeDetector(private val onShakeConfirmed: () -> Unit) : SensorEventListener {

    companion object {
        // Acceleration threshold to detect a "shake" (2.5g - 3.0g range)
        // 1g is approx 9.8 m/s^2. 2.7g is approx 26.5 m/s^2.
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        
        // Time window for a sequence of shakes (1.5 seconds)
        private const val SHAKE_WINDOW_MS = 1500
        
        // Number of shake cycles required to confirm SOS (3 cycles)
        private const val MIN_SHAKE_COUNT = 3
    }

    private var shakeCount = 0
    private var lastShakeTimestamp: Long = 0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration magnitude in units of 'g'
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // G-Force will be approximately 1 when at rest
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()

            // Ignore shake events too close together (within 500ms) to ensure distinct movements
            if (lastShakeTimestamp + 500 > now) {
                return
            }

            // Reset count if the window has expired
            if (lastShakeTimestamp + SHAKE_WINDOW_MS < now) {
                shakeCount = 0
            }

            lastShakeTimestamp = now
            shakeCount++

            if (shakeCount >= MIN_SHAKE_COUNT) {
                shakeCount = 0 // Reset
                onShakeConfirmed()
            }
        }
    }
}
