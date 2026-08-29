package com.example.acousticguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log

class EmergencyManager(private val context: Context) {

    companion object {
        @Volatile
        var isAlarmActive = false
            private set

        @Volatile
        var isFlashActive = false
            private set

        @Volatile
        private var mediaRecorder: MediaRecorder? = null

        private var toneGen: ToneGenerator? = null
        private var flashThread: Thread? = null
        private var alarmThread: Thread? = null

        private val flashLock = Any()
        private val alarmLock = Any()
        private val recorderLock = Any()
    }
    
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

        if (prefs.getBoolean("emergency_call", true)) {
            makeEmergencyCall()
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
            Log.i("EmergencyManager", "updateAndSendLocation: mapsLink=$mapsLink, loc=$location")
            sendEmergencySms(mapsLink)
        }
    }

    fun stopEmergencyMode() {
        stopAlarm()
        stopFlashlight()
        stopAudioRecording()
        handler.removeCallbacks(liveLocationRunnable)
    }

    fun startRemoteAlarm() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Force alarm stream to max volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        
        // Trigger the standard alarm logic
        startAlarm()
        
        Log.i("EmergencyManager", "Remote Alarm triggered and volume forced to max.")
    }

    private fun makeEmergencyCall() {
        val prefs = context.getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
        
        val primaryContact = contacts.firstOrNull()
        if (primaryContact != null) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$primaryContact")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i("EmergencyManager", "Emergency call initiated to $primaryContact")
            } catch (e: SecurityException) {
                Log.e("EmergencyManager", "Permission denied for CALL_PHONE", e)
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Failed to initiate call", e)
            }
        } else {
            Log.w("EmergencyManager", "No trusted contact found for automatic call.")
        }
    }

    fun toggleAlarm() {
        if (isAlarmActive) stopAlarm() else startAlarm()
    }

    fun toggleFlashlight() {
        if (isFlashActive) stopFlashlight() else startFlashlight()
    }

    fun startAlarm() {
        synchronized(alarmLock) {
            if (isAlarmActive) return
            isAlarmActive = true
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            
            try {
                toneGen?.release()
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Failed to init ToneGenerator", e)
            }
            
            val thread = Thread({
                while (isAlarmActive && !Thread.currentThread().isInterrupted) {
                    try {
                        synchronized(alarmLock) {
                            if (isAlarmActive && toneGen != null) {
                                toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
                            }
                        }
                        Thread.sleep(1200)
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.e("EmergencyManager", "Alarm loop error", e)
                        break
                    }
                }
                synchronized(alarmLock) {
                    try {
                        toneGen?.stopTone()
                        toneGen?.release()
                    } catch (e: Exception) {}
                    toneGen = null
                }
            }, "EmergencyAlarmThread")
            thread.isDaemon = true
            thread.start()
            alarmThread = thread
        }
    }

    fun stopAlarm() {
        synchronized(alarmLock) {
            isAlarmActive = false
            alarmThread?.interrupt()
            alarmThread = null
            try {
                toneGen?.stopTone()
                toneGen?.release()
            } catch (e: Exception) {}
            toneGen = null
        }
        // Also stop RemoteAlertService if active
        try {
            RemoteAlertService.stopAlert(context)
        } catch (e: Exception) {}
    }

    fun startFlashlight() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraIds = try {
            cameraManager.cameraIdList.filter { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (e: Exception) {
                    true
                }
            }.ifEmpty { cameraManager.cameraIdList.toList() }
        } catch (e: Exception) {
            cameraManager.cameraIdList.toList()
        }

        synchronized(flashLock) {
            if (isFlashActive) return
            isFlashActive = true

            val thread = Thread({
                var toggle = true
                while (isFlashActive && !Thread.currentThread().isInterrupted) {
                    try {
                        for (id in cameraIds) {
                            try {
                                cameraManager.setTorchMode(id, toggle)
                            } catch (e: Exception) {}
                        }
                        toggle = !toggle
                        Thread.sleep(350)
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        break
                    }
                }
                // Ensure flashlight is OFF when loop terminates
                for (id in cameraIds) {
                    try {
                        cameraManager.setTorchMode(id, false)
                    } catch (e: Exception) {}
                }
            }, "FlashlightStrobeThread")
            thread.isDaemon = true
            thread.start()
            flashThread = thread
        }
    }

    fun stopFlashlight() {
        synchronized(flashLock) {
            isFlashActive = false
            flashThread?.interrupt()
            flashThread = null
        }
        
        // Immediate hardware torch shut-off
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                for (id in cameraManager.cameraIdList) {
                    try {
                        cameraManager.setTorchMode(id, false)
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Failed to shut off torch in stopFlashlight", e)
        }
    }

    private fun startAudioRecording() {
        synchronized(recorderLock) {
            if (mediaRecorder != null) return
            try {
                val fileName = "${context.getExternalFilesDir(null)}/emergency_audio_${System.currentTimeMillis()}.mp4"
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(fileName)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                Log.i("EmergencyManager", "Audio recording started: $fileName")
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Failed to start audio recording", e)
            }
        }
    }

    private fun stopAudioRecording() {
        synchronized(recorderLock) {
            try {
                mediaRecorder?.apply {
                    try { stop() } catch (e: Exception) {}
                    try { release() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Failed to stop audio recording", e)
            } finally {
                mediaRecorder = null
                Log.i("EmergencyManager", "Audio recording stopped and released")
            }
        }
    }

    private fun sendEmergencySms(mapsLink: String) {
        val trigger = RemoteSmsReceiver.TRIGGER_KEYWORD
        sendCustomSms("EMERGENCY LIVE LOCATION! I need help. My current position: $mapsLink (This link will be updated every 2 mins) $trigger")
    }

    fun sendCustomSms(message: String) {
        val prefs = context.getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
        
        if (contacts.isEmpty()) {
            Log.w("EmergencyManager", "No trusted contacts configured.")
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
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
