package com.example.acousticguard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_EMERGENCY_STARTED = "com.example.acousticguard.EMERGENCY_STARTED"
    }

    private lateinit var btnSafetyMode: Button
    private lateinit var tvAiStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvEmergencyContact: TextView
    private lateinit var sbSensitivity: SeekBar
    private lateinit var tvSensitivityValue: TextView
    private var isSafetyModeActive = false

    private val PERMISSION_REQUEST_CODE = 100

    private var countdownTimer: android.os.CountDownTimer? = null
    private lateinit var emergencyManager: EmergencyManager
    private var isEmergencyMode = false

    private val audioUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioDetectionService.ACTION_AUDIO_UPDATE) {
                val db = intent.getDoubleExtra(AudioDetectionService.EXTRA_LOUDNESS, 0.0)
                val event = intent.getStringExtra(AudioDetectionService.EXTRA_EVENT)
                
                val statusText = if (event.isNullOrEmpty()) {
                    "AI Detection: ON (Vol: ${String.format("%.1f", db)} dB)"
                } else {
                    "AI Detection: ON (Vol: ${String.format("%.1f", db)} dB) - $event"
                }
                tvAiStatus.text = statusText
            } else if (intent?.action == AudioDetectionService.ACTION_EMERGENCY_CONFIRM) {
                showCountdownDialog()
            } else if (intent?.action == ACTION_EMERGENCY_STARTED) {
                activateEmergencyActions()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emergencyManager = EmergencyManager(this)

        btnSafetyMode = findViewById(R.id.btnSafetyMode)
        tvAiStatus = findViewById(R.id.tvAiStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvEmergencyContact = findViewById(R.id.tvEmergencyContact)
        sbSensitivity = findViewById(R.id.sbSensitivity)
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue)

        val savedSensitivity = prefs.getInt("detection_sensitivity", 80)
        sbSensitivity.progress = savedSensitivity
        tvSensitivityValue.text = "$savedSensitivity dB"

        sbSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSensitivityValue.text = "$progress dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("detection_sensitivity", seekBar?.progress ?: 80).apply()
            }
        })
        
        btnSafetyMode.setOnClickListener {
            if (isEmergencyMode) {
                isEmergencyMode = false
                emergencyManager.stopEmergencyMode()
                stopSafetyMode()
                return@setOnClickListener
            }
            
            if (!isSafetyModeActive) {
                if (checkPermissions()) {
                    startSafetyMode()
                } else {
                    requestPermissions()
                }
            } else {
                stopSafetyMode()
            }
        }
        
        val btnTrustedContacts = findViewById<Button>(R.id.btnTrustedContacts)
        btnTrustedContacts.setOnClickListener {
            showTrustedContactsDialog()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        updateContactsUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction(AudioDetectionService.ACTION_AUDIO_UPDATE)
        filter.addAction(AudioDetectionService.ACTION_EMERGENCY_CONFIRM)
        filter.addAction(ACTION_EMERGENCY_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(audioUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(audioUpdateReceiver)
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSafetyMode()
            } else {
                Toast.makeText(this, "Permissions required for safety mode", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startSafetyMode() {
        isSafetyModeActive = true
        btnSafetyMode.text = getString(R.string.stop_safety)
        btnSafetyMode.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        tvAiStatus.text = getString(R.string.ai_detection_on)
        tvGpsStatus.text = getString(R.string.gps_on)
        
        val serviceIntent = Intent(this, AudioDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        Toast.makeText(this, "Safety Mode Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSafetyMode() {
        isSafetyModeActive = false
        btnSafetyMode.text = getString(R.string.start_safety)
        btnSafetyMode.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_light)
        tvAiStatus.text = getString(R.string.ai_detection_off)
        tvGpsStatus.text = getString(R.string.gps_off)
        
        val serviceIntent = Intent(this, AudioDetectionService::class.java)
        stopService(serviceIntent)
        
        Toast.makeText(this, "Safety Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showCountdownDialog() {
        if (isEmergencyMode) return
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(R.string.possible_emergency)
        builder.setMessage("${getString(R.string.are_you_safe)}\nCountdown: 5")
        builder.setCancelable(false)
        
        builder.setNegativeButton(R.string.cancel_countdown) { dialog, _ ->
            countdownTimer?.cancel()
            stopVibration()
            dialog.dismiss()
            Toast.makeText(this, "Emergency cancelled", Toast.LENGTH_SHORT).show()
        }
        
        val dialog = builder.create()
        dialog.show()
        
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("emergency_vibration", true)) {
            startVibration()
        }

        countdownTimer = object : android.os.CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                dialog.setMessage("${getString(R.string.are_you_safe)}\nCountdown: ${millisUntilFinished / 1000}")
            }
            
            override fun onFinish() {
                stopVibration()
                dialog.dismiss()
                activateEmergencyActions()
            }
        }.start()
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200), 0)
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    private fun activateEmergencyActions() {
        isEmergencyMode = true
        Toast.makeText(this, "EMERGENCY MODE ACTIVATED", Toast.LENGTH_LONG).show()
        emergencyManager.activateEmergencyMode()
        
        btnSafetyMode.text = "STOP EMERGENCY ALARM"
        btnSafetyMode.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
    }

    private fun showTrustedContactsDialog() {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val currentContacts = prefs.getStringSet("trusted_contacts", setOf())?.toMutableSet() ?: mutableSetOf()
        
        val contactList = currentContacts.toList().sorted()
        val displayList = contactList.map { "$it (Tap to remove)" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Trusted Contacts")
            .setItems(displayList) { _, which ->
                val contactToRemove = contactList[which]
                currentContacts.remove(contactToRemove)
                prefs.edit().putStringSet("trusted_contacts", currentContacts).apply()
                updateContactsUI()
                Toast.makeText(this, "Removed $contactToRemove", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Add New") { _, _ ->
                showAddContactDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddContactDialog() {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val currentContacts = prefs.getStringSet("trusted_contacts", setOf())?.toMutableSet() ?: mutableSetOf()
        
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.hint = "Enter phone number"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Trusted Contact")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val phone = input.text.toString()
                if (phone.isNotEmpty()) {
                    currentContacts.add(phone)
                    prefs.edit().putStringSet("trusted_contacts", currentContacts).apply()
                    updateContactsUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateContactsUI() {
        val prefs = getSharedPreferences("AcousticGuardPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("trusted_contacts", setOf()) ?: setOf()
        
        if (contacts.isEmpty()) {
            tvEmergencyContact.text = getString(R.string.emergency_contact_none)
        } else {
            tvEmergencyContact.text = "Emergency Contacts: ${contacts.joinToString(", ")}"
        }
    }
}
