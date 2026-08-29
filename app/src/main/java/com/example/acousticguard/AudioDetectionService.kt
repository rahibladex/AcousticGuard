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

    @Volatile
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val audioLock = Any()

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
            
            val prefs = getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("low_battery_alert", true) && batteryPct <= 5f && !lowBatteryAlertSent) {
                lowBatteryAlertSent = true
                emergencyManager.sendCustomSms("Low Battery Alert! My battery is below 5%.")
            }
        }
    }

    companion object {
        const val ACTION_AUDIO_UPDATE = "com.example.acousticguard.AUDIO_UPDATE"
        const val ACTION_EMERGENCY_CONFIRM = "com.example.acousticguard.EMERGENCY_CONFIRM"
        const val ACTION_STOP_SERVICE = "com.example.acousticguard.ACTION_STOP_SERVICE"
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

        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            Log.e("AudioDetection", "Failed to register battery receiver", e)
        }
    }

    private fun setupShakeDetector() {
        shakeDetector = ShakeDetector {
            Log.i("AudioDetection", "Shake SOS detected!")
            emergencyManager.triggerEmergency()
        }
        
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(
                shakeDetector,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("AudioDetection", "Received ACTION_STOP_SERVICE, stopping service completely.")
            stopAudioRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TEJASHWINI Active")
            .setContentText("Safety Mode is monitoring audio...")
            .setSmallIcon(R.drawable.app_logo)
            .setOngoing(true)
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

        stopAudioRecording()
        startAudioRecording()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TEJASHWINI Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun startAudioRecording() {
        synchronized(audioLock) {
            if (isRecording) return
            
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (bufferSize <= 0) {
                Log.e("AudioDetection", "Invalid buffer size for AudioRecord: $bufferSize")
                return
            }

            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 2
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioDetection", "AudioRecord failed to initialize")
                    record.release()
                    return
                }

                record.startRecording()
                audioRecord = record
                isRecording = true

                val thread = Thread({
                    val buffer = ShortArray(bufferSize)
                    while (isRecording && !Thread.currentThread().isInterrupted) {
                        val recordInstance = audioRecord ?: break
                        val readResult = try {
                            recordInstance.read(buffer, 0, buffer.size)
                        } catch (e: Exception) {
                            -1
                        }

                        if (readResult > 0 && isRecording) {
                            processAudioBuffer(buffer, readResult)
                        } else if (readResult < 0) {
                            break
                        }
                    }
                    Log.i("AudioDetection", "Audio processing thread exited.")
                }, "AudioDetectionWorkerThread")

                thread.isDaemon = true
                thread.start()
                recordingThread = thread
                Log.i("AudioDetection", "Audio recording started successfully.")
            } catch (e: SecurityException) {
                Log.e("AudioDetection", "Permission denied for recording audio", e)
            } catch (e: Exception) {
                Log.e("AudioDetection", "Failed to start AudioRecord", e)
            }
        }
    }

    private fun stopAudioRecording() {
        synchronized(audioLock) {
            isRecording = false
            
            recordingThread?.interrupt()
            recordingThread = null

            try {
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            record.stop()
                        } catch (e: Exception) {
                            Log.w("AudioDetection", "Exception while stopping AudioRecord", e)
                        }
                    }
                    record.release()
                }
            } catch (e: Exception) {
                Log.e("AudioDetection", "Error releasing AudioRecord", e)
            } finally {
                audioRecord = null
                Log.i("AudioDetection", "Microphone completely released and stopped.")
            }
        }
    }

    private fun processAudioBuffer(buffer: ShortArray, readSize: Int) {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val rms = Math.sqrt(sum / readSize)
        
        var db = 0.0
        if (rms > 0) {
            db = 20 * log10(rms)
        }

        val prefs = getSharedPreferences("NariShaktiSOSPrefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("detection_sensitivity", 80)

        val dummyFeatures = FloatArray(0)
        val classification = audioClassifier.classifyAudio(dummyFeatures, db, threshold)
        
        val eventLabel = classification.first
        val confidence = classification.second

        val voiceSosEnabled = prefs.getBoolean("voice_sos", false)
        if (voiceSosEnabled && db > threshold + 10) { 
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
            screamConfidenceCount = 0
        }

        if (!isRecording) return

        val intent = Intent(ACTION_AUDIO_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_LOUDNESS, db)
            putExtra(EXTRA_EVENT, "$eventLabel (${(confidence * 100).toInt()}%)")
        }
        sendBroadcast(intent)

        if (screamConfidenceCount >= 3) {
            screamConfidenceCount = 0
            val confirmIntent = Intent(ACTION_EMERGENCY_CONFIRM).apply {
                setPackage(packageName)
            }
            sendBroadcast(confirmIntent)
        }
    }

    override fun onDestroy() {
        stopAudioRecording()
        
        shakeDetector?.let {
            try {
                sensorManager.unregisterListener(it)
            } catch (e: Exception) {}
        }

        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e("AudioDetection", "Error in stopForeground", e)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
