package com.example.acousticguard

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EmergencyManager(private val context: Context) {

    private var toneGen: ToneGenerator? = null
    var isAlarmActive by mutableStateOf(false)
        private set
    private var isFlashActive = false
    private var mediaRecorder: MediaRecorder? = null
    
    private val emergencyLocationManager = EmergencyLocationManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private val liveLocationRunnable = object : Runnable {
        override fun run() {
            updateAndSendLocation()
            handler.postDelayed(this, 2 * 60 * 1000) // Send every 2 minutes
        }
    }

    fun triggerEmergency() {
        activateEmergencyMode()
        
        // Notify MainActivity to update UI (set isEmergencyMode = true, change button text)
        val intent = Intent(MainActivity.ACTION_EMERGENCY_STARTED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun activateEmergencyMode() {
        val prefs = context.getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        val isSilent = prefs.getBoolean("silent_sos_mode", false)

        if (!isSilent) {
            if (prefs.getBoolean("emergency_alarm", true)) {
                startAlarm()
            }
            
            if (prefs.getBoolean("emergency_flashlight", true)) {
                startFlashlight()
            }
        }

        startAudioRecording()
        
        // Start periodic live location updates
        handler.removeCallbacks(liveLocationRunnable)
        handler.post(liveLocationRunnable)
    }

    private fun updateAndSendLocation() {
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
        stopAudioRecording()
        handler.removeCallbacks(liveLocationRunnable)
    }

    fun toggleAlarm() {
        if (isAlarmActive) stopAlarm() else startAlarm()
    }

    fun toggleFlashlight() {
        if (isFlashActive) stopFlashlight() else startFlashlight()
    }

    fun startAlarm() {
        if (isAlarmActive) return
        isAlarmActive = true
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        
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

    fun stopAlarm() {
        isAlarmActive = false
        toneGen?.stopTone()
        toneGen?.release()
        toneGen = null
    }

    fun startFlashlight() {
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

    fun stopFlashlight() {
        isFlashActive = false
    }

    private fun startAudioRecording() {
        try {
            val fileName = "${context.getExternalFilesDir(null)}/emergency_audio_${System.currentTimeMillis()}.mp4"
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(fileName)
                prepare()
                start()
            }
            Log.i("EmergencyManager", "Audio recording started: $fileName")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Failed to start audio recording", e)
        }
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.i("EmergencyManager", "Audio recording stopped")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Failed to stop audio recording", e)
        }
    }

    private fun sendEmergencySms(mapsLink: String) {
        sendCustomSms("EMERGENCY LIVE LOCATION! I need help. My current position: $mapsLink (This link will be updated every 2 mins)")
    }

    fun sendCustomSms(message: String) {
        val prefs = context.getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
        
        if (contacts.isEmpty()) {
            Log.w("EmergencyManager", "No trusted contacts configured.")
            return
        }

        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            
            for (contact in contacts) {
                smsManager.sendTextMessage(contact, null, message, null, null)
            }
            Log.i("EmergencyManager", "SMS sent to ${contacts.size} contacts: $message")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Failed to send SMS", e)
        }
    }
}
