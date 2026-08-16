package com.example.acousticguard

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log

class EmergencyManager(private val context: Context) {

    private var toneGen: ToneGenerator? = null
    private var isAlarmActive = false
    private var isFlashActive = false
    
    private val emergencyLocationManager = EmergencyLocationManager(context)

    fun activateEmergencyMode() {
        startAlarm()
        startFlashlight()
        
        emergencyLocationManager.getLastLocation { location ->
            val mapsLink = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Location not available"
            }
            sendEmergencySms(mapsLink)
        }
    }

    fun stopEmergencyMode() {
        stopAlarm()
        stopFlashlight()
    }

    private fun startAlarm() {
        if (isAlarmActive) return
        isAlarmActive = true
        toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        
        Thread {
            while (isAlarmActive) {
                try {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
                    Thread.sleep(1200)
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }

    private fun stopAlarm() {
        isAlarmActive = false
        toneGen?.stopTone()
        toneGen?.release()
        toneGen = null
    }

    private fun startFlashlight() {
        if (isFlashActive) return
        isFlashActive = true
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            Thread {
                var toggle = true
                while (isFlashActive) {
                    try {
                        cameraManager.setTorchMode(cameraId, toggle)
                        toggle = !toggle
                        Thread.sleep(500)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }
                // Ensure flashlight is off when loop exits
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {}
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopFlashlight() {
        isFlashActive = false
    }

    private fun sendEmergencySms(mapsLink: String) {
        val prefs = context.getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
        
        if (contacts.isEmpty()) {
            Log.w("EmergencyManager", "No trusted contacts configured.")
            return
        }

        val message = "EMERGENCY! I need help. My location: $mapsLink"
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            
            for (contact in contacts) {
                smsManager.sendTextMessage(contact, null, message, null, null)
            }
            Log.i("EmergencyManager", "Emergency SMS sent to ${contacts.size} contacts.")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EmergencyManager", "Failed to send SMS")
        }
    }
}
