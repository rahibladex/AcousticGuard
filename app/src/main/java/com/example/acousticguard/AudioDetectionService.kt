package com.example.acousticguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.log10

class AudioDetectionService : Service() {

    private val CHANNEL_ID = "AcousticGuardServiceChannel"
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private lateinit var audioClassifier: AudioClassifier
    private var screamConfidenceCount = 0
    
    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null
    private lateinit var emergencyManager: EmergencyManager

    private var lowBatteryAlertSent = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            
            val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("low_battery_alert", true) && batteryPct <= 5f && !lowBatteryAlertSent) {
                lowBatteryAlertSent = true
                emergencyManager.sendCustomSms("Low Battery Alert! My battery is below 5%.")
            }
        }
    }

    companion object {
        const val ACTION_AUDIO_UPDATE = "com.example.acousticguard.AUDIO_UPDATE"
        const val ACTION_EMERGENCY_CONFIRM = "com.example.acousticguard.EMERGENCY_CONFIRM"
        const val EXTRA_LOUDNESS = "extra_loudness"
        const val EXTRA_EVENT = "extra_event"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioClassifier = AudioClassifier(this)
        emergencyManager = EmergencyManager(this)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setupShakeDetector()

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun setupShakeDetector() {
        shakeDetector = ShakeDetector {
            Log.i("AudioDetection", "Shake SOS detected!")
            emergencyManager.triggerEmergency()
        }
        
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(
            shakeDetector,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NariShakti SOS Active")
            .setContentText("Safety Mode is monitoring audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1, notification)
        }

        startAudioRecording()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Acoustic Guard Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startAudioRecording() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            Thread {
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        processAudioBuffer(buffer, readResult)
                    }
                }
            }.start()
        } catch (e: SecurityException) {
            Log.e("AudioDetection", "Permission denied for recording audio")
        }
    }

    private fun processAudioBuffer(buffer: ShortArray, readSize: Int) {
        // Calculate RMS (Root Mean Square) for loudness
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val rms = Math.sqrt(sum / readSize)
        
        // Convert to decibels (approximate)
        var db = 0.0
        if (rms > 0) {
            db = 20 * log10(rms)
        }

        // Get user-defined threshold
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("detection_sensitivity", 80)

        // Pass dummy features (for prototype), loudness, and threshold to AudioClassifier
        val dummyFeatures = FloatArray(0)
        val classification = audioClassifier.classifyAudio(dummyFeatures, db, threshold)
        
        val eventLabel = classification.first
        val confidence = classification.second

        // Voice SOS (Keyword Detection Placeholder)
        val voiceSosEnabled = prefs.getBoolean("voice_sos", false)
        if (voiceSosEnabled && db > threshold + 10) { 
            // Simple heuristic: if it's loud and voice SOS is enabled, trigger confirmation
            // This acts as a placeholder for actual "Help Me" keyword spotting
            Log.i("AudioDetection", "Voice SOS placeholder triggered")
            val confirmIntent = Intent(ACTION_EMERGENCY_CONFIRM).apply {
                setPackage(packageName)
            }
            sendBroadcast(confirmIntent)
        }

        if (eventLabel == "Scream-like sound" && confidence > 0.8f) {
            screamConfidenceCount++
            Log.d("AudioDetection", "Scream detected. Count: $screamConfidenceCount")
        } else {
            // Reset if the consecutive requirement is broken
            screamConfidenceCount = 0
        }

        // Broadcast loudness and event back to MainActivity
        val intent = Intent(ACTION_AUDIO_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_LOUDNESS, db)
            putExtra(EXTRA_EVENT, "$eventLabel (${(confidence * 100).toInt()}%)")
        }
        sendBroadcast(intent)

        // Trigger emergency confirmation if 3 consecutive high-confidence windows are detected
        if (screamConfidenceCount >= 3) {
            screamConfidenceCount = 0 // Reset
            val confirmIntent = Intent(ACTION_EMERGENCY_CONFIRM).apply {
                setPackage(packageName)
            }
            sendBroadcast(confirmIntent)
        }
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        shakeDetector?.let {
            sensorManager.unregisterListener(it)
        }

        unregisterReceiver(batteryReceiver)
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
