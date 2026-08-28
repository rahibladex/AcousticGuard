package com.example.acousticguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class RemoteAlertService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isVibrating = false

    companion object {
        const val CHANNEL_ID = "RemoteSOSAlarmChannel"
        const val NOTIFICATION_ID = 2002
        
        const val ACTION_START_ALERT = "com.example.acousticguard.ACTION_START_REMOTE_ALERT"
        const val ACTION_STOP_ALERT = "com.example.acousticguard.ACTION_STOP_REMOTE_ALERT"
        const val ACTION_REMOTE_ALERT_STATE_CHANGED = "com.example.acousticguard.ACTION_REMOTE_ALERT_STATE_CHANGED"
        
        const val EXTRA_IS_ACTIVE = "extra_is_active"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MAPS_URL = "extra_maps_url"
        const val EXTRA_MESSAGE = "extra_message"

        var isAlertActive by mutableStateOf(false)
            private set
        var activeSender by mutableStateOf("")
            private set
        var activeMapsUrl by mutableStateOf("")
            private set

        fun startAlert(context: Context, sender: String, mapsUrl: String, message: String) {
            val intent = Intent(context, RemoteAlertService::class.java).apply {
                action = ACTION_START_ALERT
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_MAPS_URL, mapsUrl)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlert(context: Context) {
            val intent = Intent(context, RemoteAlertService::class.java).apply {
                action = ACTION_STOP_ALERT
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_ALERT) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_START_ALERT) {
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: "Trusted Contact"
            val mapsUrl = intent.getStringExtra(EXTRA_MAPS_URL) ?: ""
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Emergency SOS Triggered!"

            isAlertActive = true
            activeSender = sender
            activeMapsUrl = mapsUrl

            acquireWakeLock()
            startForegroundNotification(sender, mapsUrl, message)
            startLoudAlarm()
            startEmergencyVibration()

            val stateIntent = Intent(ACTION_REMOTE_ALERT_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_ACTIVE, true)
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_MAPS_URL, mapsUrl)
                setPackage(packageName)
            }
            sendBroadcast(stateIntent)
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "acousticguard:remote_alarm_wake"
            ).apply {
                acquire(5 * 60 * 1000L) // 5 minutes max
            }
        } catch (e: Exception) {
            Log.e("RemoteAlertService", "Failed to acquire wake lock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Remote SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority audible alerts for incoming remote SOS triggers"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(sender: String, mapsUrl: String, message: String) {
        val stopIntent = Intent(this, RemoteAlertService::class.java).apply {
            action = ACTION_STOP_ALERT
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_MAPS_URL, mapsUrl)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 EMERGENCY SOS FROM: $sender")
            .setContentText(message)
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.app_logo, "STOP ALARM", stopPendingIntent)

        if (mapsUrl.isNotEmpty()) {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val mapPendingIntent = PendingIntent.getActivity(
                this,
                2,
                mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            builder.addAction(R.drawable.app_logo, "VIEW LOCATION", mapPendingIntent)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLoudAlarm() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RemoteAlertService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.i("RemoteAlertService", "Loud alarm started playing at max volume")
        } catch (e: Exception) {
            Log.e("RemoteAlertService", "Failed to start media player for alarm", e)
        }
    }

    private fun startEmergencyVibration() {
        isVibrating = true
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 800, 400), 0)
            }
        } catch (e: Exception) {
            Log.e("RemoteAlertService", "Vibration failed", e)
        }
    }

    private fun stopEmergencyVibration() {
        if (!isVibrating) return
        isVibrating = false
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isAlertActive = false
        activeSender = ""
        activeMapsUrl = ""

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}

        stopEmergencyVibration()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}

        val stateIntent = Intent(ACTION_REMOTE_ALERT_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_ACTIVE, false)
            setPackage(packageName)
        }
        sendBroadcast(stateIntent)

        Log.i("RemoteAlertService", "Remote SOS Alert stopped.")
    }
}
